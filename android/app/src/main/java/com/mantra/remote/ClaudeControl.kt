package com.mantra.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.max

/**
 * v59, THE CLAUDE CONTROL DOOR — the loop-back (Marko, 15.11.2026: "build a backdoor you can control ... Every time you
 * control my phones, you are going to open this Remote Viewer. Open the same phone over the network, the local network ...
 * build a local network loop inside the phone, so nothing is spent over the internet ... I want to see a big mouse
 * pointer, and I want to see indication that you are scrolling, whatever you are doing. I need to see feedback on the
 * screen.")
 *
 * Unlike the Macro Door (MacroDoor.kt, port 8841) which drives a *remote* machine over VNC, this door makes the phone
 * act on ITSELF. An AccessibilityService dispatches the gestures (tap, swipe, scroll, long-press) on its own glass, and
 * a TYPE_ACCESSIBILITY_OVERLAY layer draws a BIG white mouse pointer that glides to the target before every action, a
 * ripple on a tap, a growing ring on a long-press, an animated trail + arrow + "SCROLL ↓" for a swipe/scroll, and a
 * label riding just above the arrow tip (v62, eye tracing) in plain words ("Claude: tapping NDI"). The pointer stays while a session is connected and
 * fades 5 s after the last command. Whoever watches the phone — the Android viewer, the Mac viewer, scrcpy — sees it,
 * because it is painted on the phone itself.
 *
 * The door is HTTP on port 8790, bound to every address of the phone (LAN / hotspot / Tailscale) AND 127.0.0.1, so the
 * Mac reaches it over `adb forward tcp:8790 tcp:8790` and nothing travels the internet. Loopback (adb forward) needs no
 * token; any other address needs X-Mantra-Token (files/door.token, the same token the Macro Door uses).
 *
 *     GET  /health                         app, version, whether the overlay is up
 *     POST /tap        {x,y,label}         one tap, pointer glides there first
 *     POST /longpress  {x,y,ms,label}      press and hold (growing ring)
 *     POST /swipe      {x1,y1,x2,y2,ms,label}
 *     POST /scroll     {dir,amount,label}  dir = up|down|left|right; amount = pixels (default 800)
 *     POST /text       {text}              types into the focused editable field
 *     POST /key        {name}              back | home | recents
 *     GET  /shot                           PNG of the screen (takeScreenshot)
 *     GET  /tree                           the accessibility node tree (text + bounds), reliable where uiautomator drops
 *
 * The Mac side is tools/rvctl (rvctl pixel tap X Y --label "...").
 */
class ClaudeControl : AccessibilityService() {

    companion object {
        const val PORT = 8790
        @Volatile private var instance: ClaudeControl? = null
        fun isUp() = instance != null
    }

