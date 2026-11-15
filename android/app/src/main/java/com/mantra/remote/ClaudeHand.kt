package com.mantra.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v59, THE CLAUDE HAND (Marko, 15.11.2026: "Every time you control my phones ... I want to see a big mouse pointer, and
 * I want to see indication that you are scrolling, whatever you are doing. I need to see feedback on the screen.").
 *
 * An AccessibilityService (switched on once in Settings → Accessibility → Mantra Remote Viewing, or by `rvctl setup`
 * over adb) that
 *  - performs the gestures (dispatchGesture: tap, long press, swipe, scroll) and the global keys (back, home, recents),
 *  - draws them on a TYPE_ACCESSIBILITY_OVERLAY above every app, never catching a touch: a BIG arrow pointer (white,
 *    black outline) that glides to the target first, a ripple on a tap, a growing ring on a long press, a trail with an
 *    arrow and "SCROLL ↓" for a swipe or scroll, and a caption strip at the top ("Claude: tapping NDI"). The pointer
 *    stays while commands keep coming and fades 5 s after the last one.
 *  - reads the screen for Claude: the accessibility node tree (text, description, id, bounds) and a screenshot.
 * The commands come through ClaudeDoor (port 8790, loopback via adb forward, the home LAN, Tailscale; never the internet).
 */
class ClaudeHand : AccessibilityService() {
    companion object {
        @Volatile var me: ClaudeHand? = null
        fun on() = me != null
    }

    private val main = Handler(Looper.getMainLooper())
    private var layer: HandLayer? = null

    override fun onServiceConnected() {
        me = this
        ClaudeDoor.open(applicationContext)
        main.post { addLayer() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        me = null
        runCatching { layer?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        layer = null
        super.onDestroy()
    }

    private fun addLayer() {
        if (layer != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = HandLayer(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            title = "Claude hand"
        }
        wm.addView(v, lp)
        layer = v
    }

    // ------------------------------------------------------------------ the screen

    fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = wm.maximumWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun onMain(block: () -> Unit) {
        val done = CountDownLatch(1)
        main.post { try { block() } finally { done.countDown() } }
        done.await(5, TimeUnit.SECONDS)
    }

    private fun gesture(path: Path, ms: Long): Boolean {
        val done = CountDownLatch(1)
        var ok = false
        main.post {
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceAtLeast(1))).build()
            val sent = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { ok = true; done.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { done.countDown() }
            }, main)
            if (!sent) done.countDown()
        }
        done.await(ms + 5000, TimeUnit.MILLISECONDS)
        return ok
    }

    /** The pointer glides to x,y (glide ms), the caption says what comes; returns when it is there. */
    private fun glide(x: Float, y: Float, caption: String, glideMs: Long) {
        onMain { layer?.caption(caption); layer?.glideTo(x, y, glideMs) }
        Thread.sleep(glideMs + 60)
    }

    // ------------------------------------------------------------------ the commands (called from the door's threads)

    fun tap(x: Float, y: Float, label: String, glideMs: Long, times: Int = 1): Boolean {
        glide(x, y, label, glideMs)
        var ok = true
        repeat(times.coerceIn(1, 3)) {
            onMain { layer?.ripple(x, y) }
            ok = gesture(Path().apply { moveTo(x, y) }, 60) && ok
            Thread.sleep(if (times > 1) 90 else 250)
        }
        return ok
    }

    fun longPress(x: Float, y: Float, label: String, glideMs: Long, ms: Long): Boolean {
        glide(x, y, label, glideMs)
        onMain { layer?.ring(x, y, ms) }
        val ok = gesture(Path().apply { moveTo(x, y) }, ms)
        Thread.sleep(250)
        return ok
    }

    /** A swipe from x1,y1 to x2,y2 in ms: the pointer goes to the start, then the trail follows the finger. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long, label: String, glideMs: Long, tag: String): Boolean {
        glide(x1, y1, label, glideMs)
        onMain { layer?.trail(x1, y1, x2, y2, ms, tag) }
        val ok = gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)
        Thread.sleep(300)
        return ok
    }

    /** dir up/down/left/right = the way the content moves into view (down = see what is below: the finger goes up). */
    fun scroll(dir: String, amount: Float, x: Float?, y: Float?, ms: Long, label: String, glideMs: Long): Boolean {
        val (w, h) = screenSize()
        val cx = x ?: (w / 2f); val cy = y ?: (h / 2f)
        val d = if (amount <= 1f) amount * (if (dir == "left" || dir == "right") w else h) else amount
        val half = d / 2f
        val arrow = when (dir) { "up" -> "↑"; "left" -> "←"; "right" -> "→"; else -> "↓" }
        return when (dir) {
            "up" -> swipe(cx, cy - half, cx, cy + half, ms, label, glideMs, "SCROLL $arrow")
            "left" -> swipe(cx - half, cy, cx + half, cy, ms, label, glideMs, "SCROLL $arrow")
            "right" -> swipe(cx + half, cy, cx - half, cy, ms, label, glideMs, "SCROLL $arrow")
            else -> swipe(cx, cy + half, cx, cy - half, ms, label, glideMs, "SCROLL $arrow")
        }
    }

    fun key(name: String, label: String): Boolean {
        val action = when (name.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME; "recents", "apps" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; "quick" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN; else -> return false
        }
        onMain { layer?.caption(label); layer?.badge(name.uppercase()) }
        Thread.sleep(500)
        var ok = false
        onMain { ok = performGlobalAction(action) }
        Thread.sleep(300)
        return ok
    }

    /** Text into the field that has the focus (added to what is there, or replacing it). */
    fun text(t: String, replace: Boolean, label: String): String? {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return "no text field has the focus: tap one first"
        val r = Rect().also { node.getBoundsInScreen(it) }
        glide(r.exactCenterX(), r.exactCenterY(), label, 400)
        val now = if (replace) "" else (node.text?.toString()?.takeUnless { node.isShowingHintText } ?: "")
        var ok = false
        onMain {
            layer?.badge("⌨ TYPING")
            ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, now + t)
            })
        }
        Thread.sleep(300)
        return if (ok) null else "the field refused the text"
    }

    /** Only the pointer and the caption, no action (a "look here"). */
    fun point(x: Float, y: Float, label: String, glideMs: Long) = glide(x, y, label, glideMs)

    fun show(label: String) = onMain { layer?.caption(label) }

    // ------------------------------------------------------------------ reading the screen

    fun tree(): JSONObject {
        val out = JSONArray()
        val ws = runCatching { windows }.getOrDefault(emptyList())
        val roots = ws.mapNotNull { w -> w.root?.let { w to it } }.ifEmpty { listOfNotNull(rootInActiveWindow?.let { null to it }) }
        for ((w, root) in roots) walk(root, 0, out, w?.title?.toString() ?: "")
        val (sw, sh) = screenSize()
        return JSONObject().put("screen", JSONObject().put("w", sw).put("h", sh))
            .put("package", rootInActiveWindow?.packageName?.toString() ?: "").put("nodes", out)
    }

    private fun walk(n: AccessibilityNodeInfo, depth: Int, out: JSONArray, win: String) {
        if (depth > 60 || out.length() > 3000) return
        val r = Rect().also { n.getBoundsInScreen(it) }
        val text = n.text?.toString() ?: ""
        val desc = n.contentDescription?.toString() ?: ""
        val id = n.viewIdResourceName ?: ""
        if (n.isVisibleToUser && (text.isNotEmpty() || desc.isNotEmpty() || id.isNotEmpty() || n.isClickable || n.isScrollable || n.isEditable)) {
            out.put(JSONObject().put("d", depth).put("text", text).put("desc", desc).put("id", id)
                .put("cls", n.className?.toString()?.substringAfterLast('.') ?: "")
                .put("b", JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom))
                .put("click", n.isClickable).put("scroll", n.isScrollable).put("edit", n.isEditable)
                .apply { if (win.isNotEmpty() && depth == 0) put("win", win) })
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1, out, win) }
    }

    /** The first visible node whose text or description holds [want] (exact match first), its centre. */
    fun find(want: String): Pair<Float, Float>? {
        val hits = mutableListOf<Pair<Int, Rect>>()
        fun go(n: AccessibilityNodeInfo, d: Int) {
            if (d > 60) return
            val t = (n.text?.toString() ?: "") + "\n" + (n.contentDescription?.toString() ?: "")
            if (n.isVisibleToUser && t.contains(want, true)) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                val exact = t.lines().any { it.trim().equals(want, true) }
                if (!r.isEmpty) hits += (if (exact) 0 else 1) to r
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { go(it, d + 1) }
        }
        runCatching { windows }.getOrDefault(emptyList()).mapNotNull { it.root }.ifEmpty { listOfNotNull(rootInActiveWindow) }.forEach { go(it, 0) }
        val r = hits.minByOrNull { it.first }?.second ?: return null
        return r.exactCenterX() to r.exactCenterY()
    }

    fun screenshot(): ByteArray? {
        val done = CountDownLatch(1)
        var png: ByteArray? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(s: ScreenshotResult) {
                runCatching {
                    val hw = Bitmap.wrapHardwareBuffer(s.hardwareBuffer, s.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    s.hardwareBuffer.close()
                    png = bmp?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
                }
                done.countDown()
            }
            override fun onFailure(errorCode: Int) { done.countDown() }
        })
        done.await(8, TimeUnit.SECONDS)
        return png
    }
}

/**
 * The drawn layer: never touchable, the size of the whole screen. Everything is drawn in screen pixels (the layer's
 * own offset on the screen taken away), so a pointer at x,y stands exactly where the gesture lands.
 */
class HandLayer(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private var px = -1f; private var py = -1f
    private var glider: ValueAnimator? = null
    private var alphaNow = 0f
    private var fader: ValueAnimator? = null
    private var captionText = ""
    private var badgeText = ""; private var badgeUntil = 0L
    private data class Ripple(val x: Float, val y: Float, val t0: Long)
    private val ripples = mutableListOf<Ripple>()
    private var ringX = 0f; private var ringY = 0f; private var ringT0 = 0L; private var ringMs = 0L
    private var trX1 = 0f; private var trY1 = 0f; private var trX2 = 0f; private var trY2 = 0f
    private var trT0 = 0L; private var trMs = 0L; private var trTag = ""; private var trUntil = 0L
    private val off = IntArray(2)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f * d; strokeJoin = Paint.Join.ROUND }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; style = Paint.Style.FILL }
    private val accent = 0xFFF