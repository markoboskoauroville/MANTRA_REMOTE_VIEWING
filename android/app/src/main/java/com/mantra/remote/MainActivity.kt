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
import androidx.compose.foundation.layout.RowScope
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
        l.startsWith("MON 1") -> "Shows monitor 1, the BenQ. Only this monitor's picture travels to the phone, so it costs less data than both. A quick swipe left or right on the picture switches too."
        l.startsWith("MON 2") -> "Shows monitor 2, the MacBook Pro's own screen. Only this monitor's picture travels to the phone."
        l == "✕" -> "Closes the link to the Mac and goes back to the settings page."
        l.contains("KEYS") || l == "⌨" -> "Opens the phone's keyboard. What you type is typed on the Mac."
        l == "SCROLL" -> "While it is lit, one finger dragging up or down turns the Mac's mouse wheel instead of moving the pointer."
        l.contains("FPS") -> "Frames per second: how often a new picture of the Mac is fetched. Fewer is cheaper on mobile data. A tap steps to the next; a long press opens the list."
        l.removePrefix("C").trim() in setOf("FULL", "65K", "4096", "512", "64", "8", "B/W") || l == "COL" -> "Colours: how many the Mac sends. Fewer colours cost far less mobile data: 8 colours or black and white about a sixth of full colour. A tap steps to the next; a long press opens the list."
        l.startsWith("NEXT") || l == "TAB ▶" -> "The next tab of the app in front: Chrome, iTerm, Safari, Finder (⌘⇧] on the Mac, the same shortcut in all of them). Tap again and again to go through the tabs; after the last one it starts at the first."
        l == "DOCK" -> "The Dock's apps on the whole phone, the Dock Switcher's own list. Tap one: the Mac switches to it, or starts it. A dot: open; lit: in front. ⌃⌃ MAC opens the square on the Mac itself; ✕ or Back closes."
        l == "LOGIN" -> "Types your Mac password from the settings, then Return: for any password box that pops up on the Mac."
        l.contains("LANDSCAPE") -> "Turns the app sideways: the Mac on the left, the keys on the right."
        l.contains("PORTRAIT") -> "Turns the app upright again: the Mac in the middle, the keys below."
        l == "⚙" -> "Settings: the meter of the link with its meaning, the meter on the screen on or off, and ✕ QUIT, which closes the link, switches Tailscale off and quits the app."
        l == "☰" -> "MENU: the keys below become the Mac's menu bar keys. The menu bar of the app in front opens (even when it hides itself); ← → go from menu to menu, ↑ ↓ through the items, ⏎ CHOOSE picks, ☰ OPEN opens it again, ✕ EXIT closes the menus and brings your keys back."
        l == "⇄" -> "Switches the picture between the two screens: the BenQ and the MacBook Pro. The small word under it is the screen you are watching now."
        l.contains("FULL") || l == "⛶" -> "Full screen: the phone turns sideways and the Mac fills it, with the keys as words at the sides. EXIT brings you back."
        l == "EXIT" -> "Leaves full screen."
        l.startsWith("VIEW") -> "View only: the mouse does not reach the Mac: no click, no drag, no scroll. You can watch, zoom and move around the picture; one finger moves the zoomed picture. The keys still work: ESC, DOCK, TAB ▶, typing, every key. Tap again to use the mouse on the Mac."
        l == "⌘" -> "Command, held down on the Mac while it is lit. Tap it on, then TAB, TAB, TAB walks the app switcher; tap it off to choose. ⌘ on, then Q, then ⌘ off quits an app."
        l == "⌥" -> "Option. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⌃" || l == "^" -> "Control. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⇧" -> "Shift. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "ESC" -> "Escape: closes a menu or a dialog, cancels."
        l == "TAB" -> "Tab: the next field. With ⌘ first, it switches between apps."
        l == "⏎" -> "Return: confirms, sends, a new line."
        l == "⌫" -> "Delete: removes the letter before the cursor."
        l == "←" || l == "→" || l == "↑" || l == "↓" -> "The arrow key, as on the Mac's keyboard."
        l == "CONNECT" -> "Switches Tailscale on, finds the Mac by itself (home Wi-Fi or Tailscale, the first that answers) and logs in with the user and password above. The app does this by itself when it opens."
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

/** The app closed (Back, or swiped away from the recent apps): the link and Tailscale go off. */
internal fun closeApp() { Hold.session?.hangUp() }

