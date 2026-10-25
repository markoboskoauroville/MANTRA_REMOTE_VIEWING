package com.mantra.remote

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.TrafficStats
import android.os.Bundle
import android.os.Process
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// AMBER PHOSPHOR (FEATURE_REQUEST.md PART 1). Amber is the voice of the machine, cyan the voice of the numbers.
private val Bg = Color(0xFF0A0A0A)
private val BgRaised = Color(0xFF141210)
private val Amber = Color(0xFFFFB000)
private val AmberDim = Color(0xFF8A5F00)
private val AmberGlow = Color(0xFFFFD37A)
private val GreenOk = Color(0xFF33FF66)
private val RedAlert = Color(0xFFFF3B30)
private val Cyan = Color(0xFF4FD8E8)
private val Rule = Color(0xFF2A2320)
private val Mono = FontFamily.Monospace

/**
 * The Mac's monitors as Apple's VNC server lays them out (measured 25.10.2026): one framebuffer of 5584x1964, the
 * displays side by side in their own PIXELS, tops aligned. The BenQ (main, 2560x1440) on the left, the MacBook Pro's
 * Retina screen (3024x1964 pixels, 1512x982 points) to its right.
 */
private data class Monitor(val name: String, val x: Int, val y: Int, val w: Int, val h: Int)
private val MONITORS = listOf(
    Monitor("1 BENQ", 0, 0, 2560, 1440),
    Monitor("2 MACBOOK", 2560, 0, 3024, 1964),
)

private const val PREFS = "remote"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(getSharedPreferences(PREFS, Context.MODE_PRIVATE)) }
    }
}

private enum class Phase { SETUP, CONNECTING, LIVE }

private class Session : Rfb.Listener {
    var phase by mutableStateOf(Phase.SETUP)
    var status by mutableStateOf("READY")
    var statusBad by mutableStateOf(false)
    var name by mutableStateOf("")
    var fbW by mutableIntStateOf(0)
    var fbH by mutableIntStateOf(0)
    var tick by mutableLongStateOf(0L)
    var frames by mutableLongStateOf(0L)
    var lastMs by mutableLongStateOf(0L)
    var rfb: Rfb? = null
    var full by mutableStateOf(false)
    var loginPassword = ""
    var landscape by mutableStateOf(false)

    override fun onConnected(name: String, width: Int, height: Int) {
        android.util.Log.i("MantraRemote", "framebuffer ${width}x$height name '$name'")
        if (name.isNotEmpty()) this.name = name
        fbW = width; fbH = height
        phase = Phase.LIVE; status = "LINK ESTABLISHED"; statusBad = false
    }
    override fun onFrame() { tick++ }
    override fun onClosed(reason: String) {
        phase = Phase.SETUP; status = reason.uppercase(); statusBad = true
    }
    override fun onStats(bytesIn: Long, frames: Long, lastFrameMs: Long) { this.frames = frames; lastMs = lastFrameMs }
}

@Composable
private fun App(prefs: android.content.SharedPreferences) {
    val session = remember { Session().also { it.loginPassword = prefs.getString("login", "") ?: "" } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .then(if (session.full) Modifier else Modifier.safeDrawingPadding())
    ) {
        when (session.phase) {
            Phase.SETUP, Phase.CONNECTING -> Setup(prefs, session)
            Phase.LIVE -> Live(session)
        }
    }
}

@Composable
private fun T(text: String, color: Color = Amber, size: Int = 16, bold: Boolean = false, modifier: Modifier = Modifier) =
    androidx.compose.material3.Text(
        text, modifier = modifier, color = color, fontFamily = Mono, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.4f).sp,
    )

/** A finger-sized key: amber box line, label inside, lit while active. */
@Composable
private fun Key(label: String, modifier: Modifier = Modifier, active: Boolean = false, size: Int = 16, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier
            .height(52.dp)
            .background(if (active) Amber else BgRaised)
            .border(1.dp, if (active) AmberGlow else Rule)
            .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, if (active) Bg else Amber, size, bold = true) }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        T(label, AmberDim, 13)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Amber, fontFamily = Mono, fontSize = 22.sp),
            cursorBrush = SolidColor(AmberGlow),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Number else if (secret) KeyboardType.Password else KeyboardType.Uri,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth().border(1.dp, Rule).padding(12.dp),
        )
    }
}

