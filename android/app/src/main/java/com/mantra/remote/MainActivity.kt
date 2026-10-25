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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.unit.em
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

/** The key whose explanation is open (a long press on any key, Marko 25.10.2026), or null. */
private val helpFor = mutableStateOf<String?>(null)

/** What each key does, in a sentence or two. Found by the key's word, so every layout shares one text. */
private fun helpText(label: String): String {
    val l = label.uppercase()
    return when {
        l.startsWith("MON 1") -> "Shows monitor 1, the BenQ. Only this monitor's picture travels to the phone, so it costs less data than both."
        l.startsWith("MON 2") -> "Shows monitor 2, the MacBook Pro's own screen. Only this monitor's picture travels to the phone."
        l == "✕" -> "Closes the link to the Mac and goes back to the settings page."
        l.contains("KEYS") -> "Opens the phone's keyboard. What you type is typed on the Mac."
        l == "SCROLL" -> "While it is lit, one finger dragging up or down turns the Mac's mouse wheel instead of moving the pointer."
        l.contains("FPS") -> "Frames per second: how often a new picture of the Mac is fetched. Fewer is cheaper on mobile data. A tap steps to the next; a long press opens the list."
        l == "FULL" || l == "65K" || l == "4096" || l == "512" || l == "64" || l == "8" || l == "B/W" || l == "COL" -> "Colours: how many the Mac sends. Fewer colours cost far less mobile data: 8 colours or black and white about a sixth of full colour. A tap steps to the next; a long press opens the list."
        l.startsWith("CHROME") -> "The next Chrome tab (Control-Tab on the Mac). Tap again and again to go through the tabs; after the last one it starts at the first. Chrome must be the app in front. It works in VIEW ONLY too."
        l == "LOGIN" -> "Types your Mac password from the settings, then Return: for any password box that pops up on the Mac."
        l.contains("LANDSCAPE") -> "Turns the app sideways: the Mac on the left, the keys on the right."
        l.contains("PORTRAIT") -> "Turns the app upright again: the Mac in the middle, the keys below."
        l.contains("FULL") -> "Full screen: the phone turns sideways and the Mac fills it, with the keys as words at the sides. EXIT brings you back."
        l == "EXIT" -> "Leaves full screen."
        l.startsWith("VIEW") -> "View only: you can watch, zoom and move around the picture, but nothing reaches the Mac: no click, no key, no scroll. One finger moves the zoomed picture. Tap again to control the Mac."
        l == "⌘" -> "Command. Tap it, then a key or a letter: the two are pressed together (⌘ then Q quits an app). It turns off after one use."
        l == "⌥" -> "Option. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⌃" || l == "^" -> "Control. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⇧" -> "Shift. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "ESC" -> "Escape: closes a menu or a dialog, cancels."
        l == "TAB" -> "Tab: the next field. With ⌘ first, it switches between apps."
        l == "⏎" -> "Return: confirms, sends, a new line."
        l == "⌫" -> "Delete: removes the letter before the cursor."
        l == "←" || l == "→" || l == "↑" || l == "↓" -> "The arrow key, as on the Mac's keyboard."
        l == "CONNECT" -> "Dials the Mac through Tailscale and logs in by itself with the user and password above."
        else -> label
    }
}

/** The explanation of the key that was long-pressed, in the app's own ink. A tap anywhere closes it. */
@Composable
private fun HelpPopup() {
    val label = helpFor.value ?: return
    Dialog(onDismissRequest = { helpFor.value = null }) {
        Column(
            Modifier.background(Bg).border(1.dp, Rule).clickable { helpFor.value = null }.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(label, Amber, 22, bold = true)
            T(helpText(label), Color(0xFFF2DDB4), 16)
        }
    }
}

/** The link outlives the window: a window Android rebuilt finds the same session, still connected. */
private object Hold {
    var session: Session? = null
}

