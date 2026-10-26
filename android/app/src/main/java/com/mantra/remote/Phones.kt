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
    /** The Pixel 7 on Tailscale (adb on TCP 5555), known from the start. */
    private val DEFAULT = listOf(Phone("PIXEL 7", "100.70.23.53:5555"))

    fun all(prefs: android.content.SharedPreferences?): List<Phone> {
        val saved = prefs?.getString(KEY, null) ?: return DEFAULT
        return saved.lines().mapNotNull { l -> l.split('|').takeIf { it.size == 2 }?.let { Phone(it[0], it[1]) } }
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
fun PhoneScreen(phone: Phone, onMac: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf("${phone.name}: CONNECTING TO ${phone.address}") }
    var w by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf<AdbLink?>(null) }
    // v16 (Marko, 26.10.2026: "I want to see my mouse on the screen ... I press and I drag, and it acts like ...
    // keeping my left mouse button down"): MOUSE, a pointer moved like a trackpad (tap = click, hold then drag =
    // the button held), or TOUCH, the finger is the finger; ⌨ types into the phone
    var mouse by remember { mutableStateOf(true) }
    var keyboard by remember { mutableStateOf(false) }
    var px by remember { mutableStateOf(0.5f) }
    var py by remember { mutableStateOf(0.5f) }
    var held by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { link?.key("BACK") }

    DisposableEffect(phone.address) { onDispose { link?.stop(); link = null } }
    // the status line: what arrives, every second, as on the Mac's
    LaunchedEffect(link) {
        var last = 0L to 0L
        while (true) {
            kotlinx.coroutines.delay(1000)
            val a = link ?: continue
            val now = a.frames.get() to a.bytes.get()
            if (a.connected && a.width > 0) status = "%s · %s · %d fps · %d kbit/s".format(
                phone.name, a.host, now.first - last.first, ((now.second - last.second) * 8 / 1000).toInt())
            last = now
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PhoneKey("◀ MAC", Modifier.weight(1f)) { onMac() }
            PhoneKey("BACK", Modifier.weight(1f)) { link?.key("BACK") }
            PhoneKey("HOME", Modifier.weight(1f)) { link?.key("HOME") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PhoneKey(if (mouse) "MOUSE" else "TOUCH", Modifier.weight(1f)) { mouse = !mouse }
            PhoneKey("⌨", Modifier.weight(1f)) { keyboard = !keyboard }
            PhoneKey("APPS", Modifier.weight(1f)) { link?.key("APPS") }
        }
        androidx.compose.material3.Text(status, color = Color(0xFF7FE0E6), fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp))
        if (keyboard) TypingField(onText = { t -> if (t == "\n") link?.key("ENTER") else link?.text(t) }, onBackspace = { link?.key("DEL") })
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
                                    onStatus = { msg -> post { status = if (msg.contains("failed to connect") || msg.contains("did not answer"))
                                        "${phone.name} DOES NOT ANSWER AT ${phone.address}. Locked since a restart (Tailscale starts only after " +
                                        "the first unlock), Tailscale off, or adb not on 5555 (adb tcpip 5555 from the Mac). Trying again…"
                                        else "${phone.name}: $msg" } },
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
                        setOnTouchListener { v, ev ->
                            val a = link ?: return@setOnTouchListener true
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
