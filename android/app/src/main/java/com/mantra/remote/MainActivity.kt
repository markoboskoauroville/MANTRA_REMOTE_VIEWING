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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.heightIn
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
// v41 (9.11.2026): the layout is derived from the framebuffer, not hard-coded: the BenQ 2560x1440 at 0,0, the Retina
// 3024x1964 to its right; fbH 1964 = tops level (2560,0), fbH 2361 = Retina 397 lower, any taller picture: the Retina
// stands at the bottom (y = fbH - 1964). Set by layoutMonitors() whenever the picture's size is known.
@Volatile private var MONITORS = monitorsFor(5584, 1964)
private fun monitorsFor(fbW: Int, fbH: Int): List<Monitor> = listOf(
    Monitor("1 BENQ", 0, 0, 2560, 1440),
    Monitor("2 MACBOOK", 2560, if (fbH > 1964) fbH - 1964 else 0, 3024, 1964),
)
private fun layoutMonitors(fbW: Int, fbH: Int) { if (fbW > 0 && fbH > 0) MONITORS = monitorsFor(fbW, fbH) }

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
        l == "🖱" || l == "MOUSE" -> "MOUSE: while it is lit, one finger moves the Mac's pointer to where it touches, without clicking. Three fingers tapped together click, two fingers tapped together right-click, where the pointer stands. Spreading, pinching and three-finger panning still work."
        l == "L" -> "The left mouse button: a short press clicks where the pointer stands; a long press lights it (TAP=L): then every tap on the screen left-clicks at once where the finger touches (a finger moved on is a drag). Lighting R puts L out; another long press switches it off."
        l == "R" -> "The right mouse button: a short press right-clicks where the pointer stands; a long press lights it (TAP=R): then every tap on the screen right-clicks at once where the finger touches. Lighting L puts R out; another long press switches it off."
        l == "D" -> "DRAG: the first press holds the Mac's left button down where the pointer stands; moving the pointer then drags; the second press lets the button go (lit green while held)."
        l == "SCROLL" -> "While it is lit, put a finger on a window and drag it up or down: the pointer goes there first, so that window turns, as with a mouse wheel."
        l.contains("FPS") -> "Frames per second: how often a new picture of the Mac is fetched. Fewer is cheaper on mobile data. A tap steps to the next; a long press opens the list."
        l.removePrefix("C").trim() in setOf("FULL", "65K", "4096", "512", "64", "8", "B/W") || l == "COL" -> "Colours: how many the Mac sends. Fewer colours cost far less mobile data: 8 colours or black and white about a sixth of full colour. A tap steps to the next; a long press opens the list."
        l.startsWith("NEXT") || l == "⇥" -> "The next tab of the app in front: Chrome, iTerm, Safari, Finder (⌘⇧] on the Mac, the same shortcut in all of them). Tap again and again to go through the tabs; after the last one it starts at the first."
        l == "DOCK" -> "A tap shows the Mac's Dock, moves the picture down to it at the zoom you have, and puts the pointer on it; the next tap hides it again and gives the view and the pointer back. A long press: the Dock's apps on the whole phone, the Dock Switcher's own list. Tap one: the Mac switches to it, or starts it. A dot: open; lit: in front. ⌃⌃ MAC opens the square on the Mac itself; ✕ or Back closes."
        l == "LOGIN" -> "Types your Mac password from the settings, then Return: for any password box that pops up on the Mac."
        l.contains("LANDSCAPE") -> "Turns the app sideways: the Mac on the left, the keys on the right."
        l.contains("PORTRAIT") -> "Turns the app upright again: the Mac in the middle, the keys below."
        l == "⚙" -> "Settings: the numbers of the status line with their meaning, the frames per second, the colours, the status line at the top on or off, the three rows of keys on or off (with all three off, ⚙ stays see-through over the picture), and ✕ QUIT APP, which closes the link and quits the app (Tailscale stays on)."
        l == "☰" -> "MENU: the keys below become the Mac's menu bar keys. The menu bar of the app in front opens (even when it hides itself); ← → go from menu to menu, ↑ ↓ through the items, ⏎ CHOOSE picks, ☰ OPEN opens it again, ✕ EXIT closes the menus and brings your keys back."
        l == "⇄" -> "Switches the picture between the two screens: the BenQ and the MacBook Pro. The small word under it is the screen you are watching now."
        l.contains("FULL") || l == "⛶" -> "Full screen: the phone turns sideways and the Mac fills it, with the keys as words at the sides. EXIT brings you back."
        l == "EXIT" -> "Leaves full screen."
        l == "APPS" -> "The view moves to the middle of the next open window on the Mac, at the zoom you have, on either screen: left to right, then around again. A long press: the window before. Nothing on the Mac is clicked; only your view moves."
        l.startsWith("VIEW") -> "View only: the mouse does not reach the Mac: no click, no drag, no scroll. You can watch, zoom and move around the picture; one finger moves the zoomed picture. The keys still work: ESC, DOCK, ⇥ (next tab), typing, every key. Tap again to use the mouse on the Mac."
        l == "⌘" -> "Command, held down on the Mac while it is lit. Tap it on, then TAB, TAB, TAB walks the app switcher; tap it off to choose. ⌘ on, then Q, then ⌘ off quits an app."
        l == "⌥" -> "Option. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⌃" || l == "^" -> "Control. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "⇧" -> "Shift. Tap it, then the next key: they are pressed together. It turns off after one use."
        l == "ESC" -> "Escape: closes a menu or a dialog, cancels."
        l == "TAB" -> "Tab: the next field. With ⌘ first, it switches between apps."
        l == "⏎" -> "Return: confirms, sends, a new line."
        l == "⌫" -> "Delete: removes the letter before the cursor."
        l == "←" || l == "→" || l == "↑" || l == "↓" -> "The arrow key, as on the Mac's keyboard."
        l == "FIT" || l == "FITTED" -> "Linux machine only: FIT fills the phone with the picture, its shape kept, the machine's own size untouched. One finger drags across the picture, a tap clicks, a long press drags with the button. Tap again for the whole picture."
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
// v32: Tailscale stays on when the app closes (Marko, 5.11.2026: the Pixel 7 "can be viewed from all machines"; a phone
// that switches Tailscale off is unreachable for adb, the macro door and the other phone)
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
        // v30: the macro door (port 8841, Tailscale and adb forward only) and the debugging keeper
        MacroDoor.attach(this, object : MacroTarget {
            override fun live() = session.phase == Phase.LIVE && session.rfb != null
            override fun key(keysym: Int, down: Boolean) { session.rfb?.key(keysym, down) }
            override fun pointer(x: Int, y: Int, buttons: Int) { session.rfb?.pointer(x, y, buttons) }
            override fun size() = session.fbW to session.fbH
            override fun back() { onBackPressedDispatcher.onBackPressed() }
            override fun describe() = org.json.JSONObject()
                .put("screen", when { session.phone != null -> "phone"; session.phase == Phase.LIVE -> "remote"; session.phase == Phase.CONNECTING -> "connecting"; else -> "machines" })
                .put("machine", session.phone?.name ?: session.linux?.name ?: if (session.phase == Phase.SETUP) "" else "MAC")
                .put("remote_size", "${session.fbW}x${session.fbH}")
                .put("full_screen", session.full)
                .put("status", session.status)
        })
        DebugKeeper.start(this)
        // v20: nothing starts by itself; the first page is the list of machines (Marko, 2.11.2026: "I don't need auto
        // login. Remove the option completely")
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
    /**
     * A mouse press is not a key: the sister's key count never moves for it, so a click read "NOT LANDED YET" while it
     * had landed (Marko, 30.10.2026). A press marked so is confirmed by the picture probe even while the sister answers.
     */
    @Volatile var byPicture = false
    private var watching = 0
    /** The phone's dock (DOCK): open, and its apps with their icons, kept between openings. */
    var dockOpen by mutableStateOf(false)
    /** v16: the phone on the screen instead of the Mac (null: the Mac); the Mac's link stays up meanwhile. */
    var phone by mutableStateOf<Phone?>(null)
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
        pressed = ""; byPicture = false
    }
    /** v25: the first page opens SETTINGS (a password is missing or was refused). */
    var openSettings by mutableStateOf(false)
    /** v23: why Tailscale is not standing ("" while it stands), shown with OPEN TAILSCALE on the first page. */
    var tailscaleDown by mutableStateOf("")
    /** A link is wanted (CONNECT was pressed, ✕ not): a link that falls is dialled again on return. */
    var wanted = false
    /** v21: the Linux machine entered (chromium-vm), or null for the Mac. Mac-only keys hide while it is set. */
    var linux by mutableStateOf<Tailnet.LinuxVm?>(null)
    /** v21: the machine's size before FIT, given back on a second FIT or on leaving; null: not fitted. */
    var fitFrom by mutableStateOf<Pair<Int, Int>?>(null)

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
        linux?.let { startLinux(c, p, it); return }
        loginPassword = p.getString("login", "") ?: ""
        // v25: no Mac password saved at all: nothing is dialled, SETTINGS opens
        if (loginPassword.isEmpty() && (p.getString("password", "") ?: "").isEmpty()) {
            phase = Phase.SETUP; statusBad = true; openSettings = true
            status = "THE MAC NEEDS ITS PASSWORD: WRITE IT UNDER SETTINGS, THEN ENTER AGAIN"
            return
        }
        wanted = true; phase = Phase.CONNECTING; status = "> TAILSCALE ON"; statusBad = false
        rfb?.close(); rfb = null; baseRx = 0; baseTx = 0
        LinkService.start(c)
        val gen = ++attempt
        Thread({
            // v23: every ENTER (and every redial) checks Tailscale and looks the Mac's address up again, fresh
            val tsUp = Tailscale.ensure(c) { if (gen == attempt) status = it }
            tailscaleDown = if (tsUp) "" else Tailscale.whyDown(c)
            if (gen != attempt || !wanted) return@Thread
            val port = p.getString("port", "5900")?.toIntOrNull() ?: 5900
            val fresh = if (tsUp) { status = "> TAILSCALE: LOOKING UP THE MAC'S ADDRESS"; Tailnet.macAddresses(c, p) }
                else listOfNotNull(p.getString(Tailnet.MAC_IP, null))
            android.util.Log.i("MantraRemote", "tailscale up=$tsUp own=${Tailscale.ownIp()} mac candidates=$fresh")
            val host = MacFinder.find(c, port, fresh + listOf(p.getString("host", "") ?: "", p.getString("lan_host", "") ?: "")) {
                if (gen == attempt) status = it
            }
            if (gen != attempt || !wanted) return@Thread
            if (host == null) {
                phase = Phase.SETUP; statusBad = true
                status = if (!tsUp) tailscaleDown + " (THE HOME WI-FI DID NOT ANSWER EITHER)"
                    else "THE MAC DOES NOT ANSWER AT ${fresh.joinToString(", ")}. IS IT AWAKE, ON TAILSCALE, SCREEN SHARING ON? ENTER TRIES AGAIN."
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

    /**
     * v21 (Marko, 2.11.2026: "update the Android application Mantra Remote VNC to be compatible with Chromium virtual
     * machine"): a Linux machine is dialled straight at its Tailscale address and VNC port, with its VNC password
     * (kept encrypted, SETTINGS); no Mac finder, no Dock Switcher.
     */
    private fun startLinux(c: Context, p: android.content.SharedPreferences, vm: Tailnet.LinuxVm) {
        val password = Secrets.pin(p, Tailnet.vncKey(vm))
        wanted = true; phase = Phase.CONNECTING; status = "> TAILSCALE ON"; statusBad = false
        rfb?.close(); rfb = null; baseRx = 0; baseTx = 0; sisterOk = false; sisterHost = ""
        LinkService.start(c)
        val gen = ++attempt
        Thread({
            val tsUp = Tailscale.ensure(c) { if (gen == attempt) status = it }
            tailscaleDown = if (tsUp) "" else Tailscale.whyDown(c)
            if (gen != attempt || !wanted) return@Thread
            if (!tsUp) { phase = Phase.SETUP; statusBad = true; status = tailscaleDown; return@Thread }
            // v23: its address looked up again on every ENTER
            status = "> TAILSCALE: LOOKING UP ${vm.name}"
            val ip = Tailnet.vmAddress(c, p, vm)
            if (gen != attempt || !wanted) return@Thread
            host = ip
            status = "> DIAL ${vm.name} $ip:${vm.port}"
            android.util.Log.i("MantraRemote", "dialling ${vm.name} at $ip:${vm.port}")
            dial = { colours ->
                rfb?.let { old -> old.close(); baseRx += old.rxBytes.get(); baseTx += old.txBytes.get() }
                val r = Rfb(this, colours, extendedSize = true); rfb = r
                r.connect(ip, vm.port, password)
            }
            main.post { if (gen == attempt && wanted) dial(coloursSaved(p)) }
        }, "dial-linux").start()
    }

    /**
     * v24: enter a machine of the list, from the first page or from ⚙ CONNECT TO OTHER SESSION (Marko, 3.11.2026: "in
     * my settings, add a button connect to other session, and then I can switch"). A link already open is closed
     * first. False when the machine still needs its password under SETTINGS.
     */
    fun enter(pl: Tailnet.Place): Boolean {
        val p = prefs ?: return true
        val vm = Tailnet.linuxVm(pl)
        when {
            pl.kind == Tailnet.Kind.MAC -> {
                if (phase != Phase.SETUP) hangUp()
                phone = null; linux = null
                p.edit().putString("host", pl.ip).putString(Tailnet.MAC_NAME, pl.name.lowercase()).apply(); start()
            }
            vm != null -> {
                if (Secrets.pin(p, Tailnet.vncKey(vm)).isEmpty()) {
                    status = "${vm.name}: WRITE ITS VNC PASSWORD UNDER SETTINGS"; statusBad = true; return false
                }
                if (phase != Phase.SETUP) hangUp()
                phone = null; linux = vm; start()
            }
            pl.kind == Tailnet.Kind.PHONE -> {
                val known = Phones.all(p).firstOrNull { it.address.substringBefore(':') == pl.ip }
                phone = known ?: Phone(pl.name, pl.ip + ":5555").also { Phones.add(p, it) }
            }
            else -> status = "${pl.name}: ENTERING THIS MACHINE COMES IN A NEXT VERSION"
        }
        return true
    }

    /** True when [pl] is the machine on the screen now. */
    fun isCurrent(pl: Tailnet.Place): Boolean = when {
        phone != null -> phone?.address?.substringBefore(':') == pl.ip
        linux != null -> Tailnet.linuxVm(pl) == linux
        else -> pl.kind == Tailnet.Kind.MAC && phase != Phase.SETUP
    }

    /** ✕ or the app closed: the link is closed on purpose, nothing dials again, the service ends and Tailscale goes off. */
    fun hangUp() {
        wanted = false; attempt++
        // v21: a fitted Linux machine gets its old size back before the link closes
        val r = rfb; val from = fitFrom
        if (r != null && from != null) { r.setDesktopSize(from.first, from.second); r.closeAfterWrites() } else r?.close()
        fitFrom = null
        // v20: closing a machine keeps Tailscale on, so the list beneath it still sees every machine; Tailscale goes
        // off only when the app closes (closeApp)
        app?.let { LinkService.stop(it) }
        phase = Phase.SETUP; status = "LINK CLOSED"; statusBad = false
    }

    override fun onConnected(name: String, width: Int, height: Int) {
        android.util.Log.i("MantraRemote", "framebuffer ${width}x$height name '$name'")
        if (name.isNotEmpty()) this.name = name
        fbW = width; fbH = height
        phase = Phase.LIVE; status = "LINK ESTABLISHED"; statusBad = false; redials = 0
        if (background) rfb?.minIntervalMs = BACKGROUND_MS
        app?.let(LinkService::start)
        if (linux == null) watchKeys()      // the Dock Switcher's key count lives on the Mac only
    }
    override fun onFrame() { tick++ }
    // the picture probe speaks only while the sister on the Mac does not answer
    override fun onLanded() { if (!sisterOk || byPicture) main.post { landed() } }
    override fun onClosed(reason: String) {
        phase = Phase.SETUP; status = reason.uppercase(); statusBad = true
        // v25 (Pixel 7 test, 3.11.2026: one tap with no password gave six refused logins in 38 s): a refused login is
        // never dialled again; a wrong password tried over and over can lock the account. SETTINGS opens instead.
        val r = reason.uppercase()
        if (r.startsWith("LOGIN REFUSED") || r.startsWith("REFUSED") || r.startsWith("NO VNC PASSWORD") || r.startsWith("NOT A VNC SERVER")) {
            wanted = false; attempt++
            app?.let { LinkService.stop(it) }
            if (r.startsWith("LOGIN REFUSED")) {
                status = "LOGIN REFUSED: CHECK THE ${linux?.name ?: "MAC"} PASSWORD UNDER SETTINGS, THEN ENTER AGAIN"
                openSettings = true
            }
            android.util.Log.w("MantraRemote", "not dialled again: $reason")
            return
        }
        // in front, a fall is dialled again by itself (Tailscale coming up changes the network under the link);
        // behind, on return (onStart)
        if (wanted && !background && redials < MAX_REDIALS) { redials++; main.postDelayed({ if (wanted && phase == Phase.SETUP) start() }, 2000) }
    }
    /** The picture's time, averaged over the last pictures, so the meter moves slowly (Marko, 26.10.2026). */
    @Volatile var avgMs = -1.0
    override fun onStats(bytesIn: Long, frames: Long, lastFrameMs: Long) {
        this.frames = frames
        avgMs = if (avgMs < 0) lastFrameMs.toDouble() else avgMs * 0.9 + lastFrameMs * 0.1
    }
}

@Composable
private fun App(prefs: android.content.SharedPreferences, session: Session) {
    // v18, MANTRA_MANIFEST system-bars.md §0 (Marko, 2.11.2026: "No Mantra application ever drawing behind what already
    // is there in the screen in Android"): the safe area pads the root in BOTH modes. In NORMAL the status bar, the camera
    // hole and the navigation bar are outside the app; in FULL SCREEN the bars are hidden (their insets are 0, so the
    // picture still fills the glass), the camera hole stays clear, and when Android does show its buttons (the keyboard
    // brings them back on the right in landscape) the rail moves out from under them instead of sitting below them.
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Bg)
    ) {
        when (session.phase) {
            Phase.SETUP, Phase.CONNECTING -> Setup(prefs, session)
            Phase.LIVE -> Live(session)
        }
        // v16: a phone on the whole screen, over the Mac or over the first screen (a phone needs no Mac)
        session.phone?.let { p -> PhoneScreen(p, pin = Secrets.pin(prefs, p.address), onMac = { session.phone = null }) }
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

/** v30: a button on screen can be pressed by the macro door by its word (Press "ESC"), while it is shown. */
@Composable
private fun MacroButton(word: String, onClick: () -> Unit) {
    val latest by androidx.compose.runtime.rememberUpdatedState(onClick)
    androidx.compose.runtime.DisposableEffect(word) {
        val f: () -> Unit = { latest() }
        MacroDoor.buttons[word] = f
        onDispose { MacroDoor.buttons.remove(word, f) }
    }
}

/** A finger-sized key: amber box line, label inside, lit while active. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(label: String, modifier: Modifier = Modifier, active: Boolean = false, size: Int = 16, sub: String? = null, padH: Int = 10, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val view = LocalView.current
    MacroButton(label, onClick)
    // v15 (Marko, 26.10.2026: "When I press a button, the symbol ... needs to change color to white for half a
    // second, so I know I pressed it"): every press lights the symbol white for PRESS_FLASH_MS
    var flash by remember { mutableStateOf(0) }
    LaunchedEffect(flash) { if (flash > 0) { kotlinx.coroutines.delay(PRESS_FLASH_MS); flash = 0 } }
    val lit = flash > 0
    Box(
        modifier
            .height(52.dp)
            .background(if (active) Amber else BgRaised)
            .border(1.dp, if (lit) Color.White else if (active) AmberGlow else Rule)
            .combinedClickable(
                onLongClick = { flash++; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); (onLongClick ?: { helpFor.value = label })() },
            ) { flash++; view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .padding(horizontal = padH.dp),
        contentAlignment = Alignment.Center,
    ) {
        // a key's word never breaks (Marko, 25.10.2026); a modifier carries its name under the symbol, small
        val ink = if (lit) Color.White else if (active) Bg else Amber
        if (sub == null) T(label, ink, size, bold = true, oneLine = true)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(label, ink, size, bold = true, oneLine = true)
            T(sub, if (lit) Color.White else if (active) Bg else AmberDim, 9, oneLine = true)
        }
    }
}


/** v17: the three rows of keys below the picture, each switched on or off in ⚙. */
private const val ROW_KEYS = "row_keys_on"
private const val ROW_TOOLS = "row_tools_on"
private const val ROW_VIEWS = "row_views_on"

/** v17: the inner side margin of the keys in the two lower rows, narrow so the words fit (Marko, 27.10.2026). */
private const val ROW_PAD = 3

/** v15: how long a pressed key's symbol stays white. */
private const val PRESS_FLASH_MS = 500L

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
    // v20 (Marko, 2.11.2026: "What I want to see on the first page is that Tailscale reads all my machines, and I have a
    // button for each machine. Then I click on the button and get into the machine. When I close the machine, I get the
    // list of machines again. In the list of machines, there will also be an X to close the app completely"). AUTO LOGIN
    // and its "→ THE MAC" are gone ("Remove the option completely"); the passwords wait behind SETTINGS.
    val activity = LocalContext.current as? android.app.Activity
    var settings by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf<List<Tailnet.Place>?>(null) }
    var looking by remember { mutableStateOf(0) }
    LaunchedEffect(s.openSettings) { if (s.openSettings) { settings = true; s.openSettings = false } }
    LaunchedEffect(looking) {
        online = null
        online = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val c = s.app ?: return@withContext emptyList()
            s.tailscaleDown = if (Tailscale.ensure(c)) "" else Tailscale.whyDown(c)
            Tailnet.places(c, prefs)
        }
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // v22 (Marko, 2.11.2026: "when the user clicks at the top of the application, it comes to the GitHub page to
            // download the latest version" ... "only from the login screen, not inside the remote session"): only here.
            Column(Modifier.weight(1f).clickable {
                activity?.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/markoboskoauroville/MANTRA_REMOTE_VNC_TAILSCALE/releases/latest")))
            }) {
                T("MANTRA REMOTE VIEWING ▊", Amber, 26, bold = true)
                T("v" + BuildConfig.VERSION_NAME + "  ·  tap for the latest version", AmberDim, 13)
            }
            Key("✕", Modifier.width(60.dp), size = 22) { activity?.finish() }   // closes the app: link, service (Tailscale stays on, v32)
        }
        T("── MY MACHINES ─────────────────", Rule, 16)
        val list = online
        if (s.tailscaleDown.isNotEmpty()) {
            T("> " + s.tailscaleDown, RedAlert, 14)
            Key("OPEN TAILSCALE", Modifier.fillMaxWidth(), size = 14) { s.app?.let(Tailscale::openApp) }
        }
        if (list == null) T("> Tailscale is reading your machines …", AmberDim, 14)
        else if (list.isEmpty()) T("> no machine answers", RedAlert, 14)
        else list.forEach { pl -> Machine(pl, busy = s.phase == Phase.CONNECTING) {
            when {
                // v23: a Mac or a VM shown OFFLINE is entered all the same: ENTER checks Tailscale and looks its
                // address up again, so a list read before Tailscale stood (or with an old address) never blocks it
                (pl.kind == Tailnet.Kind.MAC || Tailnet.linuxVm(pl) != null) -> if (s.phase != Phase.CONNECTING) {
                    if (!s.enter(pl)) settings = true
                }
                !pl.online -> looking++
                else -> s.enter(pl)
            }
        } }
        Key("↻ LOOK AGAIN", Modifier.fillMaxWidth(), size = 14) { looking++ }
        if (s.status.isNotEmpty()) T("> " + s.status, if (s.statusBad) RedAlert else GreenOk, 14)
        Spacer(Modifier.height(8.dp))
        Key(if (settings) "SETTINGS ▲" else "SETTINGS ▼", Modifier.fillMaxWidth(), size = 14) { settings = !settings }
        if (settings) SetupSettings(prefs) { looking++ }
    }
}