    private val main = Handler(Looper.getMainLooper())
    private var overlay: PointerOverlay? = null
    @Volatile private var started = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        dropShortcut()
        main.post {
            if (overlay == null) overlay = PointerOverlay(this).also { it.attach() }
        }
        openDoor()
    }

    /** v61 (Marko, 15.11.2026: "I have a small blob coming up. I didn't invite it. Remove that blob"): the service asks
     *  for no accessibility button, and if Android put ours into accessibility_button_targets anyway, it is taken out
     *  (works where `mremote keep` granted WRITE_SECURE_SETTINGS; silently skipped elsewhere). */
    private fun dropShortcut() = runCatching {
        val key = "accessibility_button_targets"
        val cur = android.provider.Settings.Secure.getString(contentResolver, key) ?: return@runCatching
        val mine = android.content.ComponentName(this, ClaudeControl::class.java)
        val kept = cur.split(':').filter { it.isNotBlank() && android.content.ComponentName.unflattenFromString(it) != mine }
        if (kept.size != cur.split(':').count { it.isNotBlank() })
            android.provider.Settings.Secure.putString(contentResolver, key, kept.joinToString(":"))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        main.post { overlay?.detach(); overlay = null }
    }

    // ---------------------------------------------------------------- the door

    @Synchronized
    private fun openDoor() {
        if (started) return
        started = true
        thread(name = "claude-control", isDaemon = true) {
            while (true) {
                runCatching {
                    ServerSocket(PORT, 16, InetAddress.getByName("0.0.0.0")).use { server ->
                        while (true) {
                            val c = server.accept()
                            thread(isDaemon = true) { runCatching { serve(c) }; runCatching { c.close() } }
                        }
                    }
                }
                Thread.sleep(3000)
            }
        }
    }

    private fun token(): String = runCatching { java.io.File(filesDir, "door.token").readText().trim() }.getOrDefault("")

    private fun allowed(a: InetAddress): Boolean {
        if (a.isLoopbackAddress) return true
        if (a !is Inet4Address) return false
        return true   // LAN / hotspot / Tailscale all allowed, but only with the token (checked below)
    }

    private fun serve(c: Socket) {
        c.soTimeout = 15000
        val inp = c.getInputStream().buffered()
        val out = c.getOutputStream()
        if (!allowed(c.inetAddress)) { reply(out, 403, "text/plain", "blocked".toByteArray()); return }
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) { val ch = inp.read(); if (ch < 0) return; head.append(ch.toChar()) }
        val lines = head.lines()
        val first = lines[0].split(' ')
        val method = first[0]; val path = first.getOrElse(1) { "/" }
        val len = lines.firstOrNull { it.lowercase().startsWith("content-length:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val body = ByteArray(len).also { var n = 0; while (n < len) { val r = inp.read(it, n, len - n); if (r < 0) break; n += r } }
        val route = path.substringBefore('?')
        val query = path.substringAfter('?', "").split('&').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        // loopback (adb forward) is trusted; any other address must bring the token
        if (!c.inetAddress.isLoopbackAddress) {
            val sent = lines.firstOrNull { it.lowercase().startsWith("x-mantra-token:") }?.substringAfter(':')?.trim() ?: ""
            val mine = token()
            if (mine.isEmpty() || sent != mine) { reply(out, 401, "application/json", """{"ok":false,"error":"token missing or wrong"}""".toByteArray()); return }
        }
        val j = runCatching { if (body.isNotEmpty()) JSONObject(String(body)) else JSONObject() }.getOrDefault(JSONObject())
        fun lbl(default: String) = j.optString("label", default)
        try {
            when {
                route == "/health" -> reply(out, 200, "application/json", JSONObject()
                    .put("app", "mantra-remote").put("door", "claude-control").put("version", BuildConfig.VERSION_NAME)
                    .put("overlay", overlay != null).toString().toByteArray())

                route == "/tap" && method == "POST" -> {
                    val x = j.getInt("x"); val y = j.getInt("y")
                    overlay?.caption("Claude: tapping ${lbl("")}".trim())
                    glideThen(x, y) { overlay?.ripple(x, y); tap(x, y) }
                    ok(out)
                }
                route == "/longpress" && method == "POST" -> {
                    val x = j.getInt("x"); val y = j.getInt("y"); val ms = j.optInt("ms", 700)
                    overlay?.caption("Claude: holding ${lbl("")}".trim())
                    glideThen(x, y) { overlay?.ring(x, y, ms.toLong()); longPress(x, y, ms) }
                    ok(out)
                }
                route == "/swipe" && method == "POST" -> {
                    val x1 = j.getInt("x1"); val y1 = j.getInt("y1"); val x2 = j.getInt("x2"); val y2 = j.getInt("y2")
                    val ms = j.optInt("ms", 400)
                    overlay?.caption("Claude: swiping ${lbl("")}".trim())
                    glideThen(x1, y1) { overlay?.trail(x1, y1, x2, y2); swipe(x1, y1, x2, y2, ms) }
                    ok(out)
                }
                route == "/scroll" && method == "POST" -> {
                    val dir = j.optString("dir", "down").lowercase()
                    val amount = j.optInt("amount", 800)
                    val (cx, cy) = screenCenter()
                    // scrolling the content DOWN means the finger moves UP, and vice-versa
                    val (x1, y1, x2, y2) = when (dir) {
                        "up" -> intArrayOf(cx, cy - amount / 2, cx, cy + amount / 2)
                        "left" -> intArrayOf(cx - amount / 2, cy, cx + amount / 2, cy)
                        "right" -> intArrayOf(cx + amount / 2, cy, cx - amount / 2, cy)
                        else -> intArrayOf(cx, cy + amount / 2, cx, cy - amount / 2)  // down
                    }.let { listOf(it[0], it[1], it[2], it[3]) }
                    overlay?.caption("Claude: scrolling ${mapOf("up" to "↑", "left" to "←", "right" to "→").getOrDefault(dir, "↓")} ${lbl("")}".trim())
                    glideThen(x1, y1) { overlay?.scroll(x1, y1, x2, y2, dir); swipe(x1, y1, x2, y2, 500) }
                    ok(out)
                }
                route == "/text" && method == "POST" -> {
                    val t = j.optString("text", "")
                    overlay?.caption("Claude: typing")
                    val done = typeText(t)
                    reply(out, 200, "application/json", JSONObject().put("ok", done).put("typed", t)
                        .apply { if (!done) put("error", "no focused editable field") }.toString().toByteArray())
                }
                route == "/key" && method == "POST" -> {
                    val name = j.optString("name", "").lowercase()
                    overlay?.caption("Claude: $name")
                    val g = when (name) {
                        "back" -> GLOBAL_ACTION_BACK
                        "home" -> GLOBAL_ACTION_HOME
                        "recents", "apps" -> GLOBAL_ACTION_RECENTS
                        else -> -1
                    }
                    if (g == -1) reply(out, 400, "application/json", """{"ok":false,"error":"key is back|home|recents"}""".toByteArray())
                    else { performGlobalAction(g); ok(out) }
                }
                route == "/shot" -> shot()?.let { reply(out, 200, "image/png", it) }
                    ?: reply(out, 500, "text/plain", "no screenshot".toByteArray())
                route == "/tree" -> reply(out, 200, "application/json", tree().toString().toByteArray())
                else -> reply(out, 404, "text/plain", "GET /health /shot /tree; POST /tap /longpress /swipe /scroll /text /key".toByteArray())
            }
        } catch (e: Exception) {
            reply(out, 500, "application/json", JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName).toString().toByteArray())
        }
    }

    private fun ok(out: java.io.OutputStream) = reply(out, 200, "application/json", """{"ok":true}""".toByteArray())

    private fun reply(out: java.io.OutputStream, code: Int, type: String, data: ByteArray) {
        out.write("HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(data); out.flush()
    }

    // ---------------------------------------------------------------- the pointer glide then the gesture

    /** Move the big pointer to (x,y) over ~400 ms, then run [act] OFF the main thread (dispatchGesture delivers its
     *  callback on main, so the gesture must not block main); blocks the HTTP request until done. */
    private fun glideThen(x: Int, y: Int, act: () -> Unit) {
        val latch = CountDownLatch(1)
        val fire = { thread(isDaemon = true) { runCatching { act() }; latch.countDown() } }
        main.post {
            val ov = overlay
            if (ov != null) ov.moveTo(x.toFloat(), y.toFloat(), 400) { fire() } else fire()
        }
        latch.await(10, TimeUnit.SECONDS)
        Thread.sleep(120)  // let the ripple/gesture be seen before the next command
    }

    private fun screenCenter(): Pair<Int, Int> {
        val m = resources.displayMetrics
        return m.widthPixels / 2 to m.heightPixels / 2
    }

    private fun dispatch(path: Path, startMs: Long, durMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, startMs, durMs)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        val done = CountDownLatch(1)
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { done.countDown() }
            override fun onCancelled(d: GestureDescription?) { done.countDown() }
        }, main)
        done.await(5, TimeUnit.SECONDS)
    }

    private fun tap(x: Int, y: Int) {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x.toFloat(), y.toFloat()) }
        dispatch(p, 0, 60)
    }

    private fun longPress(x: Int, y: Int, ms: Int) {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x.toFloat(), y.toFloat()) }
        dispatch(p, 0, ms.toLong().coerceIn(300, 5000))
    }

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int) {
        val p = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        dispatch(p, 0, ms.toLong().coerceIn(50, 4000))
    }

    private fun typeText(t: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!node.isEditable) return false
        val existing = node.text?.toString() ?: ""
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + t) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ---------------------------------------------------------------- screenshot

    private fun shot(): ByteArray? {
        var png: ByteArray? = null
        val done = CountDownLatch(1)
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, java.util.concurrent.Executor { it.run() }, object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                runCatching {
                    val hb = res.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, res.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp != null) ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); png = it.toByteArray() }
                }
                done.countDown()
            }
            override fun onFailure(code: Int) { done.countDown() }
        })
        done.await(5, TimeUnit.SECONDS)
        return png
    }

    // ---------------------------------------------------------------- the node tree

    private fun tree(): JSONObject {
        val root = rootInActiveWindow ?: return JSONObject().put("ok", false).put("error", "no active window")
        val arr = JSONArray()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40) return
            val b = Rect(); n.getBoundsInScreen(b)
            val text = n.text?.toString()
            val desc = n.contentDescription?.toString()
            if (!text.isNullOrBlank() || !desc.isNullOrBlank() || n.isClickable) {
                arr.put(JSONObject()
                    .put("text", text ?: "")
                    .put("desc", desc ?: "")
                    .put("cls", n.className?.toString()?.substringAfterLast('.') ?: "")
                    .put("clickable", n.isClickable)
                    .put("x", b.centerX()).put("y", b.centerY())
                    .put("bounds", "${b.left},${b.top},${b.right},${b.bottom}"))
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return JSONObject().put("ok", true).put("count", arr.length()).put("nodes", arr)
    }
}

