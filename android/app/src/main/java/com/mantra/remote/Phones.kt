package com.mantra.remote

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mantra.remote.adb.AdbLink

/**
 * v16 (Marko, 26.10.2026: "in the Mantra remote control settings, I want to see all the available devices I can
 * connect to. You also need to enable the Pixel 7 phone, so I can control my computer or the Pixel 7 from my own one
 * app, switching between them"): the phones this app can drive besides the Mac, over ADB (wireless debugging or adb
 * on TCP 5555), through Tailscale when away, the way the Camera Remote drives the camera: scrcpy's picture, every
 * touch back.
 */
data class Phone(val name: String, val address: String)

object Phones {
    private const val KEY = "phones"
    /** The Pixel 7 and (v29) the Nothing Phone on Tailscale (adb on TCP 5555), known from the start, so each phone
     *  lists the other even without the Tailscale key (Marko, 5.11.2026: "Look from Pixel 7 to Nothing Phone"). */
    private val DEFAULT = listOf(Phone("PIXEL 7", "100.70.23.53:5555"), Phone("NOTHING PHONE 2A", "100.67.149.40:5555"))

    fun all(prefs: android.content.SharedPreferences?): List<Phone> {
        val saved = prefs?.getString(KEY, null) ?: return DEFAULT
        val list = saved.lines().mapNotNull { l -> l.split('|').takeIf { it.size == 2 }?.let { Phone(it[0], it[1]) } }
        // v29: a list saved before the Nothing was known gets it too
        return list + DEFAULT.filter { d -> list.none { it.address == d.address } }
    }

    fun add(prefs: android.content.SharedPreferences?, p: Phone) {
        val list = all(prefs).filter { it.address != p.address } + p
        prefs?.edit()?.putString(KEY, list.joinToString("\n") { "${it.name}|${it.address}" })?.apply()
    }

    fun remove(prefs: android.content.SharedPreferences?, p: Phone) {
        val list = all(prefs).filter { it.address != p.address }
        prefs?.edit()?.putString(KEY, list.joinToString("\n") { "${it.name}|${it.address}" })?.apply()
    }
}