/** In the background the picture comes at 0.2 FPS; in front, at what Marko chose (Marko, 25.10.2026). */
private const val BACKGROUND_MS = 5000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val session = Hold.session ?: Session().also {
            it.loginPassword = prefs.getString("login", "") ?: ""; it.prefs = prefs; it.app = applicationContext
            Hold.session = it
        }
        setContent { App(prefs, session) }
    }

    override fun onStart() {
        super.onStart()
        val s = Hold.session ?: return
        s.background = false
        s.rfb?.let { it.minIntervalMs = s.fpsMs; it.wake() }
        // the link fell while away (the network changed, the Mac slept): dial again by itself, no CONNECT needed
        if (s.wanted && s.phase == Phase.SETUP) { s.phase = Phase.CONNECTING; s.status = "> DIAL AGAIN"; s.statusBad = false; s.dial(coloursSaved(s.prefs)) }
    }

    override fun onStop() {
        super.onStop()
        val s = Hold.session ?: return
        s.background = true
        s.rfb?.minIntervalMs = BACKGROUND_MS
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
    var rfb by mutableStateOf<Rfb?>(null)
    /** What the last CONNECT dialled, so a change of colours can dial again with the same. */
    var dial: (Colours) -> Unit = {}
    /** VNC bytes of the links already closed in this session (a change of colours dials again). */
    var baseRx = 0L
    var baseTx = 0L
    var full by mutableStateOf(false)
    var loginPassword = ""
    var prefs: android.content.SharedPreferences? = null
    var landscape by mutableStateOf(false)
    var app: Context? = null
    /** The frame interval Marko chose; the background uses [BACKGROUND_MS] and comes back to this. */
    var fpsMs = 1000L
    var background = false
    /** A link is wanted (CONNECT was pressed, ✕ not): a link that falls is dialled again on return. */
    var wanted = false

    /** ✕: the link is closed on purpose, nothing dials again and the background service ends. */
    fun hangUp() {
        wanted = false; rfb?.close(); app?.let(LinkService::stop)
        phase = Phase.SETUP; status = "LINK CLOSED"; statusBad = false
    }

    override fun onConnected(name: String, width: Int, height: Int) {
        android.util.Log.i("MantraRemote", "framebuffer ${width}x$height name '$name'")
        if (name.isNotEmpty()) this.name = name
        fbW = width; fbH = height
        phase = Phase.LIVE; status = "LINK ESTABLISHED"; statusBad = false
        if (background) rfb?.minIntervalMs = BACKGROUND_MS
        app?.let(LinkService::start)
    }
    override fun onFrame() { tick++ }
    override fun onClosed(reason: String) {
        phase = Phase.SETUP; status = reason.uppercase(); statusBad = true
        // in front, the fall is shown; behind, the service stays and the link is dialled again on return
        if (!background) app?.let(LinkService::stop)
    }
    override fun onStats(bytesIn: Long, frames: Long, lastFrameMs: Long) { this.frames = frames; lastMs = lastFrameMs }
}

@Composable
private fun App(prefs: android.content.SharedPreferences, session: Session) {
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
        HelpPopup()
    }
}

@Composable
private fun T(text: String, color: Color = Amber, size: Int = 16, bold: Boolean = false, modifier: Modifier = Modifier) =
    androidx.compose.material3.Text(
        text, modifier = modifier, color = color, fontFamily = Mono, fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.4f).sp,
    )

/** A finger-sized key: amber box line, label inside, lit while active. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(label: String, modifier: Modifier = Modifier, active: Boolean = false, size: Int = 16, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier
            .height(52.dp)
            .background(if (active) Amber else BgRaised)
            .border(1.dp, if (active) AmberGlow else Rule)
            .combinedClickable(
                onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); (onLongClick ?: { helpFor.value = label })() },
            ) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
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
    var user by remember { mutableStateOf(prefs.getString("user", "markobosko") ?: "markobosko") }
    Column(
        Modifier.fillMaxSize().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T("MANTRA REMOTE ▊", Amber, 34, bold = true)
        T("── THE MAC ───────────────────", Rule, 16)
        Field("HOST (TAILSCALE NAME OR 100.x.x.x)", host) { host = it.trim() }
        Field("PORT", port, number = true) { port = it.filter(Char::isDigit) }
        Field("VNC PASSWORD (SCREEN SHARING)", password, secret = true) { password = it }
        Field("MAC USER (SHORT NAME)", user) { user = it.trim() }
        Field("MAC LOGIN PASSWORD (AUTO-LOGIN, AND THE LOGIN BUTTON)", login, secret = true) { login = it }
        Spacer(Modifier.height(12.dp))
        Key(if (s.phase == Phase.CONNECTING) "DIALLING …" else "CONNECT", Modifier.fillMaxWidth(), size = 22) {
            if (s.phase == Phase.CONNECTING) return@Key
            prefs.edit().putString("host", host).putString("port", port).putString("password", password).putString("login", login).putString("user", user).apply()
            s.loginPassword = login
            s.phase = Phase.CONNECTING; s.status = "> DIAL $host:$port"; s.statusBad = false
            s.rfb?.close(); s.rfb = null; s.baseRx = 0; s.baseTx = 0; s.wanted = true   // CONNECT starts a new session
            s.dial = { colours ->
                s.rfb?.let { old -> old.close(); s.baseRx += old.rxBytes.get(); s.baseTx += old.txBytes.get() }
                val rfb = Rfb(s, colours); s.rfb = rfb
                rfb.connect(host, port.toIntOrNull() ?: 5900, password, user, login)
            }
            s.dial(coloursSaved(prefs))
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

/** The colours chosen last time, remembered by name; FULL when none was chosen. */
private fun coloursSaved(prefs: android.content.SharedPreferences?): Colours {
    val saved = prefs?.getString("colours_label", "FULL") ?: "FULL"
    return Colours.ALL.firstOrNull { it.label == saved } ?: Colours.ALL[0]
}