/**
 * The visible layer: a big mouse arrow that glides, a tap ripple, a long-press ring, a swipe/scroll trail with an arrow
 * and a label, and a caption strip at the top. Added through the AccessibilityService's own window type, so it needs no
 * SYSTEM_ALERT_WINDOW grant and sits above everything.
 */
private class PointerOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val view = OverlayView(service)
    private var attached = false

    fun attach() {
        if (attached) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(view, lp); attached = true }
    }

    fun detach() { if (attached) runCatching { wm.removeView(view) }; attached = false }

    fun moveTo(x: Float, y: Float, durMs: Long, then: () -> Unit) = view.moveTo(x, y, durMs, then)
    fun ripple(x: Int, y: Int) = view.ripple(x.toFloat(), y.toFloat())
    fun ring(x: Int, y: Int, ms: Long) = view.ring(x.toFloat(), y.toFloat(), ms)
    fun trail(x1: Int, y1: Int, x2: Int, y2: Int) = view.trail(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), null)
    fun scroll(x1: Int, y1: Int, x2: Int, y2: Int, dir: String) =
        view.trail(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), null)   // v62: the words ride above the pointer
    fun caption(text: String) = view.caption(text)

    private fun arrow(dir: String) = when (dir) { "up" -> "↑"; "left" -> "←"; "right" -> "→"; else -> "↓" }
}