private const val MAX_REDIALS = 5

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
        // nothing to press: Tailscale on, the Mac found, dialled and logged in (Marko, 26.10.2026)
        if (!session.wanted && !prefs.getString("password", "").isNullOrEmpty()) session.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Back out of the app closes it: the link and Tailscale go off with it
        if (isFinishing) closeApp()
    }

    override fun onStart() {
        super.onStart()
        val s = Hold.session ?: return
        s.background = false
        s.rfb?.let { it.minIntervalMs = s.fpsMs; it.wake() }
        // the link fell while away (the network changed, the Mac slept): dial again by itself, no CONNECT needed
        if (s.wanted && s.phase == Phase.SETUP) s.start()
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
    /** The key on its way to the Mac, shown small above the keys until it lands; "" when nothing is travelling. */
    var pressed by mutableStateOf("")
    var pressedAt by mutableLongStateOf(0L)
    /** The Mac's address of this link. */
    @Volatile var host = ""
    /**
     * Where the Dock Switcher's listener (MacSister) answers: Tailscale first, macOS holds back the home Wi-Fi from it
     * unless Local Network is allowed for it (found 26.10.2026); at home Tailscale goes straight over the Wi-Fi anyway.
     */
    @Volatile var sisterHost = ""
    /** Keys the Mac has seen (MacSister), and that count when the key now travelling left; -1: not known yet. */
    @Volatile var keysSeen = -1L
    @Volatile var pressedBase = -1L
    /** The sister answers: the landing line is hers; otherwise the 1x1 picture (Rfb.probe) stands in. */
    @Volatile var sisterOk = false
    private var watching = 0
    /** The phone's dock (DOCK): open, and its apps with their icons, kept between openings. */
    var dockOpen by mutableStateOf(false)
    var dockApps by mutableStateOf<List<MacSister.DockApp>>(emptyList())
    var dockNote by mutableStateOf("")
    val icons = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    /**
     * Asks the Mac, again and again, how many keys it has seen; each answer comes the moment the count passes the last
     * one, so a key from the phone is known landed when the Mac itself saw it (Marko, 26.10.2026).
     */
    private fun watchKeys() {
        val me = ++watching
        Thread({
            while (me == watching && wanted) {
                if (sisterHost.isEmpty()) sisterHost = listOf(MacFinder.MAC_TAILSCALE_IP, host).firstOrNull { it.isNotEmpty() && MacSister.keys(it, -1) != null } ?: ""
                val n = if (sisterHost.isEmpty()) null else MacSister.keys(sisterHost, keysSeen)
                if (n == null) { sisterOk = false; sisterHost = ""; Thread.sleep(2000); continue }
                sisterOk = true
                keysSeen = n
                if (pressed.isNotEmpty() && pressedBase >= 0 && n > pressedBase) main.post { landed() }
            }
        }, "mac-keys").start()
    }

    fun landed() {
        if (pressed.isEmpty()) return
        android.util.Log.i("MantraRemote", "landed: $pressed in ${System.currentTimeMillis() - pressedAt} ms (${if (sisterOk) "the Mac saw it" else "picture probe"})")
        pressed = ""
    }
    /** A link is wanted (CONNECT was pressed, ✕ not): a link that falls is dialled again on return. */
    var wanted = false

    /** Each start counts; a search still running for an older start gives up. */
    @Volatile private var attempt = 0
    /** Falls dialled again in a row; after [MAX_REDIALS] the app stops and shows why. */
    private var redials = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Tailscale on, the Mac found (home Wi-Fi or Tailscale, whichever answers first), dialled and logged in, with nothing
     * typed (Marko, 26.10.2026: "all connections must be done automatically without me searching for addresses").
     */
    fun start() {
        val c = app ?: return
        val p = prefs ?: return
        loginPassword = p.getString("login", "") ?: ""
        wanted = true; phase = Phase.CONNECTING; status = "> TAILSCALE ON"; statusBad = false
        rfb?.close(); rfb = null; baseRx = 0; baseTx = 0
        LinkService.start(c)
        val gen = ++attempt
        Thread({
            Tailscale.onAndWait(c)
            val port = p.getString("port", "5900")?.toIntOrNull() ?: 5900
            val host = MacFinder.find(c, port, listOf(p.getString("host", "") ?: "", p.getString("lan_host", "") ?: "")) {
                if (gen == attempt) status = it
            }
            if (gen != attempt || !wanted) return@Thread
            if (host == null) {
                phase = Phase.SETUP; statusBad = true
                status = "THE MAC DOES NOT ANSWER. IS IT AWAKE, IS SCREEN SHARING ON? CONNECT TRIES AGAIN."
                return@Thread
            }
            // the home address is remembered: next time at home it is tried first among the others
            android.util.Log.i("MantraRemote", "the Mac answered at $host (${if (MacFinder.isLocal(host)) "home Wi-Fi" else "Tailscale"})")
            if (MacFinder.isLocal(host)) p.edit().putString("lan_host", host).apply()
            this.host = host
            status = "> DIAL $host:$port"
            val password = p.getString("password", "") ?: ""
            val user = p.getString("user", "markobosko") ?: "markobosko"
            dial = { colours ->
                rfb?.let { old -> old.close(); baseRx += old.rxBytes.get(); baseTx += old.txBytes.get() }
                val r = Rfb(this, colours); rfb = r
                r.connect(host, port, password, user, loginPassword)
            }
            main.post { if (gen == attempt && wanted) dial(coloursSaved(p)) }
        }, "find-mac").start()
    }

    /** ✕ or the app closed: the link is closed on purpose, nothing dials again, the service ends and Tailscale goes off. */
    fun hangUp() {
        wanted = false; attempt++; rfb?.close()
        app?.let { LinkService.stop(it); Tailscale.off(it) }
        phase = Phase.SETUP; status = "LINK CLOSED"; statusBad = false
    }

    override fun onConnected(name: String, width: Int, height: Int) {
        android.util.Log.i("MantraRemote", "framebuffer ${width}x$height name '$name'")
        if (name.isNotEmpty()) this.name = name
        fbW = width; fbH = height
        phase = Phase.LIVE; status = "LINK ESTABLISHED"; statusBad = false; redials = 0
        if (background) rfb?.minIntervalMs = BACKGROUND_MS
        app?.let(LinkService::start)
        watchKeys()
    }
    override fun onFrame() { tick++ }
    // the picture probe speaks only while the sister on the Mac does not answer
    override fun onLanded() { if (!sisterOk) main.post { landed() } }
    override fun onClosed(reason: String) {
        phase = Phase.SETUP; status = reason.uppercase(); statusBad = true
        // in front, a fall is dialled again by itself (Tailscale coming up changes the network under the link);
        // behind, on return (onStart)
        if (wanted && !background && redials < MAX_REDIALS) { redials++; main.postDelayed({ if (wanted && phase == Phase.SETUP) start() }, 2000) }
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
private fun T(text: String, color: Color = Amber, size: Int = 16, bold: Boolean = false, modifier: Modifier = Modifier, oneLine: Boolean = false) =
    androidx.compose.material3.Text(
        text, modifier = modifier, color = color, fontFamily = Mono, fontSize = size.sp,
        maxLines = if (oneLine) 1 else Int.MAX_VALUE, softWrap = !oneLine,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = (size * 1.4f).sp,
    )

/** A finger-sized key: amber box line, label inside, lit while active. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(label: String, modifier: Modifier = Modifier, active: Boolean = false, size: Int = 16, sub: String? = null, padH: Int = 10, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        modifier
            .height(52.dp)
            .background(if (active) Amber else BgRaised)
            .border(1.dp, if (active) AmberGlow else Rule)
            .combinedClickable(
                onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); (onLongClick ?: { helpFor.value = label })() },
            ) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .padding(horizontal = padH.dp),
        contentAlignment = Alignment.Center,
    ) {
        // a key's word never breaks (Marko, 25.10.2026); a modifier carries its name under the symbol, small
        if (sub == null) T(label, if (active) Bg else Amber, size, bold = true, oneLine = true)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(label, if (active) Bg else Amber, size, bold = true, oneLine = true)
            T(sub, if (active) Bg else AmberDim, 9, oneLine = true)
        }
    }
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
        T("v" + BuildConfig.VERSION_NAME, AmberDim, 13)          // the version the person sees (versioning.md §3)
        T("── THE MAC ───────────────────", Rule, 16)
        Field("HOST (FOUND BY ITSELF; ONLY IF IT IS NOT)", host) { host = it.trim() }
        Field("PORT", port, number = true) { port = it.filter(Char::isDigit) }
        Field("VNC PASSWORD (SCREEN SHARING)", password, secret = true) { password = it }
        Field("MAC USER (SHORT NAME)", user) { user = it.trim() }
        Field("MAC LOGIN PASSWORD (AUTO-LOGIN, AND THE LOGIN BUTTON)", login, secret = true) { login = it }
        Spacer(Modifier.height(12.dp))
        Key(if (s.phase == Phase.CONNECTING) "DIALLING …" else "CONNECT", Modifier.fillMaxWidth(), size = 22) {
            if (s.phase == Phase.CONNECTING) return@Key
            prefs.edit().putString("host", host).putString("port", port).putString("password", password).putString("login", login).putString("user", user).apply()
            s.start()
        }
        Spacer(Modifier.height(12.dp))
        T("> " + s.status, if (s.statusBad) RedAlert else GreenOk, 16)
        Spacer(Modifier.height(16.dp))
        T("Nothing to type: the app switches Tailscale on, finds the Mac on the home Wi-Fi or through Tailscale, " +
            "whichever answers first, and logs in. Closing the app switches Tailscale off.", AmberDim, 13)
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
// symbol, keysym, the name written under it; in the MacBook Pro's order (K_* below are compile-time constants)
private val MOD_KEYS by lazy { listOf(Triple("⌃", K_CTRL, "control"), Triple("⌥", K_OPT, "option"), Triple("⌘", K_CMD, "command"), Triple("⇧", K_SHIFT, "shift")) }
private const val K_OPT = 0xffe9
private const val K_CTRL = 0xffe3
private const val K_SHIFT = 0xffe1
private const val K_BACKSPACE = 0xff08
private const val K_TAB = 0xff09
private const val K_BRACERIGHT = 0x7d
private const val K_RETURN = 0xff0d
private const val K_ESC = 0xff1b
private const val K_LEFT = 0xff51
private const val K_UP = 0xff52
private const val K_RIGHT = 0xff53
private const val K_DOWN = 0xff54
private val KEY_NAMES by lazy { mapOf(K_ESC to "ESC", K_TAB to "TAB", K_RETURN to "⏎", K_BACKSPACE to "⌫", K_LEFT to "←", K_UP to "↑", K_RIGHT to "→", K_DOWN to "↓") }

@Composable
private fun Live(s: Session) {
    val rfb = s.rfb ?: return
    // the monitor, the zoom and where the picture stood come back after a restart (Marko, 26.10.2026)
    var monitor by remember { mutableIntStateOf(s.prefs?.getInt("monitor", 0) ?: 0) }
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
    var cmdHeld by remember { mutableStateOf(false) }
    var rx by remember { mutableLongStateOf(0L) }
    var tx by remember { mutableLongStateOf(0L) }
    var kbps by remember { mutableLongStateOf(0L) }
    val view = LocalView.current

    val rect = monitorRect(monitor, s.fbW, s.fbH)
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(rfb, monitor, s.fbW, s.fbH) {
        rfb.setRegion(rect[0], rect[1], rect[2], rect[3])
        val p = s.prefs
        if (!restored && p != null && p.getInt("monitor", -1) == monitor) {
            zoom = p.getFloat("zoom", 1f); pan = Offset(p.getFloat("pan_x", 0f), p.getFloat("pan_y", 0f))
        } else { zoom = 1f; pan = Offset.Zero }
        restored = true
        p?.edit()?.putInt("monitor", monitor)?.apply()
    }
    LaunchedEffect(zoom, pan) {
        if (!restored) return@LaunchedEffect
        delay(400)                                               // saved once the fingers rest
        s.prefs?.edit()?.putFloat("zoom", zoom)?.putFloat("pan_x", pan.x)?.putFloat("pan_y", pan.y)?.apply()
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
    // what is on its way, shown above the keys until the Mac has it (Marko, 26.10.2026)
    fun travelling(label: String) { s.pressed = label; s.pressedAt = System.currentTimeMillis(); s.pressedBase = s.keysSeen }
    fun tapKey(k: Int) {
        travelling((mods.value.mapNotNull { m -> MOD_KEYS.firstOrNull { it.second == m }?.first } + KEY_NAMES.getOrElse(k) { "KEY" }).joinToString(" "))
        withMods { rfb.key(k, true); rfb.key(k, false) }
        rfb.probe()
    }
    // ⌘ is a toggle held down on the Mac: on, it stays pressed there until tapped off, so ⌘ then TAB, TAB, TAB walks the
    // app switcher and ⌘ off chooses (Marko, 25.10.2026: "make my Command key a toggle"). ⌥ ⌃ ⇧ stay one-shot.
    fun toggleMod(k: Int) {
        if (k == K_CMD) { cmdHeld = !cmdHeld; travelling(if (cmdHeld) "⌘ ON" else "⌘ OFF"); rfb.key(K_CMD, cmdHeld); rfb.probe() }
        else {
            // ⌃ is also pressed and let go on the Mac at once, so two taps are the Mac's double ⌃ (the Dock Switcher's
            // square, Marko 25.10.2026: "I double tap, but it does not register"); it still goes with the next key too
            if (k == K_CTRL && !cmdHeld) { travelling("⌃"); rfb.key(K_CTRL, true); rfb.key(K_CTRL, false); rfb.probe() }
            mods.value = if (k in mods.value) mods.value - k else mods.value + k
        }
    }
    fun modOn(k: Int) = if (k == K_CMD) cmdHeld else k in mods.value
    fun releaseCmd() { if (cmdHeld) { cmdHeld = false; rfb.key(K_CMD, false) } }
    // NEXT TAB ▶: ⌘⇧], the next tab in Chrome and in iTerm alike, so the phone need not know which app is in front
    // (Marko, 25.10.2026: "go through the tabs", then "the same shortcut doesn't work in iTerm"). Ctrl-Tab was Chrome's only.
    fun nextTab() {
        mods.value = emptySet(); cmdHeld = false
        travelling("TAB ▶")
        listOf(K_CMD, K_SHIFT, K_BRACERIGHT).forEach { rfb.key(it, true) }
        listOf(K_BRACERIGHT, K_SHIFT, K_CMD).forEach { rfb.key(it, false) }
        rfb.probe()
    }

    // DOCK: ⌃ tapped twice, the Dock Switcher's square (Marko, 26.10.2026: "an action button to open the dock so I can
    // launch my applications remotely from the phone"). Not ⌃`: on his US International layout ` is a dead key and
    // Screen Sharing never delivers it (tried 26.10.2026). The pause makes the two taps two, as his finger would.
    // the old way, ⌃⌃ on the Mac: still there when the Dock Switcher's listener does not answer
    fun dockOnMac() {
        mods.value = emptySet(); releaseCmd()
        travelling("DOCK")
        Thread {
            rfb.key(K_CTRL, true); rfb.key(K_CTRL, false)
            Thread.sleep(150)
            rfb.key(K_CTRL, true); rfb.key(K_CTRL, false)
            rfb.probe()
        }.start()
    }

    // the tiny line above the keys: what is travelling; gone when it landed, red when it has not after 3 s
    var late by remember { mutableStateOf(false) }
    LaunchedEffect(s.pressedAt) { late = false; delay(3000); late = s.pressed.isNotEmpty() }
    val travelLine: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.height(16.dp), contentAlignment = Alignment.Center) {
            if (s.pressed.isNotEmpty()) T(if (late) "${s.pressed}  NOT LANDED YET" else "${s.pressed}  →  MAC", if (late) RedAlert else AmberGlow, 11, oneLine = true)
        }
    }

    // DOCK: the Dock's apps on the whole phone, picked here, switched or started on the Mac (Marko, 26.10.2026: "build a
    // remote controller for my dock, so I'm not waiting for the dock and trying to find it on screen")
    fun dock() { s.dockOpen = true; s.dockNote = "" }

    // ☰ MENU: the action keys become the menu bar's (Marko, 26.10.2026: "one button, which will switch all of these
    // action buttons into an action button for the menu bar, and when I exit, my normal action buttons are returning").
    // The Mac opens the menu by Accessibility; the arrows walk it; EXIT closes the menus the same way, never by ESC.
    var menuMode by remember { mutableStateOf(false) }
    fun sisterHost() = s.sisterHost.ifEmpty { MacFinder.MAC_TAILSCALE_IP }
    fun openMenu() {
        travelling("MENU BAR")
        Thread { if (MacSister.menu(sisterHost())) s.landed() else s.pressed = "MENU: THE MAC DID NOT ANSWER" }.start()
    }
    fun menuOn() { menuMode = true; mods.value = emptySet(); releaseCmd(); openMenu() }
    fun menuOff() {
        menuMode = false
        travelling("MENU CLOSED")
        Thread { if (MacSister.menuClose(sisterHost())) s.landed() else s.pressed = "" }.start()
    }
    val menuPanel: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("ESC", Modifier.weight(1f), size = 18) { tapKey(K_ESC) }
                Key("↑", Modifier.weight(1f), size = 26) { tapKey(K_UP) }
                Key("☰ OPEN", Modifier.weight(1f), size = 16) { openMenu() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("←", Modifier.weight(1f), size = 26) { tapKey(K_LEFT) }
                Key("↓", Modifier.weight(1f), size = 26) { tapKey(K_DOWN) }
                Key("→", Modifier.weight(1f), size = 26) { tapKey(K_RIGHT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("⏎ CHOOSE", Modifier.weight(2f), size = 16) { tapKey(K_RETURN) }
                Key("✕ EXIT", Modifier.weight(1f), active = true, size = 16) { menuOff() }
            }
        }
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
                        last = p
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
                        c.consume()
                    }
                    // a quick sideways swipe of one finger, the picture not zoomed: the other monitor, as they stand on
                    // the desk: swipe left for the one on the right (MON 2), right for the one on the left (Marko, 25.10.2026)
                    val dx = last.x - start.x; val dy = last.y - start.y
                    val quick = System.currentTimeMillis() - t0 < 400
                    if (!multi && !dragging && quick && zoom <= 1.01f && abs(dx) > 150f && abs(dx) > 2 * abs(dy)) {
                        val next = (monitor + if (dx < 0) 1 else -1).coerceIn(0, MONITORS.size - 1)
                        if (next != monitor) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); monitor = next }
                        return@awaitEachGesture
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
    // the meter's numbers always take the same width, so no digit runs left and right as they change (Marko, 26.10.2026)
    val meterTexts = { listOf(
        "VNC %6.2f MB ↓%6.2f ↑%5.2f".format((rx + tx) / 1e6, rx / 1e6, tx / 1e6),
        "%6d kbit/s".format(kbps),
        "%5d ms".format(s.lastMs.coerceIn(0, 99999)),
    ) }
    val meter: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            meterTexts().forEach { T(it, Cyan, 13, oneLine = true) }
        }
    }
    // the meter on the screen, or only in ⚙ (Marko, 26.10.2026: "I can disable this speed status line")
    var showMeter by remember { mutableStateOf(s.prefs?.getBoolean("meter_on", true) ?: true) }
    var settingsOpen by remember { mutableStateOf(false) }
    val modKeys: @Composable () -> Unit = {
        // as on a MacBook Pro's bottom row: control, option, command; shift after (Marko, 25.10.2026)
        MOD_KEYS.forEach { (label, k, name) ->
            Key(label, Modifier.width(56.dp), active = modOn(k), size = 20, sub = name) { toggleMod(k) }
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
            // ⏎ in a narrow frame, so the whole first row stands inside the phone's 413 dp, ⏎ included (Marko, 26.10.2026)
            specialKeys.forEach { (label, k) -> Key(label, Modifier.width(if (k == K_RETURN) 36.dp else 60.dp), size = 18, padH = if (k == K_RETURN) 2 else 10) { tapKey(k) } }
        }
    }
    fun typeChars(text: String) = text.forEach { ch ->
        when (ch) {
            '\n' -> withMods { rfb.key(K_RETURN, true); rfb.key(K_RETURN, false) }
            else -> withMods {
                val ks = if (ch.code < 256) ch.code else 0x01000000 + ch.code
                val shift = ch.isUpperCase() || ch in "~!@#$%^&*()_+{}|:\"<>?"
                if (shift) rfb.key(K_SHIFT, true)
                rfb.key(ks, true); rfb.key(ks, false)
                if (shift) rfb.key(K_SHIFT, false)
            }
        }
    }
    fun typeText(text: String) { typeChars(text); if (text.isNotEmpty()) { travelling(if (text.length == 1) text.uppercase() else "TEXT"); rfb.probe() } }
    // LOGIN: the Mac password from the settings, typed into whatever asks for it (lock screen, a dialog), then Return
    fun login() {
        if (s.loginPassword.isEmpty()) return
        mods.value = emptySet(); releaseCmd()
        typeChars(s.loginPassword); tapKey(K_RETURN); travelling("LOGIN")
    }
    val typing: @Composable () -> Unit = {
        if (keyboard) TypingField(onText = { typeText(it) }, onBackspace = { tapKey(K_BACKSPACE) })
    }
    val toggles: @Composable RowScope.(Modifier) -> Unit = { modifier ->
        // FPS first, the keyboard where FPS was, drawn as a keyboard (Marko, 26.10.2026)
        Key("${FPS[fpsIndex].first} FPS", modifier, size = 14, onLongClick = { fpsMenu = true }) { fpsIndex = (fpsIndex + 1) % FPS.size }
        Key("SCROLL", modifier, active = scrollMode, size = 14) { scrollMode = !scrollMode }
        Key("⌨", modifier, active = keyboard, size = 26) { keyboard = !keyboard }
        Key("C" + colours.label.let { if (it[0].isDigit()) it else " $it" }, Modifier.weight(0.75f), size = 14, padH = 4, onLongClick = { coloursMenu = true }) {
            setColours(Colours.ALL[(Colours.ALL.indexOfFirst { it.label == colours.label } + 1) % Colours.ALL.size])
        }
        Key("LOGIN", modifier, size = 14) { login() }
        Key("⚙", Modifier.weight(0.6f), size = 22) { settingsOpen = true }
    }
    val views: @Composable RowScope.() -> Unit = {
        // LANDSCAPE left the row (Marko, 25.10.2026: "more space for the letters"); FULL SCREEN is the sideways view.
        // ⛶ alone, without its word, and ⇄ beside TAB ▶ switches BenQ / MacBook (Marko, 26.10.2026); widths by their words
        Key("⛶", Modifier.weight(0.6f), active = true, size = 22) { s.full = true }
        Key("VIEW", Modifier.weight(1f), active = viewOnly, size = 14) { viewOnly = !viewOnly }   // lit = view only (Marko, 26.10.2026)
        Key("TAB ▶", Modifier.weight(1.1f), size = 14) { nextTab() }
        Key("⇄", Modifier.weight(0.7f), size = 22, sub = if (monitor == 0) "BENQ" else "MACBOOK", padH = 2) { monitor = 1 - monitor }
        Key("DOCK", Modifier.weight(1f), size = 14) { dock() }
        Key("☰", Modifier.weight(0.6f), size = 22) { menuOn() }
    }

    // ⚙: the meter, read with its words; the meter on the screen or not; ✕ quits (Marko, 26.10.2026: "quitting the app
    // is two steps: Settings and then X")
    if (settingsOpen) Dialog(onDismissRequest = { settingsOpen = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).padding(14.dp).width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("── SETTINGS ──", AmberDim, 13)
            val (vnc, speed, ms) = meterTexts()
            T(vnc.trim(), Cyan, 14, oneLine = true)
            T("The picture and the keys of this link: all of it, ↓ from the Mac (the pictures), ↑ to the Mac (keys, clicks).", Color(0xFFF2DDB4), 12)
            T(speed.trim(), Cyan, 14, oneLine = true)
            T("How fast it flows now, every second.", Color(0xFFF2DDB4), 12)
            T(ms.trim(), Cyan, 14, oneLine = true)
            T("How long the last picture took to come after it was asked for.", Color(0xFFF2DDB4), 12)
            Key(if (showMeter) "METER ON THE SCREEN: ON" else "METER ON THE SCREEN: OFF", Modifier.fillMaxWidth(), active = showMeter, size = 14) {
                showMeter = !showMeter; s.prefs?.edit()?.putBoolean("meter_on", showMeter)?.apply()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("BACK", Modifier.weight(1f), size = 14) { settingsOpen = false }
                Key("✕ QUIT", Modifier.weight(1f), size = 14) { settingsOpen = false; s.hangUp(); activity.finish() }
            }
        }
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
                MOD_KEYS.forEach { (label, k, name) ->
                    RailKey(label, name, if (k == K_CMD && cmdHeld) RailState.ON else if (modOn(k)) RailState.ARMED else RailState.OFF) { toggleMod(k) }
                }
                RailKey("ESC", null, RailState.OFF) { tapKey(K_ESC) }
                RailKey("TAB", null, RailState.OFF) { tapKey(K_TAB) }
                RailKey("⏎", null, RailState.OFF) { tapKey(K_RETURN) }
                RailKey("⌫", null, RailState.OFF) { tapKey(K_BACKSPACE) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                screen(Modifier.fillMaxSize())
                travelLine(Modifier.align(Alignment.TopCenter).background(Color(0x99000000)).padding(horizontal = 8.dp))
                if (keyboard) Box(Modifier.align(Alignment.BottomCenter)) { typing() }
            }
            Column(Modifier.width(76.dp).fillMaxHeight()) {
                MONITORS.forEachIndexed { i, m ->
                    RailKey("MON ${i + 1}", m.name.substringAfter(' '), if (monitor == i) RailState.ON else RailState.OFF) { monitor = i }
                }
                RailKey("KEYS", null, if (keyboard) RailState.ON else RailState.OFF) { keyboard = !keyboard }
                RailKey("NEXT", "TAB", RailState.OFF) { nextTab() }
                RailKey("DOCK", null, RailState.OFF) { dock() }
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { views() }
                typing()
            }
        }
        else -> Column(Modifier.fillMaxSize().imePadding()) {
            // MON 1 / MON 2 / ✕ left the top (Marko, 26.10.2026: "everything is down anyway"): ⇄ switches the screens,
            // ⚙ holds the meter and ✕
            if (showMeter) meter()
            screen(Modifier.weight(1f).fillMaxWidth())
            travelLine(Modifier.fillMaxWidth())
            if (menuMode) menuPanel() else {
                keysRow()
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { toggles(Modifier.weight(1f)) }
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { views() }
            }
            typing()
        }
    }
    if (s.dockOpen) DockScreen(s, onMac = { s.dockOpen = false; dockOnMac() }) { app ->
        travelling(app.name)
        s.dockOpen = false
        // no key travels: the line clears when the Mac answers that it switched or started the app
        Thread { if (MacSister.open(s.sisterHost.ifEmpty { MacFinder.MAC_TAILSCALE_IP }, app.id)) s.landed() else s.pressed = "${app.name}: THE MAC DID NOT ANSWER" }.start()
    }
}

/**
 * The phone's dock: the apps of the Dock Switcher's square, as large as the phone allows, in the square's order. A tap
 * switches to the app on the Mac, or starts it; the open ones carry a dot, the one in front is lit (Marko, 26.10.2026:
 * "It should fill up my screen real estate on my phone with the same functions. Then I pick switching or starting").
 */
@Composable
private fun DockScreen(s: Session, onMac: () -> Unit, onPick: (MacSister.DockApp) -> Unit) {
    androidx.activity.compose.BackHandler { s.dockOpen = false }
    LaunchedEffect(Unit) {
        val host = s.sisterHost.ifEmpty { MacFinder.MAC_TAILSCALE_IP }
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MacSister.apps(host) }
        if (list == null) { s.dockNote = "THE DOCK SWITCHER ON THE MAC DOES NOT ANSWER (PORT ${MacSister.PORT})"; return@LaunchedEffect }
        s.dockApps = list
        list.filter { it.id !in s.icons }.forEach { a ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MacSister.icon(host, a.id) }?.let { s.icons[a.id] = it }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("── DOCK ──", AmberDim, 14, modifier = Modifier.weight(1f))
            Key("⌃⌃ MAC", Modifier.width(96.dp), size = 13) { onMac() }
            Spacer(Modifier.width(6.dp))
            Key("✕", Modifier.width(56.dp)) { s.dockOpen = false }
        }
        if (s.dockNote.isNotEmpty()) T(s.dockNote, RedAlert, 13)
        val apps = s.dockApps
        if (apps.isEmpty()) { if (s.dockNote.isEmpty()) T("> ASKING THE MAC …", GreenOk, 14); return@Column }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp)) {
            // as many columns as make the cells closest to square on this screen
            val n = apps.size
            val cols = kotlin.math.ceil(kotlin.math.sqrt(n * maxWidth.value / maxHeight.value)).toInt().coerceIn(1, n)
            val rows = (n + cols - 1) / cols
            val cellW = maxWidth / cols; val cellH = maxHeight / rows
            val iconSize = minOf(cellW, cellH - 22.dp) * 0.78f
            Column {
                for (r in 0 until rows) Row {
                    for (c in 0 until cols) {
                        val a = apps.getOrNull(r * cols + c)
                        Box(Modifier.width(cellW).height(cellH), contentAlignment = Alignment.Center) {
                            if (a != null) Column(
                                Modifier.fillMaxSize().padding(3.dp)
                                    .background(if (a.front) BgRaised else Bg)
                                    .border(1.dp, if (a.front) AmberGlow else Rule)
                                    .clickable { onPick(a) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val icon = s.icons[a.id]
                                if (icon != null) androidx.compose.foundation.Image(icon, a.name, Modifier.size(iconSize))
                                else Box(Modifier.size(iconSize))
                                T(a.name, if (a.running) Amber else AmberDim, 11, oneLine = true)
                                T(if (a.running) "●" else " ", Amber, 9)
                            }
                        }
                    }
                }
            }
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
