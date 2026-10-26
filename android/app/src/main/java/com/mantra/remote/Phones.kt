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
        androidx.compose.material3.Text(status, color = Color(0xFF7FE0E6), fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val ratio = if (w > 0 && h > 0) w.toFloat() / h else 1080f / 2400f
            AndroidView(
                modifier = Modifier.aspectRatio(ratio),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(t: SurfaceTexture, sw: Int, sh: Int) {
                                val (host, port) = phone.address.substringBefore(':') to (phone.address.substringAfter(':').toIntOrNull() ?: 5555)
                                val a = AdbLink(context.applicationContext, { host to port }, Surface(t),
                                    onStatus = { msg -> post { status = "${phone.name}: $msg" } },
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
                        setOnTouchListener { v, ev ->
                            val a = link ?: return@setOnTouchListener true
                            val n = ev.pointerCount
                            val ids = IntArray(n) { ev.getPointerId(it) }
                            val xs = FloatArray(n) { (ev.getX(it) / v.width).coerceIn(0f, 1f) }
                            val ys = FloatArray(n) { (ev.getY(it) / v.height).coerceIn(0f, 1f) }
                            a.touch(ev.actionMasked, ev.actionIndex, ids, xs, ys)
                            true
                        }
                    }
                },
            )
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