private class OverlayView(private val ctx: android.content.Context) : View(ctx) {
    private val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics) }

    // pointer
    private var px = -100f; private var py = -100f
    private var fromX = 0f; private var fromY = 0f; private var toX = 0f; private var toY = 0f
    private var glideStart = 0L; private var glideDur = 0L; private var gliding = false; private var afterGlide: (() -> Unit)? = null
    @Volatile private var lastActivity = 0L

    // effects
    private var rippleX = 0f; private var rippleY = 0f; private var rippleStart = 0L; private var rippleOn = false
    private var ringX = 0f; private var ringY = 0f; private var ringStart = 0L; private var ringMs = 0L; private var ringOn = false
    private var tx1 = 0f; private var ty1 = 0f; private var tx2 = 0f; private var ty2 = 0f; private var trailStart = 0L; private var trailOn = false
    private var trailLabel: String? = null
    private var captionText = ""; private var captionStart = 0L

    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = dp(2.5f) }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9500") }
    private val accentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9500"); style = Paint.Style.STROKE; strokeWidth = dp(4f); strokeCap = Paint.Cap.ROUND }
    private val capBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC000000") }
    private val capText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(19f); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9500"); textSize = dp(18f); textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    private fun now() = SystemClock.uptimeMillis()
    private fun bump() { lastActivity = now(); postInvalidateOnAnimation() }

    fun moveTo(x: Float, y: Float, durMs: Long, then: () -> Unit) {
        if (px < 0f) { px = x; py = y }   // first time: appear at the target
        fromX = px; fromY = py; toX = x; toY = y; glideStart = now(); glideDur = max(1L, durMs); gliding = true; afterGlide = then
        bump()
    }
    fun ripple(x: Float, y: Float) { rippleX = x; rippleY = y; rippleStart = now(); rippleOn = true; bump() }
    fun ring(x: Float, y: Float, ms: Long) { ringX = x; ringY = y; ringStart = now(); ringMs = ms; ringOn = true; bump() }
    fun trail(x1: Float, y1: Float, x2: Float, y2: Float, label: String?) { tx1 = x1; ty1 = y1; tx2 = x2; ty2 = y2; trailStart = now(); trailOn = true; trailLabel = label; bump() }
    fun caption(text: String) { captionText = text; captionStart = now(); bump() }

    override fun onDraw(canvas: Canvas) {
        val t = now()

        // glide
        if (gliding) {
            val f = ((t - glideStart).toFloat() / glideDur).coerceIn(0f, 1f)
            val e = 1f - (1f - f) * (1f - f)   // ease-out
            px = fromX + (toX - fromX) * e; py = fromY + (toY - fromY) * e
            if (f >= 1f) { gliding = false; val a = afterGlide; afterGlide = null; a?.invoke() }
        }

        // trail (swipe / scroll), 900 ms
        if (trailOn) {
            val f = ((t - trailStart).toFloat() / 900f)
            if (f >= 1f) trailOn = false else {
                // the head moves with the finger (500 ms), the line fades by 900 ms; v62: the pointer rides the head
                val hf = ((t - trailStart).toFloat() / 500f).coerceIn(0f, 1f)
                val hx = tx1 + (tx2 - tx1) * hf
                val hy = ty1 + (ty2 - ty1) * hf
                if (!gliding) { px = hx; py = hy }
                accentStroke.alpha = (200 * (1f - f)).toInt().coerceIn(0, 255)
                canvas.drawLine(tx1, ty1, hx, hy, accentStroke)
                // arrow head at the moving end
                drawArrowHead(canvas, tx1, ty1, hx, hy)
                trailLabel?.let {
                    labelText.alpha = (255 * (1f - f * 0.6f)).toInt().coerceIn(0, 255)
                    canvas.drawText(it, (tx1 + tx2) / 2, (ty1 + ty2) / 2 - dp(16f), labelText)
                }
            }
        }

        // long-press ring: grows over its hold time
        if (ringOn) {
            val f = ((t - ringStart).toFloat() / ringMs.coerceAtLeast(1))
            if (f >= 1f) ringOn = false else {
                accentStroke.alpha = 220
                canvas.drawCircle(ringX, ringY, dp(10f) + dp(34f) * f, accentStroke)
            }
        }

        // tap ripple: 450 ms
        if (rippleOn) {
            val f = ((t - rippleStart).toFloat() / 450f)
            if (f >= 1f) rippleOn = false else {
                val r = dp(8f) + dp(44f) * f
                val a = (180 * (1f - f)).toInt().coerceIn(0, 255)
                accent.alpha = a
                canvas.drawCircle(rippleX, rippleY, r, accent)
            }
            accent.alpha = 255
        }

        // the big pointer, visible while a session is fresh (fades 5 s after the last command)
        val sinceAct = t - lastActivity
        if (lastActivity > 0 && sinceAct < 5600) {
            val alpha = if (sinceAct < 5000) 255 else (255 * (1f - (sinceAct - 5000) / 600f)).toInt().coerceIn(0, 255)
            drawPointer(canvas, px, py, alpha)
            if (captionText.isNotEmpty()) drawLabel(canvas, captionText, px, py, alpha)
        }

        val busy = gliding || trailOn || ringOn || rippleOn || sinceAct < 5600
        if (busy) postInvalidateOnAnimation()
    }

    /**
     * v62, EYE TRACING (Marko, 15.11.2026: "If the text is up and the arrow is down, I'm confused. The text should always
     * be above the arrow. Written when you do the clicking."; MANTRA_MANIFEST modules/eye-tracing.md): the words travel
     * WITH the pointer in a dark pill, centred on the tip, 24 dp above it, kept inside the screen; only when there is no
     * room above (the top edge) they sit just under the arrow, still touching it. The eye never jumps.
     */
    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, alpha: Int) {
        val padH = dp(14f); val h = dp(40f); val gap = dp(24f); val edge = dp(8f)
        val w = (capText.measureText(text) + padH * 2).coerceAtMost(width - edge * 2)
        var left = x - w / 2
        left = left.coerceIn(edge, (width - edge - w).coerceAtLeast(edge))
        var top = y - gap - h
        if (top < dp(28f)) top = y + dp(48f) * 0.84f + dp(8f)   // no room above: right under the arrow
        capBg.alpha = (alpha * 0.85f).toInt(); capText.alpha = alpha
        canvas.drawRoundRect(left, top, left + w, top + h, h / 2, h / 2, capBg)
        val fm = capText.fontMetrics
        canvas.drawText(text, left + w / 2, top + h / 2 - (fm.ascent + fm.descent) / 2, capText)
        capBg.alpha = 255; capText.alpha = 255
    }

    /** A classic arrow cursor, ~48 dp tall, white fill with a black outline. Tip at (x,y). */
    private fun drawPointer(canvas: Canvas, x: Float, y: Float, alpha: Int) {
        val s = dp(48f)
        val p = Path().apply {
            moveTo(x, y)
            lineTo(x, y + s * 0.72f)
            lineTo(x + s * 0.20f, y + s * 0.55f)
            lineTo(x + s * 0.33f, y + s * 0.84f)
            lineTo(x + s * 0.44f, y + s * 0.80f)
            lineTo(x + s * 0.31f, y + s * 0.52f)
            lineTo(x + s * 0.55f, y + s * 0.52f)
            close()
        }
        white.alpha = alpha; black.alpha = alpha
        canvas.drawPath(p, white)
        canvas.drawPath(p, black)
        white.alpha = 255; black.alpha = 255
    }

    private fun drawArrowHead(canvas: Canvas, fromX: Float, fromY: Float, x: Float, y: Float) {
        val ang = Math.atan2((y - fromY).toDouble(), (x - fromX).toDouble())
        val h = dp(18f)
        val a1 = ang + Math.toRadians(150.0); val a2 = ang - Math.toRadians(150.0)
        canvas.drawLine(x, y, x + (h * Math.cos(a1)).toFloat(), y + (h * Math.sin(a1)).toFloat(), accentStroke)
        canvas.drawLine(x, y, x + (h * Math.cos(a2)).toFloat(), y + (h * Math.sin(a2)).toFloat(), accentStroke)
    }
}