/**
 * A phone on the whole screen: its picture as large as it fits, a touch on it is a touch there. The keys above: MAC
 * goes back to the Mac (its link stays), BACK and HOME are the phone's own.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PhoneScreen(phone: Phone, pin: String = "", onMac: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf("${phone.name}: CONNECTING TO ${phone.address}") }
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf<AdbLink?>(null) }
    // v16 (Marko, 26.10.2026: "I want to see my mouse on the screen ... I press and I drag, and it acts like ...
    // keeping my left mouse button down"): MOUSE, a pointer moved like a trackpad (tap = click, hold then drag =
    // the button held), or TOUCH, the finger is the finger; ⌨ types into the phone
    // v53 (Marko, 14.11.2026: "it come immediately in the full screen ... not mouse default, default is tapping ...
    // swiping from left or right edge of the screen brings me the menus ... mouse pointer is showing, which I don't
    // like"): TOUCH first, the picture on the whole glass (no bars, no keys), the keys in a menu that an inward swipe
    // from the left or right edge (or Android's own back swipe) opens; MOUSE is still in the menu
    var mouse by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    var px by remember { mutableStateOf(0.5f) }
    var py by remember { mutableStateOf(0.5f) }
    var held by remember { mutableStateOf(false) }
    var misses by remember { mutableIntStateOf(0) }
    var macAskedAt by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler { menu = !menu }
    // the whole glass: Android's bars hidden (a swipe from the edge shows them for a moment), upright
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val win = activity?.window
        val before = activity?.requestedOrientation
        if (win != null) {
            val bars = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            if (win != null) {
                // back to what the Mac's screen had: its FULL keeps the bars hidden and stands sideways
                val wasFull = before == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                if (!wasFull) androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                if (before != null) activity.requestedOrientation = before
            }
        }
    }

    DisposableEffect(phone.address) { onDispose { link?.stop(); link = null } }
    // v16: a phone is reached through Tailscale: switched on here too (the Mac's FIRST SCREEN or a fresh start leave it off)
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Tailscale.onAndWait(context.applicationContext) } }
    // the status line: what arrives, every second, as on the Mac's
    LaunchedEffect(link) {
        var last = 0L to 0L
        var unlockTried = false
        while (true) {
            kotlinx.coroutines.delay(1000)
            val a = link ?: continue
            // v16: the first moment the link is up, a locked phone is opened with its PIN (entered on the first screen)
            if (a.connected && !unlockTried) {
                unlockTried = true
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { a.unlock(pin) }
                if (!ok) status = if (pin.isEmpty()) "${phone.name} IS LOCKED: its PIN on the first screen opens it" else "${phone.name}: THE PIN DID NOT OPEN IT"
            }
            val now = a.frames.get() to a.bytes.get()
            if (a.connected && a.width > 0) status = "%s · %s · %d fps · %d kbit/s".format(
                phone.name, a.host, now.first - last.first, ((now.second - last.second) * 8 / 1000).toInt())
            last = now
        }
    }

    // the window's safe-area padding is undone here, so the picture reaches every edge of the glass
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = if (w > 0 && h > 0) w.toFloat() / h else 1080f / 2400f
            Box(Modifier.aspectRatio(ratio)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(t: SurfaceTexture, sw: Int, sh: Int) {
                                val (host, port) = phone.address.substringBefore(':') to (phone.address.substringAfter(':').toIntOrNull() ?: 5555)
                                val a = AdbLink(context.applicationContext, { host to port }, Surface(t),
                                    // v16 (Marko, 26.10.2026: "I'm not able to connect to my Pixel phone ... There is some
                                    // problem"): a phone that does not answer is said in words, with what to do
                                    onStatus = { msg -> post {
                                        val gone = msg.contains("failed to connect") || msg.contains("did not answer")
                                        status = if (gone)
                                        "${phone.name} DOES NOT ANSWER AT ${phone.address}. Locked since a restart (Tailscale starts only after " +
                                        "the first unlock), Tailscale off, or adb not on 5555 (adb tcpip 5555 from the Mac). Trying again…"
                                        else "${phone.name}: $msg"
                                        // v16: two misses and a PIN known: the Mac, which holds the phone on its cable, unlocks
                                        // it and arms 5555 again (at most once a minute)
                                        if (gone) misses++
                                        val now = System.currentTimeMillis()
                                        if (gone && misses >= 2 && pin.isNotEmpty() && now - macAskedAt > 60_000) {
                                            macAskedAt = now
                                            status = "${phone.name} DOES NOT ANSWER: ASKING THE MAC TO UNLOCK IT OVER ITS CABLE…"
                                            Thread {
                                                val r = MacSister.unlockPhone(MacFinder.MAC_TAILSCALE_IP, phone.name, pin)
                                                post { status = when {
                                                    r == null -> "${phone.name}: THE MAC DOES NOT ANSWER (Dock Switcher on port ${MacSister.PORT})"
                                                    r.first -> "THE MAC: ${r.second.uppercase()}. CONNECTING…"
                                                    else -> "THE MAC: ${r.second.uppercase()}"
                                                } }
                                            }.start()
                                        }
                                    } },
                                    onSize = { nw, nh -> post { w = nw; h = nh } },
                                    onPairingNeeded = { post { status = "${phone.name}: allow this phone on the ${phone.name} (\"Allow USB debugging?\" → Always)" } },
                                    // the light picture of the Camera Remote on mobile data, enough to drive a phone
                                    quality = { Triple(2_000_000, 1280, 24) })
                                link = a
                                a.start()
                            }
                            override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, sw: Int, sh: Int) {}
                            override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean { link?.stop(); link = null; return true }
                            override fun onSurfaceTextureUpdated(t: SurfaceTexture) {}
                        }
                        var lastX = 0f; var lastY = 0f; var travel = 0f; var downAt = 0L
                        val holdCheck = Runnable {
                            // a finger held still for HOLD_MS: the button goes down where the pointer is
                            if (mouse && !held && travel < SLOP_PX) { held = true; link?.finger(MotionEvent.ACTION_DOWN, px, py) }
                        }
                        // v53: a finger that lands on the left or right edge and goes inward opens the menu; it
                        // reaches the phone only when it does something else (a tap there, or a move along the edge)
                        var edge = false; var edgeX = 0f; var edgeY = 0f; var skip = false
                        setOnTouchListener { v, ev ->
                            val a = link ?: return@setOnTouchListener true
                            val edgePx = EDGE_DP * v.resources.displayMetrics.density
                            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                                edge = ev.x < edgePx || ev.x > v.width - edgePx
                                edgeX = ev.x; edgeY = ev.y
                                if (edge) return@setOnTouchListener true
                            }
                            if (edge) {
                                val inward = if (edgeX < v.width / 2) ev.x - edgeX else edgeX - ev.x
                                val fx = (edgeX / v.width).coerceIn(0f, 1f); val fy = (edgeY / v.height).coerceIn(0f, 1f)
                                when {
                                    ev.actionMasked == MotionEvent.ACTION_MOVE && inward > SWIPE_PX -> { edge = false; menu = true; skip = true }
                                    ev.actionMasked == MotionEvent.ACTION_MOVE && kotlin.math.abs(ev.y - edgeY) > SWIPE_PX -> {
                                        // a move along the edge: it was the phone's after all, given from where it started
                                        edge = false; lastX = ev.x; lastY = ev.y
                                        if (!mouse) a.touch(MotionEvent.ACTION_DOWN, 0, intArrayOf(ev.getPointerId(0)), floatArrayOf(fx), floatArrayOf(fy))
                                    }
                                    ev.actionMasked == MotionEvent.ACTION_UP -> {
                                        edge = false
                                        if (!mouse) { a.finger(MotionEvent.ACTION_DOWN, fx, fy); a.finger(MotionEvent.ACTION_UP, fx, fy) }
                                        return@setOnTouchListener true
                                    }
                                    else -> return@setOnTouchListener true
                                }
                                if (skip) return@setOnTouchListener true
                            }
                            if (skip) { if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) skip = false; return@setOnTouchListener true }
                            if (!mouse) {
                                val n = ev.pointerCount
                                val ids = IntArray(n) { ev.getPointerId(it) }
                                val xs = FloatArray(n) { (ev.getX(it) / v.width).coerceIn(0f, 1f) }
                                val ys = FloatArray(n) { (ev.getY(it) / v.height).coerceIn(0f, 1f) }
                                a.touch(ev.actionMasked, ev.actionIndex, ids, xs, ys)
                                return@setOnTouchListener true
                            }
                            when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    lastX = ev.x; lastY = ev.y; travel = 0f; downAt = ev.eventTime; held = false
                                    v.postDelayed(holdCheck, HOLD_MS)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = ev.x - lastX; val dy = ev.y - lastY
                                    lastX = ev.x; lastY = ev.y; travel += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                                    px = (px + dx / v.width * SPEED).coerceIn(0f, 1f)
                                    py = (py + dy / v.height * SPEED).coerceIn(0f, 1f)
                                    if (held) a.finger(MotionEvent.ACTION_MOVE, px, py)
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    v.removeCallbacks(holdCheck)
                                    if (held) { a.finger(MotionEvent.ACTION_UP, px, py); held = false }
                                    else if (ev.actionMasked == MotionEvent.ACTION_UP && travel < SLOP_PX && ev.eventTime - downAt < HOLD_MS) {
                                        a.finger(MotionEvent.ACTION_DOWN, px, py); a.finger(MotionEvent.ACTION_UP, px, py)   // a click
                                    }
                                }
                            }
                            true
                        }
                    }
                },
            )
            // the pointer: an arrow at its place on the picture, red while the button is held
            if (mouse) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val x = px * size.width; val y = py * size.height; val k = 1.6f * density
                val arrow = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, y); lineTo(x, y + 18 * k); lineTo(x + 5 * k, y + 13 * k); lineTo(x + 9 * k, y + 21 * k)
                    lineTo(x + 12 * k, y + 19.5f * k); lineTo(x + 8 * k, y + 12 * k); lineTo(x + 14 * k, y + 12 * k); close()
                }
                drawPath(arrow, if (held) Color(0xFFE5484D) else Color.White)
                drawPath(arrow, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density))
            }
            }
        }
        // v53: the menu, over the picture, opened from the edge; a key does its work and closes it, ✕ or Back closes it
        if (menu) Column(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { menu = false }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PhoneKey("◀ MAC", Modifier.weight(1f)) { menu = false; onMac() }
                PhoneKey("✕", Modifier.weight(0.5f)) { menu = false }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PhoneKey("BACK", Modifier.weight(1f)) { menu = false; link?.key("BACK") }
                PhoneKey("HOME", Modifier.weight(1f)) { menu = false; link?.key("HOME") }
                PhoneKey("APPS", Modifier.weight(1f)) { menu = false; link?.key("APPS") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PhoneKey(if (mouse) "MOUSE (TAP: TOUCH)" else "TOUCH (TAP: MOUSE)", Modifier.weight(2f)) { mouse = !mouse; menu = false }
                PhoneKey("⌨", Modifier.weight(1f)) { keyboard = !keyboard; menu = false }
            }
            androidx.compose.material3.Text(status, color = Color(0xFF7FE0E6), fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(4.dp))
        }
        // the typing field over the bottom of the picture while ⌨ is on
        if (keyboard) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            TypingField(onText = { t -> if (t == "\n") link?.key("ENTER") else link?.text(t) }, onBackspace = { link?.key("DEL") })
        }
        // until the picture comes, what is happening is written in the middle
        if (w == 0) androidx.compose.material3.Text(status, color = Color(0xFF7FE0E6), fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.align(Alignment.Center).padding(24.dp))
    }
}

@Composable
private fun PhoneKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    // the same white flash as every key of this app (v15)
    var flash by remember { mutableIntStateOf(0) }
    LaunchedEffect(flash) { if (flash > 0) { kotlinx.coroutines.delay(500); flash = 0 } }
    val lit = flash > 0
    Box(
        modifier.background(Color(0xFF15171A)).padding(vertical = 12.dp)
            .clickable { flash++; onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(label, color = if (lit) Color.White else Color(0xFFFFB000),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

/** v16: how far the pointer goes for a finger's travel, how long a still finger takes to hold the button, and how
 *  far a finger may wander and still be a tap. */
private const val SPEED = 1.6f
private const val HOLD_MS = 350L
private const val SLOP_PX = 24f
/** v53: how near the left or right edge a finger must land, and how far inward it must go, to open the menu */
private const val EDGE_DP = 24f
private const val SWIPE_PX = 60f