/** v20: one machine, one button: its name, and under it what it is and its Tailscale address. */
@Composable
private fun Machine(pl: Tailnet.Place, busy: Boolean, onClick: () -> Unit) {
    MacroButton(pl.name, onClick)
    val ink = if (pl.online) Amber else AmberDim
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).background(BgRaised).border(1.dp, if (pl.online) AmberGlow else Rule)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T((if (pl.online) "● " else "○ ") + pl.name, if (pl.online) GreenOk else AmberDim, 20, bold = true)
            T(pl.os.lowercase() + "  " + pl.ip, AmberDim, 13)
        }
        T(when {
            busy && (pl.kind == Tailnet.Kind.MAC || Tailnet.linuxVm(pl) != null) -> "…"
            !pl.online && (pl.kind == Tailnet.Kind.MAC || Tailnet.linuxVm(pl) != null) -> "OFFLINE · TRY ▶"
            !pl.online -> "OFFLINE"
            pl.kind == Tailnet.Kind.MAC || pl.kind == Tailnet.Kind.PHONE || Tailnet.linuxVm(pl) != null -> "ENTER ▶"
            else -> "SOON"
        }, ink, 15, bold = true)
    }
}

/** v20: what used to fill the first page, folded away: the Tailscale pass, the Mac's passwords, each phone's PIN. */
@Composable
private fun SetupSettings(prefs: android.content.SharedPreferences, reread: () -> Unit) {
    var port by remember { mutableStateOf(prefs.getString("port", "5900") ?: "5900") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var login by remember { mutableStateOf(prefs.getString("login", "") ?: "") }
    var user by remember { mutableStateOf(prefs.getString("user", "markobosko") ?: "markobosko") }
    var saved by remember { mutableStateOf(false) }
    T("── TAILSCALE ──────────────────", Rule, 16)
    if (Tailnet.keyed(prefs)) {
        T("The pass is saved (encrypted on this phone): every machine of your tailnet is listed.", AmberDim, 12)
        Key("FORGET THE PASS", Modifier.fillMaxWidth(), size = 14) {
            Secrets.putPin(prefs, Tailnet.ID, ""); Secrets.putPin(prefs, Tailnet.SECRET, ""); reread()
        }
    } else {
        T("Without a pass only the machines this app knows. Tailscale admin console → Settings → Trust credentials → " +
          "OAuth, scope Devices Core: Read; paste both here.", AmberDim, 12)
        var tid by remember { mutableStateOf("") }
        var tsec by remember { mutableStateOf("") }
        Field("TAILSCALE OAUTH CLIENT ID", tid) { tid = it.trim() }
        Field("TAILSCALE OAUTH CLIENT SECRET", tsec, secret = true) { tsec = it.trim() }
        if (tid.isNotEmpty() && tsec.isNotEmpty()) Key("SAVE (ENCRYPTED ON THIS PHONE)", Modifier.fillMaxWidth(), size = 14) {
            Secrets.putPin(prefs, Tailnet.ID, tid); Secrets.putPin(prefs, Tailnet.SECRET, tsec); reread()
        }
    }
    T("── THE MAC ───────────────────", Rule, 16)
    Field("PORT", port, number = true) { port = it.filter(Char::isDigit); saved = false }
    Field("VNC PASSWORD (SCREEN SHARING)", password, secret = true) { password = it; saved = false }
    Field("MAC USER (SHORT NAME)", user) { user = it.trim(); saved = false }
    Field("MAC LOGIN PASSWORD (THE LOGIN BUTTON)", login, secret = true) { login = it; saved = false }
    Key(if (saved) "SAVED" else "SAVE", Modifier.fillMaxWidth(), active = saved, size = 14) {
        prefs.edit().putString("port", port).putString("password", password).putString("login", login).putString("user", user).apply()
        Hold.session?.loginPassword = login
        saved = true
    }
    // v21: each Linux machine's VNC password, kept encrypted on this phone (Secrets), like a phone's PIN
    Tailnet.LINUX_VMS.forEach { vm ->
        T("── ${vm.name} (LINUX) ─────────", Rule, 16)
        var vncPw by remember(vm.ip) { mutableStateOf(Secrets.pin(prefs, Tailnet.vncKey(vm))) }
        var vmSaved by remember(vm.ip) { mutableStateOf(false) }
        Field("VNC PASSWORD (${vm.ip}:${vm.port}; KEPT ENCRYPTED ON THIS PHONE)", vncPw, secret = true) { vncPw = it; vmSaved = false }
        Key(if (vmSaved) "SAVED" else "SAVE ${vm.name}", Modifier.fillMaxWidth(), active = vmSaved, size = 14) {
            Secrets.putPin(prefs, Tailnet.vncKey(vm), vncPw); vmSaved = true
        }
    }
    Phones.all(prefs).forEach { p ->
        T("── ${p.name} ───────────────────", Rule, 16)
        var pin by remember(p.address) { mutableStateOf(Secrets.pin(prefs, p.address)) }
        Field("PIN (UNLOCKS IT ON ENTERING; KEPT ENCRYPTED ON THIS PHONE)", pin, secret = true, number = true) { pin = it.filter(Char::isDigit) }
        Key("SAVE PIN", Modifier.fillMaxWidth(), size = 14) { Secrets.putPin(prefs, p.address, pin) }
    }
}

/** Framebuffer rectangle of a monitor. A framebuffer of another width (other displays attached) is shown whole. */
private fun monitorRect(i: Int, fbW: Int, fbH: Int): IntArray {
    val totalW = MONITORS.maxOf { it.x + it.w }
    val totalH = MONITORS.maxOf { it.y + it.h }
    if (fbW != totalW || fbH < totalH) return intArrayOf(0, 0, fbW, fbH)
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
private val MOD_KEYS by lazy { listOf(Triple("⌃", K_CTRL, "control"), Triple("⌥", K_OPT, "option"), Triple("⌘", K_CMD, "command")) }
// v21: a Linux machine has no Command; Alt is Option's keysym
private val LINUX_MOD_KEYS by lazy { listOf(Triple("⌃", K_CTRL, "ctrl"), Triple("⌥", K_OPT, "alt")) }
private const val K_OPT = 0xffe9
private const val K_CTRL = 0xffe3
private const val K_SHIFT = 0xffe1
private const val K_BACKSPACE = 0xff08
private const val K_TAB = 0xff09
private const val K_BRACERIGHT = 0x7d
private const val K_D = 0x64
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
    // v21: on a Linux machine (chromium-vm) the Mac-only keys hide: MON, DOCK, LOGIN, ☰, ⌘
    val lx = s.linux != null
    val modList = if (lx) LINUX_MOD_KEYS else MOD_KEYS
    val where = s.linux?.name?.substringBefore('-') ?: "MAC"
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // v20: Back closes the machine and brings the list of machines (Marko, 2.11.2026); the Dock and full screen keep theirs
    androidx.activity.compose.BackHandler(enabled = !s.full) { s.hangUp() }
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
    // v26 FIT (Marko, 5.11.2026: "It's big automatically. But enable finger tap holding on the screen ... going through
    // this picture, which is bigger than the screen"): the picture fills the phone, its aspect kept, the machine keeps
    // its own size; one finger drags the picture, a tap clicks, a long press drags with the button
    var fitView by remember { mutableStateOf(false) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var scrollMode by remember { mutableStateOf(false) }
    // v17: 🖱 MOUSE mode (Marko, 27.10.2026: "any tapping moves the mouse pointer, three fingers click, and two fingers
    // right click"): one finger only moves the Mac's pointer to where it touches, a tap of three fingers is the left
    // click and of two fingers the right click, both where the pointer stands; spread, pinch and pan still work.
    var mouseMode by remember { mutableStateOf(s.prefs?.getBoolean("mouse_mode", false) ?: false) }
    LaunchedEffect(mouseMode) { s.prefs?.edit()?.putBoolean("mouse_mode", mouseMode)?.apply() }
    var mouseAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // v35: what DOCK found before it moved the picture: given back on the second press
    var dockBefore by remember { mutableStateOf<DockBefore?>(null) }
    // v37: the window APPS stands on (its place in the Mac's list, left to right)
    var appsIndex by remember { mutableIntStateOf(-1) }
    // v24: the mouse button locked down: 0 none, 1 the left (L long press, or D), 4 the right (R long press)
    var held by remember { mutableIntStateOf(0) }
    val dragHeld = held == 1
    // v39 (Marko, 8.11.2026): L and R long-pressed are a click MODE, one at a time: with L lit a tap on the screen clicks
    // left at once where the finger touches, with R lit it clicks right there; 0 = none (the finger only moves the pointer)
    var clickLock by remember { mutableIntStateOf(0) }
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

    layoutMonitors(s.fbW, s.fbH)
    val rect = monitorRect(monitor, s.fbW, s.fbH)
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(rfb, monitor, s.fbW, s.fbH) {
        rfb.setRegion(rect[0], rect[1], rect[2], rect[3])
        val p = if (lx) null else s.prefs          // v21: a Linux machine keeps the Mac's monitor, zoom and pan untouched
        if (!restored && p != null && p.getInt("monitor", -1) == monitor) {
            zoom = p.getFloat("zoom", 1f); pan = Offset(p.getFloat("pan_x", 0f), p.getFloat("pan_y", 0f))
        } else { zoom = 1f; pan = Offset.Zero }
        restored = true
        p?.edit()?.putInt("monitor", monitor)?.apply()
    }
    LaunchedEffect(zoom, pan) {
        if (!restored || lx) return@LaunchedEffect
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
            s.lastMs = s.avgMs.coerceAtLeast(0.0).toLong()           // the averaged time, once a second
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
    // v26: the picture never leaves an empty edge where it is larger than the view, and stays centred where it is smaller
    fun clampPan(p: Offset): Offset {
        val w = rect[2] * scale(); val h = rect[3] * scale()
        fun axis(v: Float, size: Float, view: Int, base: Float) =
            if (size <= view) (view - size) / 2f - base else v.coerceIn(view - size - base, -base)
        return Offset(axis(p.x, w, viewSize.width, baseX()), axis(p.y, h, viewSize.height, baseY()))
    }
    fun coverZoom(): Float = if (viewSize.width > 0 && rect[2] > 0)
        maxOf(viewSize.width / rect[2].toFloat(), viewSize.height / rect[3].toFloat()) / fit() else 1f
    // v26: FIT follows a new picture size and the view (turned sideways, rows shown or hidden), after the reset above
    LaunchedEffect(rfb, monitor, s.fbW, s.fbH, viewSize) { if (fitView) { zoom = coverZoom(); pan = clampPan(Offset(0f, -10000f)) } }
    fun withMods(block: () -> Unit) {
        val m = mods.value
        m.forEach { rfb.key(it, true) }
        block()
        m.reversed().forEach { rfb.key(it, false) }
        if (m.isNotEmpty()) mods.value = emptySet()
    }
    // what is on its way, shown above the keys until the Mac has it (Marko, 26.10.2026)
    fun travelling(label: String, picture: Boolean = false) {
        s.byPicture = picture; s.pressed = label; s.pressedAt = System.currentTimeMillis(); s.pressedBase = if (picture) -1 else s.keysSeen
    }
    // v17: the left and right buttons beside 🖱, pressed where the pointer stands (Marko, 27.10.2026: "I tap to the
    // point, and then I have action buttons for the left and right mouse button"); no pointer yet: the picture's middle
    fun mouseClick(button: Int) {
        val (x, y) = mouseAt ?: (rect[0] + rect[2] / 2 to rect[1] + rect[3] / 2)
        // VIEW ONLY lets no click through: say so, instead of a click that seems sent (Marko, 27.10.2026: "the mouse
        // clicks are not landing")
        if (viewOnly) { travelling("VIEW ONLY: NO CLICK"); return }
        if (held != 0) { held = 0; Thread { rfb.pointer(x, y, 0) }.start() }   // a click ends a lock first
        travelling(if (button == 1) "CLICK" else "RIGHT CLICK", picture = true)
        // the pointer goes there first, the button is held 50 ms, as a hand does; a press and a release in the same
        // instant can be lost on the Mac
        Thread {
            rfb.pointer(x, y, 0); Thread.sleep(30)
            rfb.pointer(x, y, button); Thread.sleep(50)
            rfb.pointer(x, y, 0); rfb.probe()
        }.start()
    }
    // 🖱 and VIEW ONLY exclude each other: the mouse mode is for clicking, VIEW ONLY for never clicking
    // v18: DRAG, the third button of the mouse (Marko, 30.10.2026: "Drag means pressing the key and keeping it down until
    // I press drag again"): the first press holds the left button where the pointer stands, every move of the pointer
    // then drags, the second press lets it go
    // v24 (Marko, 3.11.2026: "Short press is clicking, long press locking, and same for the right mouse button.
    // Unlocking ... again, long press"): a long press on L or R locks that button down (lit), a second long press lets
    // it go; while locked, moving the pointer drags with it. D stays the left lock as before.
    fun toggleHold(button: Int) {
        val (x, y) = mouseAt ?: (rect[0] + rect[2] / 2 to rect[1] + rect[3] / 2)
        if (held != button && viewOnly) { travelling("VIEW ONLY: NO LOCK"); return }
        val was = held
        held = if (held == button) 0 else button
        val now = held
        val name = if (button == 1) "LEFT" else "RIGHT"
        travelling(if (now != 0) "$name BUTTON LOCKED DOWN" else "$name BUTTON LET GO", picture = true)
        Thread {
            if (now != 0) { rfb.pointer(x, y, 0); Thread.sleep(30); rfb.pointer(x, y, now) } else if (was != 0) rfb.pointer(x, y, 0)
            rfb.probe()
        }.start()
    }
    fun toggleDrag() { clickLock = 0; toggleHold(1) }
    // v42 (Marko, 9.11.2026): L or R long-pressed = a STICKY CLICK MODE (not a held button): every tap on the picture is
    // one click at the ring's current place; a finger moves the ring relatively. D stays the held drag.
    fun toggleLock(button: Int) {
        if (clickLock != button && viewOnly) { travelling("VIEW ONLY: NO CLICK"); return }
        if (held != 0) toggleHold(held)
        clickLock = if (clickLock == button) 0 else button
        val name = if (button == 1) "LEFT" else "RIGHT"
        travelling(if (clickLock != 0) "$name CLICK ON: TAP CLICKS" else "$name CLICK OFF", picture = true)
    }
    fun setMouse(on: Boolean) { mouseMode = on; if (on) { scrollMode = false; viewOnly = false } else { clickLock = 0; if (held != 0) toggleHold(held) } }
    LaunchedEffect(Unit) { if (mouseMode && viewOnly) viewOnly = false }
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
            if (k == K_CTRL && !cmdHeld && !lx) { travelling("⌃"); rfb.key(K_CTRL, true); rfb.key(K_CTRL, false); rfb.probe() }
            mods.value = if (k in mods.value) mods.value - k else mods.value + k
        }
    }
    fun modOn(k: Int) = if (k == K_CMD) cmdHeld else k in mods.value
    fun releaseCmd() { if (cmdHeld) { cmdHeld = false; rfb.key(K_CMD, false) } }
    // NEXT TAB ▶: ⌘⇧], the next tab in Chrome and in iTerm alike, so the phone need not know which app is in front
    // (Marko, 25.10.2026: "go through the tabs", then "the same shortcut doesn't work in iTerm"). Ctrl-Tab was Chrome's only.
    fun nextTab() {
        mods.value = emptySet(); cmdHeld = false
        travelling("⇥ NEXT TAB")
        // v21: Chrome on Linux: Ctrl+Tab
        if (lx) { rfb.key(K_CTRL, true); rfb.key(K_TAB, true); rfb.key(K_TAB, false); rfb.key(K_CTRL, false); rfb.probe(); return }
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
            if (s.pressed.isNotEmpty()) T(if (late) "${s.pressed}  NOT LANDED YET" else "${s.pressed}  →  $where", if (late) RedAlert else AmberGlow, 11, oneLine = true)
        }
    }

    // DOCK: the Dock's apps on the whole phone, picked here, switched or started on the Mac (Marko, 26.10.2026: "build a
    // remote controller for my dock, so I'm not waiting for the dock and trying to find it on screen")
    fun dock() { s.dockOpen = true; s.dockNote = "" }
    // v17: DOCK shows or hides the Mac's own Dock, one press each way (Marko, 27.10.2026: "the macOS Dock is
    // auto-hiding, so the button should unhide it ... the button toggles"): ⌥⌘D, macOS's own "Turn Dock Hiding On/Off".
    // The phone's dock above moved to a long press on DOCK.
    // v35 (Marko, 8.11.2026: "dock button actually unhide the dock from macOS. And automatically scroll my view in
    // whatever zoom level it is down to the dock and moves my mouse down to the dock. And when I press Dock again, it
    // restores the view"): the Dock Switcher sets the Dock's own hiding (no ⌥⌘D, which the VNC keys did not always
    // land) and says where the bar stands; the picture is moved, at the zoom it has, so the bar sits at the bottom of the
    // phone, and the pointer goes onto the bar. The second press hides the Dock again (if it was hiding) and puts the
    // zoom, the picture and the pointer back where they were. ⌥⌘D stays only for when the Dock Switcher does not answer.
    fun dockKeys() {
        listOf(K_OPT, K_CMD, K_D).forEach { rfb.key(it, true) }
        listOf(K_D, K_CMD, K_OPT).forEach { rfb.key(it, false) }
        rfb.probe()
    }
    fun dockHiding() {
        mods.value = emptySet(); releaseCmd()
        val host = s.sisterHost.ifEmpty { MacFinder.MAC_TAILSCALE_IP }
        val before = dockBefore
        if (before == null) {
            travelling("DOCK ↓")
            Thread {
                val was = MacSister.dock(host, null)
                val d = if (was != null) MacSister.dock(host, true) else null
                if (d == null) { view.post { travelling("DOCK ⇅ (KEYS)") }; dockKeys(); return@Thread }
                view.post {
                    dockBefore = DockBefore(zoom, pan, mouseAt, was!!.hidden, monitor)
                    // the bar in the picture: the Mac's own pixels (scaled if the picture is not the size it expects),
                    // else its fractions on the shown monitor
                    val kx = if (d.tw > 0) s.fbW / d.tw else 1.0; val ky = if (d.th > 0) s.fbH / d.th else 1.0
                    val px: Double; val pBottom: Double; val py: Double
                    if (d.fw > 0) { px = (d.fx + d.fw / 2) * kx; pBottom = (d.fy + d.fh) * ky; py = (d.fy + d.fh / 2) * ky }
                    else { px = rect[0] + (d.x + d.w / 2) * rect[2]; pBottom = rect[1] + (d.y + d.h) * rect[3]; py = rect[1] + (d.y + d.h / 2) * rect[3] }
                    // the monitor the bar is on, when the phone shows one monitor at a time
                    val on = MONITORS.indexOfFirst { px >= it.x && px < it.x + it.w && py >= it.y && py < it.y + it.h }
                    val switch = on >= 0 && on != monitor && monitorRect(on, s.fbW, s.fbH)[2] != s.fbW
                    val keepZoom = zoom
                    if (switch) monitor = on
                    val mx = px.roundToInt(); val my = py.roundToInt()
                    mouseAt = mx to my
                    Thread { rfb.pointer(mx, my, 0); rfb.probe() }.start()
                    // after a monitor switch its own reset runs first: the picture is placed a moment later
                    view.postDelayed({
                        // computed here from the monitor now shown (the composition's own helpers may still hold
                        // the one before the switch): the bar's middle under the phone's middle, its bottom on the
                        // phone's bottom edge, at the zoom he had, never past the picture's edges
                        val r = monitorRect(on.takeIf { switch } ?: dockBefore?.monitor ?: 0, s.fbW, s.fbH)
                        val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
                        if (vw > 0 && r[2] > 0) {
                            val f = minOf(vw / r[2], vh / r[3]); val k = f * keepZoom
                            val bx = (vw - r[2] * f) / 2f; val by = (vh - r[3] * f) / 2f
                            fun axis(v: Float, size: Float, view: Float, base: Float) =
                                if (size <= view) (view - size) / 2f - base else v.coerceIn(view - size - base, -base)
                            zoom = keepZoom
                            pan = Offset(axis(vw / 2f - bx - (px - r[0]).toFloat() * k, r[2] * k, vw, bx),
                                axis(vh - by - (pBottom - r[1]).toFloat() * k, r[3] * k, vh, by))
                        }
                        s.landed()
                    }, if (switch) 200L else 0L)
                }
            }.start()
        } else {
            travelling("DOCK ↑ VIEW BACK")
            dockBefore = null
            val back = before.monitor != monitor
            if (back) monitor = before.monitor
            mouseAt = before.mouse
            view.postDelayed({ zoom = before.zoom; pan = before.pan }, if (back) 200L else 0L)
            Thread {
                if (MacSister.dock(host, !before.hidden) == null) dockKeys()
                before.mouse?.let { (x, y) -> rfb.pointer(x, y, 0) }
                rfb.probe()
                view.post { s.landed() }
            }.start()
        }
    }

    // v37 APPS (Marko, 8.11.2026: "imagine the Mantra remote is like a window to my Mac ... on whatever level of zoom I
    // am ... it's going to scroll to next open app on the screen ... my window is at the middle of iTerm, whatever zoom
    // level is ... It's like automatic pan and scanning. It targets each open window"): the Dock Switcher lists the open
    // windows in the picture's pixels (left to right, both screens); a tap moves the view to the next one's middle, a
    // long press to the one before, at the zoom he has, switching the monitor when the window is on the other one.
    // Nothing is clicked or brought to the front: only the view moves.
    fun appsStep(step: Int) {
        val host = s.sisterHost.ifEmpty { MacFinder.MAC_TAILSCALE_IP }
        travelling(if (step > 0) "APPS ▶" else "◀ APPS")
        Thread {
            val got = MacSister.windows(host)
            if (got == null || got.first.isEmpty()) { view.post { s.pressed = if (got == null) "APPS: THE MAC DID NOT ANSWER" else "APPS: NO OPEN WINDOW" }; return@Thread }
            val (wins, size) = got
            view.post {
                val i = ((appsIndex + step) % wins.size + wins.size) % wins.size
                appsIndex = i
                val w = wins[i]
                val kx = if (size.first > 0) s.fbW / size.first else 1.0; val ky = if (size.second > 0) s.fbH / size.second else 1.0
                val px = (w.x + w.w / 2) * kx; val py = (w.y + w.h / 2) * ky
                val on = MONITORS.indexOfFirst { px >= it.x && px < it.x + it.w && py >= it.y && py < it.y + it.h }
                val switch = on >= 0 && on != monitor && monitorRect(on, s.fbW, s.fbH)[2] != s.fbW
                val keepZoom = zoom
                val target = if (switch) on else monitor
                if (switch) monitor = on
                view.postDelayed({
                    val r = monitorRect(target, s.fbW, s.fbH)
                    val vw = viewSize.width.toFloat(); val vh = viewSize.height.toFloat()
                    if (vw > 0 && r[2] > 0) {
                        val f = minOf(vw / r[2], vh / r[3]); val k = f * keepZoom
                        val bx = (vw - r[2] * f) / 2f; val by = (vh - r[3] * f) / 2f
                        fun axis(v: Float, sz: Float, vv: Float, base: Float) =
                            if (sz <= vv) (vv - sz) / 2f - base else v.coerceIn(vv - sz - base, -base)
                        zoom = keepZoom
                        pan = Offset(axis(vw / 2f - bx - (px - r[0]).toFloat() * k, r[2] * k, vw, bx),
                            axis(vh / 2f - by - (py - r[1]).toFloat() * k, r[3] * k, vh, by))
                    }
                    s.pressed = "${i + 1}/${wins.size} ${w.app.uppercase()}"; s.pressedAt = System.currentTimeMillis()
                    view.postDelayed({ s.landed() }, 1500)
                }, if (switch) 200L else 0L)
            }
        }.start()
    }

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
            .pointerInput(rect.contentHashCode(), scrollMode, viewOnly, mouseMode, held, clickLock) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = down.position
                    val t0 = System.currentTimeMillis()
                    var moved = false
                    var multi = false
                    var dragging = false
                    var last = start
                    var scrollAcc = 0f
                    var fingers = 1          // the most fingers down at once in this gesture
                    var multiTravel = 0f     // how far two or three fingers went: little = a tap, a click in MOUSE mode
                    val mouse = mouseMode && !viewOnly && !scrollMode
                    var relAt = if (held != 0 || clickLock != 0) mouseAt else null
                    var maxD = 0f
                    // v39: SCROLL: the pointer goes to the finger first, so the window under it is the one that turns
                    if (scrollMode && !viewOnly) { val (sx, sy) = toFb(start); mouseAt = sx to sy; rfb.pointer(sx, sy, 0) }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size > fingers) fingers = pressed.size
                        if (pressed.size >= 2) multiTravel += pressed.maxOf { it.positionChange().getDistance() }
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
                        maxD = maxOf(maxD, (p - start).getDistance())
                        if (!moved && (p - start).getDistance() > 24f) moved = true
                        if (multi) { c.consume(); continue }
                        if (viewOnly) {
                            // watching only: one finger moves the zoomed picture, nothing goes to the Mac
                            pan = clampPan(pan + c.positionChange())
                            c.consume(); continue
                        }
                        if (mouse) {
                            // MOUSE: the pointer follows the finger, no button; not in the first 80 ms, so the first finger of
                            // a two- or three-finger tap leaves the pointer where it is
                            if (held != 0 || clickLock != 0) {
                                // v41/42: a button is locked down: the finger moves the pointer RELATIVELY, no jump to the finger
                                val cur = relAt ?: mouseAt
                                if (cur != null && System.currentTimeMillis() - t0 > 80) {
                                    val d = c.positionChange() / scale()
                                    val nx = (cur.first + d.x.roundToInt()).coerceIn(rect[0], rect[0] + rect[2] - 1)
                                    val ny = (cur.second + d.y.roundToInt()).coerceIn(rect[1], rect[1] + rect[3] - 1)
                                    relAt = nx to ny; mouseAt = nx to ny; rfb.pointer(nx, ny, held)   // clickLock: held is 0, only the ring moves
                                }
                            }
                            else if (moved || System.currentTimeMillis() - t0 > 80) { val at = toFb(p); mouseAt = at; rfb.pointer(at.first, at.second, 0) }
                        } else if (scrollMode) {
                            // one finger in SCROLL: up and down turn the Mac's wheel
                            scrollAcc += c.positionChange().y
                            val (fx, fy) = toFb(p)      // v39: at the finger, so the window under it turns
                            mouseAt = fx to fy
                            while (abs(scrollAcc) > 20f) {
                                val button = if (scrollAcc > 0) 8 else 16   // finger down = content down = wheel up
                                rfb.pointer(fx, fy, button); Thread.sleep(4); rfb.pointer(fx, fy, 0)
                                scrollAcc -= 20f * kotlin.math.sign(scrollAcc)
                            }
                        } else if (lx && fitView && !dragging && moved) {
                            pan = clampPan(pan + c.positionChange())   // v26 FIT: the finger moves the picture
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
                    if (mouse) {
                        // MOUSE: a short tap of three fingers clicks, of two fingers right-clicks, where the pointer is
                        if (held != 0 && !multi) return@awaitEachGesture   // D drag: lifting or tapping does nothing
                        if (clickLock != 0 && !multi) {
                            // v42: a tap (under 250 ms, under 10 px) = one click of the lit button at the ring, no jump
                            if (System.currentTimeMillis() - t0 < 250 && maxD < 10f) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                mouseClick(clickLock)
                            }
                            return@awaitEachGesture
                        }
                        if (!multi) { val to = toFb(last); mouseAt = to; rfb.pointer(to.first, to.second, 0); return@awaitEachGesture }
                        val at = mouseAt
                        if (at != null && System.currentTimeMillis() - t0 < 400 && multiTravel < 60f) {
                            val button = if (fingers >= 3) 1 else 4
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            mouseClick(button)
                        }
                        return@awaitEachGesture
                    }
                    if (!lx && !multi && !dragging && quick && zoom <= 1.01f && abs(dx) > 150f && abs(dx) > 2 * abs(dy)) {
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
                // MOUSE: a ring where the Mac's pointer stands, so the finger sees where the click will land
                val at = mouseAt
                if (mouseMode && at != null) {
                    val c = Offset(baseX() + pan.x + (at.first - rect[0]) * scale(), baseY() + pan.y + (at.second - rect[1]) * scale())
                    drawCircle(Color.Black, 11f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
                    drawCircle(Amber, 11f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
                }
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
        "%5d kbit/s".format(kbps),
        "%03d ms".format(s.lastMs.coerceIn(0, 999)),          // three digits, zeros in front: 050 ms (Marko, 26.10.2026)
    ) }
    // the top line: the data, the speed, the time, and since 26.10.2026 the colours and the frames per second (Marko:
    // "add at the top of the screen the status of colors and the status of frames per second"); the ↓ ↑ split is in ⚙
    val meter: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("VNC %6.2f MB".format((rx + tx) / 1e6), Cyan, 13, oneLine = true)
            meterTexts().drop(1).forEach { T(it, Cyan, 13, oneLine = true) }
            T("%-5s".format("C" + colours.label), Cyan, 13, oneLine = true)
            T("%3s FPS".format(FPS[fpsIndex].first), Cyan, 13, oneLine = true)
        }
    }
    // the meter on the screen, or only in ⚙ (Marko, 26.10.2026: "I can disable this speed status line")
    var showMeter by remember { mutableStateOf(s.prefs?.getBoolean("meter_on", true) ?: true) }
    // v28 the pure ⌘ on the Linux machine: Super pressed and let go alone, which opens the VM's dock (chromium-vm-dock
    // watches for a lone Super_L, the same as the Mac viewer's lone ⌘)
    fun cmdAlone() {
        mods.value = emptySet()
        travelling("⌘")
        rfb.key(K_CMD, true); rfb.key(K_CMD, false); rfb.probe()
    }
    var settingsOpen by remember { mutableStateOf(false) }
    // v17: each of the three rows can be switched off in ⚙ (Marko, 27.10.2026: "In settings I can disable all the rows")
    var rowKeys by remember { mutableStateOf(s.prefs?.getBoolean(ROW_KEYS, true) ?: true) }
    var rowTools by remember { mutableStateOf(s.prefs?.getBoolean(ROW_TOOLS, true) ?: true) }
    var rowViews by remember { mutableStateOf(s.prefs?.getBoolean(ROW_VIEWS, true) ?: true) }
    val modKeys: @Composable () -> Unit = {
        // as on a MacBook Pro's bottom row: control, option, command; shift after (Marko, 25.10.2026)
        modList.forEach { (label, k, name) ->
            Key(label, Modifier.width(56.dp), active = modOn(k), size = 20, sub = name) { toggleMod(k) }
        }
    }
    // v26 (Marko, 5.11.2026: "Escape should be the first button on the left side, and then Control Alt Shift ... Remove
    // Shift", "a dedicated button which will be Command Tab to switch between applications")
    val specialKeys = listOf("TAB" to K_TAB, "⏎" to K_RETURN, "⌫" to K_BACKSPACE,
        "←" to K_LEFT, "↑" to K_UP, "↓" to K_DOWN, "→" to K_RIGHT)
    val keysRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Key("ESC", Modifier.width(60.dp), size = 18, padH = 10) { tapKey(K_ESC) }
            modKeys()
            // v28 (Marko, 5.11.2026: "on action row at the top i need to have command only, pure command button"):
            // ⌘TAB is gone; the Mac has its ⌘ toggle in modKeys, the Linux machine gets a pure ⌘ (Super alone = its dock)
            if (lx) Key("⌘", Modifier.width(56.dp), size = 20, sub = "command") { cmdAlone() }
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
        // FPS and the colours live in ⚙ (Marko, 26.10.2026: "number of colors and FPS can be moved to the settings")
        // v17: narrow inner margins (ROW_PAD), so the keys take less room (Marko, 27.10.2026)
        Key("SCROLL", modifier, active = scrollMode, size = 14, padH = ROW_PAD) { scrollMode = !scrollMode; if (scrollMode) mouseMode = false }
        Key("⌨", modifier, active = keyboard, size = 26, padH = ROW_PAD) { keyboard = !keyboard }
        // v17: 🖱 beside ⌨ (Marko, 27.10.2026: "a keyboard icon ... and next to it there must be a mouse icon")
        Key("🖱", modifier, active = mouseMode, size = 24, padH = ROW_PAD) { setMouse(!mouseMode) }
        if (mouseMode) {
            Key("L", Modifier.weight(0.6f), active = clickLock == 1, size = 18, sub = if (clickLock == 1) "TAP=L" else "CLICK", padH = 2, onLongClick = { toggleLock(1) }) { if (clickLock == 1) toggleLock(1) else mouseClick(1) }
            Key("R", Modifier.weight(0.6f), active = clickLock == 4, size = 18, sub = if (clickLock == 4) "TAP=R" else "CLICK", padH = 2, onLongClick = { toggleLock(4) }) { if (clickLock == 4) toggleLock(4) else mouseClick(4) }
            Key("D", Modifier.weight(0.6f), active = dragHeld, size = 18, sub = "DRAG", padH = 2) { toggleDrag() }
        }
    }
    // v17: ⚙ at the right end of the last row that is shown (Marko, 27.10.2026: "the settings gear icon should move to
    // the last row of action buttons, on the right side")
    val gear: @Composable RowScope.() -> Unit = { Key("⚙", Modifier.weight(0.6f), size = 22, padH = ROW_PAD) { settingsOpen = true } }
    // v21 FIT: the Linux machine takes the size of the phone's picture, in dp (Chrome at its own scale, upright);
    // the second tap, or leaving, gives it its old size back
    fun fitVm() {
        fitView = !fitView
        zoom = if (fitView) coverZoom() else 1f
        pan = clampPan(Offset(0f, -10000f))     // the top of the picture first
        travelling(if (fitView) "FIT" else "WHOLE PICTURE", picture = true)
    }
    val views: @Composable RowScope.() -> Unit = {
        // LANDSCAPE left the row (Marko, 25.10.2026: "more space for the letters"); FULL SCREEN is the sideways view.
        // ⛶ alone, without its word, and ⇄ beside TAB ▶ switches BenQ / MacBook (Marko, 26.10.2026); widths by their words
        // v17: LOGIN moved down here, and TAB ▶ is the keyboard's own tab symbol ⇥ (Marko, 27.10.2026: "just the classic
        // tab from the keyboard"); every key with narrow inner margins
        Key("⛶", Modifier.weight(0.6f), active = true, size = 22, padH = ROW_PAD) { s.full = true }
        Key("VIEW", Modifier.weight(1f), active = viewOnly, size = 14, padH = ROW_PAD) { viewOnly = !viewOnly; if (viewOnly) mouseMode = false }   // lit = view only (Marko, 26.10.2026)
        Key("⇥", Modifier.weight(0.6f), size = 22, padH = ROW_PAD) { nextTab() }
        if (lx) Key("FIT", Modifier.weight(1f), active = fitView, size = 14, padH = ROW_PAD) { fitVm() }
        else {
            Key("⇄", Modifier.weight(0.7f), size = 22, sub = if (monitor == 0) "BENQ" else "MACBOOK", padH = 2) { monitor = 1 - monitor }
            Key("DOCK", Modifier.weight(1f), size = 14, padH = ROW_PAD, onLongClick = { dock() }) { dockHiding() }
            Key("APPS", Modifier.weight(1f), size = 14, padH = ROW_PAD, onLongClick = { appsStep(-1) }) { appsStep(1) }
            Key("LOGIN", Modifier.weight(1.05f), size = 14, padH = ROW_PAD) { login() }
            Key("☰", Modifier.weight(0.6f), size = 22, padH = ROW_PAD) { menuOn() }
        }
    }

    // ⚙: the meter, read with its words; the meter on the screen or not; ✕ quits (Marko, 26.10.2026: "quitting the app
    // is two steps: Settings and then X")
    if (settingsOpen) Dialog(onDismissRequest = { settingsOpen = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).verticalScroll(rememberScrollState()).padding(14.dp).width(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // v38 (Marko, 8.11.2026): a circle with ✕ at the top closes the dialog
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                T("── SETTINGS ──", AmberDim, 13)   // titles in the middle (Marko, 26.10.2026)
                Box(Modifier.align(Alignment.CenterEnd).size(40.dp).border(1.5.dp, Color(0xFFFFB000), CircleShape)
                    .clickable { settingsOpen = false }, contentAlignment = Alignment.Center) { T("✕", Color(0xFFFFB000), 20) }
            }
            // v40 (Marko, 8.11.2026: "update the app and back to login should be at the top"): one row under the title
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // "update the app should list current version also" (Marko, 8.11.2026)
                val current = remember { runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull() ?: "?" }
                Key("⬇ UPDATE APP", Modifier.weight(1f), size = 14, sub = "NOW v$current") {
                    activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/markoboskoauroville/MANTRA_REMOTE_VNC_TAILSCALE/releases/latest")))
                }
                Key("← BACK TO LOGIN", Modifier.weight(1f), size = 14, sub = "OTHER MACHINE") { settingsOpen = false; s.phone = null; s.hangUp() }
            }
            // in ⚙ every number starts at the left edge, padded on its right, so the units stand in one column (Marko, 26.10.2026)
            val vnc = "%-7s MB  ↓ %s  ↑ %s".format("%.2f".format((rx + tx) / 1e6), "%.2f".format(rx / 1e6), "%.2f".format(tx / 1e6))
            val speed = "%-7s kbit/s".format(kbps)
            val ms = "%-7s ms".format("%03d".format(s.lastMs.coerceIn(0, 999)))
            T(vnc, Cyan, 14, oneLine = true)   // padded, never trimmed: the digits stand still
            T("The picture and the keys of this link: all of it, ↓ from the Mac (the pictures), ↑ to the Mac (keys, clicks).", Color(0xFFF2DDB4), 12)
            T(speed, Cyan, 14, oneLine = true)   // padded, never trimmed: the digits stand still
            T("How fast it flows now, every second.", Color(0xFFF2DDB4), 12)
            T(ms, Cyan, 14, oneLine = true)   // padded, never trimmed: the digits stand still
            T("How long the last picture took to come after it was asked for.", Color(0xFFF2DDB4), 12)
            // v16 (Marko, 26.10.2026: "in the Mantra remote control settings, I want to see all the available devices
            // I can connect to ... control my computer or the Pixel 7 from my own one app, switching between them")
            T("DEVICES: a tap shows it on this screen", AmberDim, 12)
            Key((s.linux?.name ?: "THE MAC") + "  " + s.host.ifEmpty { "not connected" }, Modifier.fillMaxWidth(), active = s.phone == null, size = 13) {
                s.phone = null; settingsOpen = false
            }
            // v24: every machine of the tailnet, one tap switches to it (the link here closes first)
            var showOthers by remember { mutableStateOf(false) }
            var others by remember { mutableStateOf<List<Tailnet.Place>?>(null) }
            Key("⇄ CONNECT TO OTHER SESSION", Modifier.fillMaxWidth(), active = showOthers, size = 14) { showOthers = !showOthers }
            if (showOthers) {
                LaunchedEffect(Unit) {
                    others = null
                    others = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { s.app?.let { Tailnet.places(it, s.prefs) } ?: emptyList() }
                }
                val list = others
                if (list == null) T("> Tailscale is reading your machines …", AmberDim, 12)
                else if (list.isEmpty()) T("> no machine answers", RedAlert, 12)
                else list.forEach { pl ->
                    val now = s.isCurrent(pl)
                    Key((if (pl.online) "● " else "○ ") + pl.name + "  " + pl.ip + if (now) "  (NOW)" else "", Modifier.fillMaxWidth(), active = now, size = 12) {
                        if (!now) { settingsOpen = false; s.enter(pl) }
                    }
                }
            }
            var phones by remember { mutableStateOf(Phones.all(s.prefs)) }
            phones.forEach { p ->
                Key("${p.name}  ${p.address}", Modifier.fillMaxWidth(), active = s.phone == p, size = 13,
                    onLongClick = { Phones.remove(s.prefs, p); phones = Phones.all(s.prefs) }) { s.phone = p; settingsOpen = false }
            }
            var adding by remember { mutableStateOf("") }
            Field("ADD A PHONE: name address:port (its Tailscale address, adb on 5555); a long press on one removes it", adding) { adding = it }
            if (adding.trim().contains(' ') && adding.contains(':')) Key("ADD", Modifier.fillMaxWidth(), size = 14) {
                val name = adding.trim().substringBeforeLast(' ').uppercase(); val addr = adding.trim().substringAfterLast(' ')
                Phones.add(s.prefs, Phone(name, addr)); phones = Phones.all(s.prefs); adding = ""
            }
            T("FRAMES PER SECOND: fewer cost less data", AmberDim, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                FPS.forEachIndexed { i, (label, _) -> Key(label, Modifier.weight(1f), active = i == fpsIndex, size = 12, padH = 1) { fpsIndex = i } }
            }
            T("COLOURS: fewer cost less data (B/W about a sixth of FULL)", AmberDim, 12)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Colours.ALL.forEach { c -> Key(c.label, Modifier.weight(1f), active = c.label == colours.label, size = 11, padH = 1) { setColours(c) } }
            }
            Key(if (showMeter) "STATUS LINE: ON" else "STATUS LINE: OFF", Modifier.fillMaxWidth(), active = showMeter, size = 14) {
                showMeter = !showMeter; s.prefs?.edit()?.putBoolean("meter_on", showMeter)?.apply()
            }
            // v17: the rows of keys, each on or off; with all three off ⚙ floats over the picture, to bring them back
            T("ROWS OF KEYS: with all off, ⚙ stays over the picture", AmberDim, 12)
            Key(if (rowKeys) "ROW 1 KEYS: ON" else "ROW 1 KEYS: OFF", Modifier.fillMaxWidth(), active = rowKeys, size = 14) {
                rowKeys = !rowKeys; s.prefs?.edit()?.putBoolean(ROW_KEYS, rowKeys)?.apply()
            }
            Key(if (rowTools) "ROW 2 TOOLS: ON" else "ROW 2 TOOLS: OFF", Modifier.fillMaxWidth(), active = rowTools, size = 14) {
                rowTools = !rowTools; s.prefs?.edit()?.putBoolean(ROW_TOOLS, rowTools)?.apply()
            }
            Key(if (rowViews) "ROW 3 VIEWS: ON" else "ROW 3 VIEWS: OFF", Modifier.fillMaxWidth(), active = rowViews, size = 14) {
                rowViews = !rowViews; s.prefs?.edit()?.putBoolean(ROW_VIEWS, rowViews)?.apply()
            }
        }
    }

    // a long press on FPS: every rate at once, one tap chooses (Marko, 25.10.2026)
    if (fpsMenu) Dialog(onDismissRequest = { fpsMenu = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { T("── FRAMES PER SECOND ──", AmberDim, 13) }   // titles in the middle (Marko, 26.10.2026)
            T("How often a new picture of the Mac is fetched. Fewer is cheaper on mobile data.", Color(0xFFF2DDB4), 13, modifier = Modifier.width(220.dp))
            FPS.forEachIndexed { i, (label, _) ->
                Key("$label FPS", Modifier.width(220.dp), active = i == fpsIndex, size = 18) { fpsIndex = i; fpsMenu = false }
            }
        }
    }

    if (coloursMenu) Dialog(onDismissRequest = { coloursMenu = false }) {
        Column(Modifier.background(Bg).border(1.dp, Rule).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { T("── COLOURS ──", AmberDim, 13) }   // titles in the middle (Marko, 26.10.2026)
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
                if (!lx) RailKey("LOGIN", null, if (s.loginPassword.isEmpty()) RailState.DEAD else RailState.OFF) { login() }
                modList.forEach { (label, k, name) ->
                    RailKey(label, name, if (k == K_CMD && cmdHeld) RailState.ON else if (modOn(k)) RailState.ARMED else RailState.OFF) { toggleMod(k) }
                }
                if (lx) RailKey("⌘", "command", RailState.OFF) { cmdAlone() }
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
                // v18: one key toggles the monitors, its label the one on screen (Marko, 2.11.2026: "I need only one
                // button which toggles the monitors"); with more than two it steps to the next
                if (lx) RailKey("FIT", null, if (fitView) RailState.ON else RailState.OFF) { fitVm() }
                else RailKey("MON ${monitor + 1}", MONITORS[monitor].name.substringAfter(' '), RailState.ON) { monitor = (monitor + 1) % MONITORS.size }
                RailKey("KEYS", null, if (keyboard) RailState.ON else RailState.OFF) { keyboard = !keyboard }
                RailKey("MOUSE", null, if (mouseMode) RailState.ON else RailState.OFF) { setMouse(!mouseMode) }
                if (mouseMode) {
                    RailKey("L", if (clickLock == 1) "TAP=L" else "CLICK", if (clickLock == 1) RailState.ON else RailState.OFF, onLongClick = { toggleLock(1) }) { if (clickLock == 1) toggleLock(1) else mouseClick(1) }
                    RailKey("R", if (clickLock == 4) "TAP=R" else "CLICK", if (clickLock == 4) RailState.ON else RailState.OFF, onLongClick = { toggleLock(4) }) { if (clickLock == 4) toggleLock(4) else mouseClick(4) }
                    RailKey("D", "DRAG", if (dragHeld) RailState.ON else RailState.OFF) { toggleDrag() }
                }
                RailKey("NEXT", "TAB", RailState.OFF) { nextTab() }
                if (!lx) RailKey("DOCK", null, RailState.OFF, onLongClick = { dock() }) { dockHiding() }
                if (!lx) RailKey("APPS", null, RailState.OFF, onLongClick = { appsStep(-1) }) { appsStep(1) }
                RailKey("SCROLL", null, if (scrollMode) RailState.ON else RailState.OFF) { scrollMode = !scrollMode }
                RailKey("FPS", FPS[fpsIndex].first, RailState.OFF, onLongClick = { fpsMenu = true }) { fpsIndex = (fpsIndex + 1) % FPS.size }
                RailKey("COL", colours.label, RailState.OFF, onLongClick = { coloursMenu = true }) {
                    setColours(Colours.ALL[(Colours.ALL.indexOfFirst { it.label == colours.label } + 1) % Colours.ALL.size])
                }
                RailKey("VIEW", "ONLY", if (viewOnly) RailState.ON else RailState.OFF) { viewOnly = !viewOnly; if (viewOnly) mouseMode = false }
                RailKey("EXIT", "FULL", RailState.OFF) { s.full = false }
            }
        }
        s.landscape -> Row(Modifier.fillMaxSize().imePadding()) {
            screen(Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (!lx) monitors(Modifier.weight(1f)); close() }
                meter()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { modKeys() }
                specialKeys.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (label, k) -> Key(label, Modifier.weight(1f), size = 18) { tapKey(k) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { toggles(Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { views(); gear() }
                typing()
            }
        }
        else -> Column(Modifier.fillMaxSize().imePadding()) {
            // MON 1 / MON 2 / ✕ left the top (Marko, 26.10.2026: "everything is down anyway"): ⇄ switches the screens,
            // ⚙ holds the meter and ✕
            if (showMeter) meter()
            val noRows = !rowKeys && !rowTools && !rowViews
            Box(Modifier.weight(1f).fillMaxWidth()) {
                screen(Modifier.fillMaxSize())
                // v17: every row off, ⚙ stays as a see-through key over the picture (Marko, 27.10.2026: "the settings
                // icon should stay as a transparent overlay over the pictures ... go back, and return my rows")
                if (noRows && !menuMode) Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp).background(Color(0x55000000), CircleShape)
                        .border(1.dp, Color(0x66FFB000), CircleShape).clickable { settingsOpen = true },
                    contentAlignment = Alignment.Center,
                ) { T("⚙", Color(0xAAFFB000), 24, bold = true, oneLine = true) }
            }
            travelLine(Modifier.fillMaxWidth())
            if (menuMode) menuPanel() else {
                // ⚙ goes to the right end of whichever row is the last one shown
                if (rowKeys) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { keysRow() }
                    if (!rowTools && !rowViews) Row(Modifier.width(52.dp).padding(end = 4.dp)) { gear() }
                }
                if (rowTools) Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    toggles(Modifier.weight(1f)); if (!rowViews) gear()
                }
                if (rowViews) Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) { views(); gear() }
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { T("── DOCK ──", AmberDim, 14) }
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
    MacroButton(word, onClick)
    var flash by remember { mutableStateOf(0) }
    LaunchedEffect(flash) { if (flash > 0) { kotlinx.coroutines.delay(PRESS_FLASH_MS); flash = 0 } }
    val lit = flash > 0
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = { flash++; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); (onLongClick ?: { helpFor.value = word })() },
            ) { if (state != RailState.DEAD) { flash++; view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val size = if (word.length <= 5) 13 else (13 * 5 / word.length).coerceAtLeast(6)
        androidx.compose.material3.Text(
            word, color = if (lit) Color.White else state.color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp,
            letterSpacing = 0.04.em, maxLines = 1, softWrap = false,
        )
        if (whisper != null) androidx.compose.material3.Text(
            whisper, color = if (lit) Color.White else state.color.copy(alpha = 150 / 255f), fontFamily = Mono, fontSize = 9.sp,
            maxLines = 1, softWrap = false,
        )
    }
}

/**
 * The phone's own keyboard. The field always holds one space; what is typed after it is sent, a delete that eats
 * the space is a backspace, and the field goes back to its one space.
 */
@Composable
internal fun TypingField(onText: (String) -> Unit, onBackspace: () -> Unit) {
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

/** v35: the view and the Dock's hiding as they were before DOCK moved them. */
data class DockBefore(val zoom: Float, val pan: Offset, val mouse: Pair<Int, Int>?, val hidden: Boolean, val monitor: Int)