private val FPS = listOf("0.2" to 5000L, "0.5" to 2000L, "1" to 1000L, "2" to 500L, "5" to 200L, "10" to 100L, "MAX" to 0L)

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
    // the frame rate is remembered between restarts; 1 FPS when nothing was chosen yet (Marko, 25.10.2026)
    var fpsIndex by remember {
        val saved = s.prefs?.getString("fps_label", "1") ?: "1"
        mutableIntStateOf(FPS.indexOfFirst { it.first == saved }.takeIf { it >= 0 } ?: FPS.indexOfFirst { it.first == "1" })
    }
    var fpsMenu by remember { mutableStateOf(false) }
    var colours by remember { mutableStateOf(coloursSaved(s.prefs)) }
    var coloursMenu by remember { mutableStateOf(false) }
    // a new number of colours changes every pixel's size: the app dials again (auto-login makes it a second)
    fun setColours(c: Colours) {
        if (c.label == colours.label) return
        colours = c
        s.prefs?.edit()?.putString("colours_label", c.label)?.apply()
        s.dial(c)
    }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scrollMode by remember { mutableStateOf(false) }
    // VIEW ONLY is remembered like FPS: watching stays watching after a restart
    var viewOnly by remember { mutableStateOf(s.prefs?.getBoolean("view_only", false) ?: false) }
    LaunchedEffect(rfb, viewOnly) { rfb.viewOnly = viewOnly; s.prefs?.edit()?.putBoolean("view_only", viewOnly)?.apply() }
    var keyboard by remember { mutableStateOf(false) }
    val mods = remember { mutableStateOf(setOf<Int>()) }
    var rx by remember { mutableLongStateOf(0L) }
    var tx by remember { mutableLongStateOf(0L) }
    var kbps by remember { mutableLongStateOf(0L) }
    val view = LocalView.current

    val rect = monitorRect(monitor, s.fbW, s.fbH)
    LaunchedEffect(rfb, monitor, s.fbW, s.fbH) {
        rfb.setRegion(rect[0], rect[1], rect[2], rect[3]); zoom = 1f; pan = Offset.Zero
    }
    LaunchedEffect(rfb, fpsIndex) {
        s.fpsMs = FPS[fpsIndex].second
        rfb.minIntervalMs = if (s.background) BACKGROUND_MS else s.fpsMs; rfb.wake()
        s.prefs?.edit()?.putString("fps_label", FPS[fpsIndex].first)?.apply()
    }
    LaunchedEffect(Unit) {
        // only VNC: the bytes this session's links received and sent, both ways (Marko, 25.10.2026)
        var last = 0L
        while (true) {
            val r = s.baseRx + (s.rfb?.rxBytes?.get() ?: 0)
            val t = s.baseTx + (s.rfb?.txBytes?.get() ?: 0)
            kbps = ((r + t) - last) * 8 / 1000; last = r + t; rx = r; tx = t
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
    // CHROME ▶: Control-Tab, Chrome's next tab, whatever was armed (Marko, 25.10.2026: "go through the tabs")
    fun nextTab() {
        mods.value = emptySet()
        rfb.key(K_CTRL, true, always = true); rfb.key(K_TAB, true, always = true)
        rfb.key(K_TAB, false, always = true); rfb.key(K_CTRL, false, always = true)
    }

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
            .pointerInput(rect.contentHashCode(), scrollMode, viewOnly) {
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
                        // Marko's gestures (25.10.2026): two fingers zoom, three fingers pan, one finger is the mouse.
                        // Nothing done with two or three fingers reaches the Mac.
                        if (pressed.size >= 3) {
                            multi = true
                            val move = pressed.fold(Offset.Zero) { acc, c -> acc + (c.position - c.previousPosition) } / pressed.size.toFloat()
                            pan = clampPan(pan + move)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        if (pressed.size == 2) {
                            multi = true
                            val a = pressed[0]; val b = pressed[1]
                            val prevDist = hypot(a.previousPosition.x - b.previousPosition.x, a.previousPosition.y - b.previousPosition.y)
                            val dist = hypot(a.position.x - b.position.x, a.position.y - b.position.y)
                            val centre = (a.position + b.position) / 2f
                            if (prevDist > 0f) {
                                val newZoom = (zoom * dist / prevDist).coerceIn(1f, 8f)
                                val k = newZoom / zoom
                                // spread or pinch around the point between the fingers: it stays where it is
                                val origin = Offset(baseX(), baseY())
                                pan = clampPan((pan - (centre - origin)) * k + (centre - origin))
                                zoom = newZoom
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val c = pressed[0]
                        val p = c.position
                        if (!moved && (p - start).getDistance() > 24f) moved = true
                        if (multi) { c.consume(); continue }
                        if (viewOnly) {
                            // watching only: one finger moves the zoomed picture, nothing goes to the Mac
                            pan = clampPan(pan + c.positionChange())
                            c.consume(); continue
                        }
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
                    if (multi || scrollMode || viewOnly) return@awaitEachGesture
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
        // read the frame counter here, not only inside the drawing: after a new dial the bitmap appears later, and
        // this is what makes the screen look again
        @Suppress("UNUSED_VARIABLE") val frame = s.tick
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
        Key("✕", Modifier.width(56.dp)) { s.hangUp() }
    }
    val meter: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("VNC %.2f MB  ↓%.2f ↑%.2f".format((rx + tx) / 1e6, rx / 1e6, tx / 1e6), Cyan, 13)
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
        Key("KEYS", modifier, active = keyboard, size = 14) { keyboard = !keyboard }
        Key("SCROLL", modifier, active = scrollMode, size = 14) { scrollMode = !scrollMode }
        Key("${FPS[fpsIndex].first} FPS", modifier, size = 14, onLongClick = { fpsMenu = true }) { fpsIndex = (fpsIndex + 1) % FPS.size }
        Key(colours.label, modifier, size = 14, onLongClick = { coloursMenu = true }) {
            setColours(Colours.ALL[(Colours.ALL.indexOfFirst { it.label == colours.label } + 1) % Colours.ALL.size])
        }
        Key("LOGIN", modifier, size = 14) { login() }
    }
    val views: @Composable (Modifier) -> Unit = { modifier ->
        Key(if (s.landscape) "▯ PORTRAIT" else "▭ LANDSCAPE", modifier, size = 14) { s.landscape = !s.landscape }
        Key("⛶ FULL SCREEN", modifier, active = true, size = 14) { s.full = true }
        Key("VIEW ONLY", modifier, active = viewOnly, size = 14) { viewOnly = !viewOnly }
        Key("CHROME ▶", modifier, size = 14) { nextTab() }
    }

    // a long press on FPS: every rate at once, one tap chooses (Marko, 25.10.2026)
    if (fpsMenu) Dialog(onDismissRequest = { fpsMenu = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("── FRAMES PER SECOND ──", AmberDim, 13)
            T("How often a new picture of the Mac is fetched. Fewer is cheaper on mobile data.", Color(0xFFF2DDB4), 13, modifier = Modifier.width(220.dp))
            FPS.forEachIndexed { i, (label, _) ->
                Key("$label FPS", Modifier.width(220.dp), active = i == fpsIndex, size = 18) { fpsIndex = i; fpsMenu = false }
            }
        }
    }

    if (coloursMenu) Dialog(onDismissRequest = { coloursMenu = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("── COLOURS ──", AmberDim, 13)
            T("How many colours the Mac sends. One full picture of the BenQ: FULL 978 kB, 65 000 524, 4096 427, 512 226, 64 177, 8 and B/W about 160.",
                Color(0xFFF2DDB4), 13, modifier = Modifier.width(220.dp))
            Colours.ALL.forEach { c ->
                val name = when (c.label) { "FULL" -> "FULL (16 M)"; "65K" -> "65 000"; "B/W" -> "BLACK & WHITE"; else -> c.label }
                Key(name, Modifier.width(220.dp), active = c.label == colours.label, size = 18) { setColours(c); coloursMenu = false }
            }
        }
    }

    when {
        s.full -> Row(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
            // the CAMERA language (MANTRA_MANIFEST language-camera.md): the picture is sacred, the keys are words in
            // the black bands beside it, their colour is their state. Left: what to press. Right: where to look (swapped at Marko's word, 25.10.2026).
            Column(Modifier.width(76.dp).fillMaxHeight()) {
                RailKey("LOGIN", null, if (s.loginPassword.isEmpty()) RailState.DEAD else RailState.OFF) { login() }
                listOf("⌘" to K_CMD, "⇧" to K_SHIFT, "⌥" to K_OPT, "⌃" to K_CTRL).forEach { (label, k) ->
                    RailKey(label, null, if (k in mods.value) RailState.ARMED else RailState.OFF) {
                        mods.value = if (k in mods.value) mods.value - k else mods.value + k
                    }
                }
                RailKey("ESC", null, RailState.OFF) { tapKey(K_ESC) }
                RailKey("TAB", null, RailState.OFF) { tapKey(K_TAB) }
                RailKey("⏎", null, RailState.OFF) { tapKey(K_RETURN) }
                RailKey("⌫", null, RailState.OFF) { tapKey(K_BACKSPACE) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                screen(Modifier.fillMaxSize())
                if (keyboard) Box(Modifier.align(Alignment.BottomCenter)) { typing() }
            }
            Column(Modifier.width(76.dp).fillMaxHeight()) {
                MONITORS.forEachIndexed { i, m ->
                    RailKey("MON ${i + 1}", m.name.substringAfter(' '), if (monitor == i) RailState.ON else RailState.OFF) { monitor = i }
                }
                RailKey("KEYS", null, if (keyboard) RailState.ON else RailState.OFF) { keyboard = !keyboard }
                RailKey("CHROME", "NEXT TAB", RailState.OFF) { nextTab() }
                RailKey("SCROLL", null, if (scrollMode) RailState.ON else RailState.OFF) { scrollMode = !scrollMode }
                RailKey("FPS", FPS[fpsIndex].first, RailState.OFF, onLongClick = { fpsMenu = true }) { fpsIndex = (fpsIndex + 1) % FPS.size }
                RailKey("COL", colours.label, RailState.OFF, onLongClick = { coloursMenu = true }) {
                    setColours(Colours.ALL[(Colours.ALL.indexOfFirst { it.label == colours.label } + 1) % Colours.ALL.size])
                }
                RailKey("VIEW", "ONLY", if (viewOnly) RailState.ON else RailState.OFF) { viewOnly = !viewOnly }
                RailKey("EXIT", "FULL", RailState.OFF) { s.full = false }
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

/** The colour of a rail key is its state, and the only sign of it (language-camera.md 2). */
private enum class RailState(val color: Color) {
    ON(Color(0xFF33D17A)),      // green: on, the one in use
    OFF(Color(0xFF7A8087)),     // grey: off, ready
    ARMED(Color(0xFFE8A33D)),   // amber: armed, waiting for the next key (a held ⌘)
    DEAD(Color(0xFF33383E)),    // dark: cannot be done now (LOGIN with no password in the settings)
}

/**
 * A rail key as in Mantra Manual Camera: the word and nothing else, no box, no border; under it, when there is one, a
 * whisper with the current value. Keys share their rail with equal weight; a long word shrinks, it is never clipped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.RailKey(word: String, whisper: String?, state: RailState, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); (onLongClick ?: { helpFor.value = word })() },
            ) { if (state != RailState.DEAD) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val size = if (word.length <= 5) 13 else (13 * 5 / word.length).coerceAtLeast(6)
        androidx.compose.material3.Text(
            word, color = state.color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp,
            letterSpacing = 0.04.em, maxLines = 1, softWrap = false,
        )
        if (whisper != null) androidx.compose.material3.Text(
            whisper, color = state.color.copy(alpha = 150 / 255f), fontFamily = Mono, fontSize = 9.sp,
            maxLines = 1, softWrap = false,
        )
    }
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