@Composable
private fun Setup(prefs: android.content.SharedPreferences, s: Session) {
    var host by remember { mutableStateOf(prefs.getString("host", "mantra-mac") ?: "mantra-mac") }
    var port by remember { mutableStateOf(prefs.getString("port", "5900") ?: "5900") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var login by remember { mutableStateOf(prefs.getString("login", "") ?: "") }
    Column(
        Modifier.fillMaxSize().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T("MANTRA REMOTE ▊", Amber, 34, bold = true)
        T("── THE MAC ───────────────────", Rule, 16)
        Field("HOST (TAILSCALE NAME OR 100.x.x.x)", host) { host = it.trim() }
        Field("PORT", port, number = true) { port = it.filter(Char::isDigit) }
        Field("VNC PASSWORD (SCREEN SHARING)", password, secret = true) { password = it }
        Field("MAC LOGIN PASSWORD (THE LOGIN BUTTON TYPES IT)", login, secret = true) { login = it }
        Spacer(Modifier.height(12.dp))
        Key(if (s.phase == Phase.CONNECTING) "DIALLING …" else "CONNECT", Modifier.fillMaxWidth(), size = 22) {
            if (s.phase == Phase.CONNECTING) return@Key
            prefs.edit().putString("host", host).putString("port", port).putString("password", password).putString("login", login).apply()
            s.loginPassword = login
            s.phase = Phase.CONNECTING; s.status = "> DIAL $host:$port"; s.statusBad = false
            val rfb = Rfb(s); s.rfb = rfb
            rfb.connect(host, port.toIntOrNull() ?: 5900, password)
        }
        Spacer(Modifier.height(12.dp))
        T("> " + s.status, if (s.statusBad) RedAlert else GreenOk, 16)
        Spacer(Modifier.height(16.dp))
        T("Over the internet the phone and the Mac meet on Tailscale: both signed in to the same account, " +
            "the phone's Tailscale switched on. The host is then the Mac's Tailscale name.", AmberDim, 13)
    }
}

/** Framebuffer rectangle of a monitor. A framebuffer of another size (other displays attached) is shown whole. */
private fun monitorRect(i: Int, fbW: Int, fbH: Int): IntArray {
    val totalW = MONITORS.maxOf { it.x + it.w }
    val totalH = MONITORS.maxOf { it.y + it.h }
    if (fbW != totalW || fbH != totalH) return intArrayOf(0, 0, fbW, fbH)
    val m = MONITORS[i]
    return intArrayOf(m.x, m.y, m.w, m.h)
}

private val FPS = listOf("0.5" to 2000L, "1" to 1000L, "2" to 500L, "5" to 200L, "10" to 100L, "MAX" to 0L)

// X11 keysyms as RFB wants them. Apple's server reads Super as Command and Alt as Option.
private const val K_CMD = 0xffeb
private const val K_OPT = 0xffe9
private const val K_CTRL = 0xffe3
private const val K_SHIFT = 0xffe1
private const val K_BACKSPACE = 0xff08
private const val K_TAB = 0xff09
private const val K_RETURN = 0xff0d
private const val K_ESC = 0xff1b
private const val K_LEFT = 0xff51
private const val K_UP = 0xff52
private const val K_RIGHT = 0xff53
private const val K_DOWN = 0xff54

@Composable
private fun Live(s: Session) {
    val rfb = s.rfb ?: return
    var monitor by remember { mutableIntStateOf(0) }
    var fpsIndex by remember { mutableIntStateOf(3) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scrollMode by remember { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    val mods = remember { mutableStateOf(setOf<Int>()) }
    var rx by remember { mutableLongStateOf(0L) }
    var tx by remember { mutableLongStateOf(0L) }
    val rx0 = remember { TrafficStats.getUidRxBytes(Process.myUid()) }
    val tx0 = remember { TrafficStats.getUidTxBytes(Process.myUid()) }
    var kbps by remember { mutableLongStateOf(0L) }
    val view = LocalView.current

    val rect = monitorRect(monitor, s.fbW, s.fbH)
    LaunchedEffect(monitor, s.fbW, s.fbH) {
        rfb.setRegion(rect[0], rect[1], rect[2], rect[3]); zoom = 1f; pan = Offset.Zero
    }
    LaunchedEffect(fpsIndex) { rfb.minIntervalMs = FPS[fpsIndex].second; rfb.wake() }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val r = TrafficStats.getUidRxBytes(Process.myUid()) - rx0
            tx = TrafficStats.getUidTxBytes(Process.myUid()) - tx0
            kbps = (r - last) * 8 / 1000; last = r; rx = r
            delay(1000)
        }
    }

    // fit the monitor's rectangle into the view, then the finger's zoom and pan on top
    // functions, not values: the gesture loop lives longer than one composition and must read today's zoom
    fun fit() = if (viewSize.width > 0 && rect[2] > 0) minOf(viewSize.width / rect[2].toFloat(), viewSize.height / rect[3].toFloat()) else 1f
    fun scale() = fit() * zoom
    fun baseX() = (viewSize.width - rect[2] * fit()) / 2f
    fun baseY() = (viewSize.height - rect[3] * fit()) / 2f
    fun toFb(p: Offset): Pair<Int, Int> {
        val x = rect[0] + ((p.x - baseX() - pan.x) / scale())
        val y = rect[1] + ((p.y - baseY() - pan.y) / scale())
        return x.roundToInt().coerceIn(rect[0], rect[0] + rect[2] - 1) to y.roundToInt().coerceIn(rect[1], rect[1] + rect[3] - 1)
    }
    fun clampPan(p: Offset): Offset {
        val maxX = (rect[2] * scale() - rect[2] * fit()).coerceAtLeast(0f)
        val maxY = (rect[3] * scale() - rect[3] * fit()).coerceAtLeast(0f)
        // zoom grows around the origin of the fitted picture; pan may move it back by up to the extra size
        return Offset(p.x.coerceIn(-maxX, 0f), p.y.coerceIn(-maxY, 0f))
    }
    fun withMods(block: () -> Unit) {
        val m = mods.value
        m.forEach { rfb.key(it, true) }
        block()
        m.reversed().forEach { rfb.key(it, false) }
        if (m.isNotEmpty()) mods.value = emptySet()
    }
    fun tapKey(k: Int) = withMods { rfb.key(k, true); rfb.key(k, false) }

    val activity = LocalContext.current as ComponentActivity
    // full screen is always landscape; otherwise the phone stands as Marko chose (portrait by default)
    LaunchedEffect(s.full, s.landscape) {
        activity.requestedOrientation =
            if (s.full || s.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (s.full) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else bars.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose {
            s.full = false
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val screen: @Composable (Modifier) -> Unit = { modifier ->
    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
            .pointerInput(rect.contentHashCode(), scrollMode) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = down.position
                    val t0 = System.currentTimeMillis()
                    var moved = false
                    var multi = false
                    var dragging = false
                    var last = start
                    var scrollAcc = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            // two fingers: zoom and pan the picture, nothing is sent to the Mac
                            multi = true
                            val a = pressed[0]; val b = pressed[1]
                            val prevDist = hypot(a.previousPosition.x - b.previousPosition.x, a.previousPosition.y - b.previousPosition.y)
                            val dist = hypot(a.position.x - b.position.x, a.position.y - b.position.y)
                            val centre = (a.position + b.position) / 2f
                            val prevCentre = (a.previousPosition + b.previousPosition) / 2f
                            if (prevDist > 0f) {
                                val newZoom = (zoom * dist / prevDist).coerceIn(1f, 6f)
                                val k = newZoom / zoom
                                // keep the point under the fingers where it is
                                val origin = Offset(baseX(), baseY())
                                pan = clampPan((pan - (centre - origin)) * k + (centre - origin) + (centre - prevCentre))
                                zoom = newZoom
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val c = pressed[0]
                        val p = c.position
                        if (!moved && (p - start).getDistance() > 24f) moved = true
                        if (multi) { c.consume(); continue }
                        if (scrollMode) {
                            // one finger in SCROLL: up and down turn the Mac's wheel
                            scrollAcc += c.positionChange().y
                            val (fx, fy) = toFb(start)
                            while (abs(scrollAcc) > 40f) {
                                val button = if (scrollAcc > 0) 8 else 16   // finger down = content down = wheel up
                                rfb.pointer(fx, fy, button); rfb.pointer(fx, fy, 0)
                                scrollAcc -= 40f * kotlin.math.sign(scrollAcc)
                            }
                        } else if (!dragging && !moved && System.currentTimeMillis() - t0 > 450) {
                            // a long press: the button goes down and the finger drags
                            dragging = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val (fx, fy) = toFb(p); rfb.pointer(fx, fy, 1)
                        } else if (moved) {
                            val (fx, fy) = toFb(p); rfb.pointer(fx, fy, if (dragging) 1 else 0)
                        }
                        last = p
                        c.consume()
                    }
                    if (multi || scrollMode) return@awaitEachGesture
                    val (fx, fy) = toFb(last)
                    when {
                        dragging && !moved -> { rfb.pointer(fx, fy, 0); rfb.pointer(fx, fy, 4); rfb.pointer(fx, fy, 0) } // long press = right click
                        dragging -> rfb.pointer(fx, fy, 0)
                        !moved -> { rfb.pointer(fx, fy, 0); rfb.pointer(fx, fy, 1); rfb.pointer(fx, fy, 0) }           // tap = click
                        else -> Unit                                                                                // a move only
                    }
                }
            }
    ) {
        val bmp = rfb.bitmap
        if (bmp != null && rect[2] > 0) {
            Canvas(Modifier.fillMaxSize()) {
                s.tick // redraw on every frame
                drawImage(
                    bmp.asImageBitmap(),
                    srcOffset = IntOffset(rect[0], rect[1]),
                    srcSize = IntSize(rect[2], rect[3]),
                    dstOffset = IntOffset((baseX() + pan.x).roundToInt(), (baseY() + pan.y).roundToInt()),
                    dstSize = IntSize((rect[2] * scale()).roundToInt(), (rect[3] * scale()).roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
    }

    val monitors: @Composable (Modifier) -> Unit = { modifier ->
        MONITORS.forEachIndexed { i, m ->
            Key("MON " + m.name, modifier, active = monitor == i) { monitor = i }
        }
    }
    val close: @Composable () -> Unit = {
        Key("✕", Modifier.width(56.dp)) { rfb.close(); s.phase = Phase.SETUP; s.status = "LINK CLOSED"; s.statusBad = false }
    }
    val meter: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("↓ %.2f MB  ↑ %.2f MB".format(rx / 1e6, tx / 1e6), Cyan, 13)
            T("$kbps kbit/s", Cyan, 13)
            T("${s.lastMs} ms", Cyan, 13)
        }
    }
    val modKeys: @Composable () -> Unit = {
        listOf("⌘" to K_CMD, "⌥" to K_OPT, "⌃" to K_CTRL, "⇧" to K_SHIFT).forEach { (label, k) ->
            Key(label, Modifier.width(52.dp), active = k in mods.value, size = 22) {
                mods.value = if (k in mods.value) mods.value - k else mods.value + k
            }
        }
    }
    val specialKeys = listOf("ESC" to K_ESC, "TAB" to K_TAB, "⏎" to K_RETURN, "⌫" to K_BACKSPACE,
        "←" to K_LEFT, "↑" to K_UP, "↓" to K_DOWN, "→" to K_RIGHT)
    val keysRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modKeys()
            specialKeys.forEach { (label, k) -> Key(label, Modifier.width(60.dp), size = 18) { tapKey(k) } }
        }
    }
    fun typeText(text: String) = text.forEach { ch ->
        when (ch) {
            '\n' -> tapKey(K_RETURN)
            else -> withMods {
                val ks = if (ch.code < 256) ch.code else 0x01000000 + ch.code
                val shift = ch.isUpperCase() || ch in "~!@#$%^&*()_+{}|:\"<>?"
                if (shift) rfb.key(K_SHIFT, true)
                rfb.key(ks, true); rfb.key(ks, false)
                if (shift) rfb.key(K_SHIFT, false)
            }
        }
    }
    // LOGIN: the Mac password from the settings, typed into whatever asks for it (lock screen, a dialog), then Return
    fun login() {
        if (s.loginPassword.isEmpty()) return
        mods.value = emptySet()
        typeText(s.loginPassword); tapKey(K_RETURN)
    }
    val typing: @Composable () -> Unit = {
        if (keyboard) TypingField(onText = { typeText(it) }, onBackspace = { tapKey(K_BACKSPACE) })
    }
    val toggles: @Composable (Modifier) -> Unit = { modifier ->
        Key("⌨ KEYS", modifier, active = keyboard) { keyboard = !keyboard }
        Key("SCROLL", modifier, active = scrollMode) { scrollMode = !scrollMode }
        Key("${FPS[fpsIndex].first} FPS", modifier) { fpsIndex = (fpsIndex + 1) % FPS.size }
        Key("LOGIN", modifier) { login() }
    }
    val views: @Composable (Modifier) -> Unit = { modifier ->
        Key(if (s.landscape) "▯ PORTRAIT" else "▭ LANDSCAPE", modifier) { s.landscape = !s.landscape }
        Key("⛶ FULL SCREEN", modifier, active = true) { s.full = true }
    }

    when {
        s.full -> Box(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
            // the Mac's screen edge to edge; the actions are one line of faint words in the bottom right corner, like
            // subtitles: the middle of the bottom edge is the Dock, the corner is the emptiest place on a Mac
            screen(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    Overlay("EXIT") { s.full = false }
                    MONITORS.forEachIndexed { i, m -> Overlay("MON " + m.name.take(1), active = monitor == i) { monitor = i } }
                    Overlay("KEYS", active = keyboard) { keyboard = !keyboard }
                    Overlay("SCROLL", active = scrollMode) { scrollMode = !scrollMode }
                    Overlay("LOGIN") { login() }
                }
                if (keyboard) Column(Modifier.fillMaxWidth().background(Color(0xB0000000))) {
                    keysRow()
                    typing()
                }
            }
        }
        s.landscape -> Row(Modifier.fillMaxSize().imePadding()) {
            screen(Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { monitors(Modifier.weight(1f)); close() }
                meter()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { modKeys() }
                specialKeys.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (label, k) -> Key(label, Modifier.weight(1f), size = 18) { tapKey(k) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { toggles(Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { views(Modifier.weight(1f)) }
                typing()
            }
        }
        else -> Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                monitors(Modifier.weight(1f)); close()
            }
            meter()
            screen(Modifier.weight(1f).fillMaxWidth())
            keysRow()
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { toggles(Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { views(Modifier.weight(1f)) }
            typing()
        }
    }
}

/**
 * An action over the full-screen picture, drawn like a subtitle (Marko, 25.10.2026): words only, no frame, no
 * background, no shadow, half transparent. The touch area is a thumb's size; only the letters show.
 */
@Composable
private fun Overlay(label: String, active: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .clickable { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, Amber.copy(alpha = if (active) 0.8f else 0.4f), 11, bold = active) }
}

/**
 * The phone's own keyboard. The field always holds one space; what is typed after it is sent, a delete that eats
 * the space is a backspace, and the field goes back to its one space.
 */
@Composable
private fun TypingField(onText: (String) -> Unit, onBackspace: () -> Unit) {
    val base = TextFieldValue(" ", TextRange(1))
    var value by remember { mutableStateOf(base) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); kb?.show() }
    BasicTextField(
        value = value,
        onValueChange = { v ->
            when {
                v.text.isEmpty() -> onBackspace()
                v.text.length > 1 -> onText(v.text.substring(1))
            }
            value = base
        },
        textStyle = TextStyle(color = Amber, fontFamily = Mono, fontSize = 16.sp),
        cursorBrush = SolidColor(AmberGlow),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.None,
        ),
        modifier = Modifier.fillMaxWidth().size(1.dp).focusRequester(focus),
    )
}
