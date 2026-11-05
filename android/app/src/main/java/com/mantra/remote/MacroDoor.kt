package com.mantra.remote

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * v30, THE MACRO DOOR (Marko, 5.11.2026: "This macro language needs to be built into my remote controller plus API, a
 * Python API to control it remotely ... There is only one command to call to make multiple actions, so we save on our
 * credits").
 *
 * The language is the Macro Recorder's (MANTRA_MACRO docs/SCRIPT.md, AutoHotkey v2), the part that makes sense on a
 * remote screen, plus three words for the app itself:
 *
 *     ; a comment
 *     Click 640, 400            Click 640, 400, 2      Click 640, 400, "Right"
 *     MouseMove 640, 400        MouseClickDrag "Left", 10, 10, 200, 200
 *     Send "^c"  Send "#{Tab}"  Send "Hello{Enter}"  Send "{WheelDown 3}"  Send "{Cmd down}"  Send "{Cmd up}"
 *     SendText "typed exactly"
 *     Sleep 500
 *     Loop 3 {
 *         Send "{Down}"
 *     }
 *     Press "ESC"               ; any button of this app, by the word on it (machines by their name: Press "CHROMIUM-VM")
 *     Back                      ; leaves the screen, as the phone's back gesture
 *     Log "text"                ; into the answer
 *
 * Coordinates are the remote screen's own pixels (the VM: 2560 x 1440). ^ Control, ! Alt/Option, + Shift, # Command
 * (Super on the VM). Click/Send need an open VNC session; Press works on every screen.
 *
 * The door: HTTP on port 8841, answered only from Tailscale (100.64.0.0/10) and this phone itself (adb forward):
 *     GET  /health, GET /api  the manifest's door (modules/remote-door.md); X-Mantra-Token from Tailscale
 *     GET  /api/state        what is on screen, the machine, the remote size, every button's word
 *     POST /api/run          the body is a script; the answer: ok, every line done, the error, the time
 *     GET  /api/shot?w=360   the app's screen as a small PNG (one look instead of many)
 * The Python side is tools/mremote.py.
 */
interface MacroTarget {
    /** Is a remote screen open (VNC)? */
    fun live(): Boolean
    fun key(keysym: Int, down: Boolean)
    fun pointer(x: Int, y: Int, buttons: Int)
    fun size(): Pair<Int, Int>
    fun describe(): JSONObject
    fun back()
}

object MacroDoor {
    const val PORT = 8841
    /** Every button on screen, by its word: registered by Key, RailKey and Machine while they are shown. */
    val buttons = ConcurrentHashMap<String, () -> Unit>()
    @Volatile var target: MacroTarget? = null
    private var activity = WeakReference<Activity>(null)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var started = false

    @Volatile private var app: android.content.Context? = null

    fun attach(a: Activity, t: MacroTarget) {
        activity = WeakReference(a); target = t
        open(a.applicationContext)
    }

    /** The door itself; Press, Back and /shot need the app's window, /state answers without it. */
    @Synchronized
    fun open(c: android.content.Context) {
        app = c.applicationContext
        if (started) return
        started = true
        thread(name = "macro-door", isDaemon = true) {
            while (true) {
                runCatching {
                    ServerSocket(PORT, 8, InetAddress.getByName("0.0.0.0")).use { server ->
                        while (true) {
                            val c = server.accept()
                            thread(name = "macro-req", isDaemon = true) { runCatching { serve(c) }; runCatching { c.close() } }
                        }
                    }
                }
                Thread.sleep(3000)
            }
        }
    }

    private fun allowed(a: InetAddress): Boolean {
        if (a.isLoopbackAddress) return true
        if (a !is Inet4Address) return false
        val b = a.address
        return (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64     // 100.64.0.0/10, Tailscale
    }

    private fun serve(c: Socket) {
        c.soTimeout = 15000
        val inp = c.getInputStream().buffered()
        val out = c.getOutputStream()
        if (!allowed(c.inetAddress)) { reply(out, 403, "text/plain", "only Tailscale".toByteArray()); return }
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) { val ch = inp.read(); if (ch < 0) return; head.append(ch.toChar()) }
        val lines = head.lines()
        val (method, path) = lines[0].split(' ').let { it[0] to it.getOrElse(1) { "/" } }
        val len = lines.firstOrNull { it.lowercase().startsWith("content-length:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        val body = ByteArray(len).also { var n = 0; while (n < len) { val r = inp.read(it, n, len - n); if (r < 0) break; n += r } }
        val route = path.substringBefore('?')
        val query = path.substringAfter('?', "").split('&').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        // v33: the manifest's door (modules/remote-door.md): X-Mantra-Token on every request from Tailscale (the same token
        // as the Mac's Mantra Door, put into the app by `mremote keep`); this phone itself (adb forward) needs none
        if (!c.inetAddress.isLoopbackAddress) {
            val sent = lines.firstOrNull { it.lowercase().startsWith("x-mantra-token:") }?.substringAfter(':')?.trim() ?: ""
            val mine = token()
            if (mine.isEmpty() || sent != mine) { reply(out, 401, "application/json", """{"ok": false, "error": "token missing or wrong: run mremote keep"}""".toByteArray()); return }
        }
        val r = route.removePrefix("/api").ifEmpty { "/api" }
        when {
            route == "/health" -> reply(out, 200, "application/json", JSONObject().put("app", "mantra-remote").put("name", "Mantra Remote VNC")
                .put("version", BuildConfig.VERSION_NAME.toIntOrNull() ?: 0).put("pid", android.os.Process.myPid()).toString().toByteArray())
            route == "/api" -> reply(out, 200, "application/json", JSONObject().put("app", "mantra-remote").put("version", BuildConfig.VERSION_NAME)
                .put("actions", JSONArray()
                    .put(JSONObject().put("name", "run").put("does", "runs a macro (Macro Recorder language), many actions in one call").put("args", JSONObject().put("script", "text")))
                    .put(JSONObject().put("name", "state").put("does", "what is on screen, the buttons, the debugging switches").put("args", JSONObject()))
                    .put(JSONObject().put("name", "shot").put("does", "the app's screen as a small PNG").put("args", JSONObject().put("w", "int")))).toString(1).toByteArray())
            r == "/state" -> reply(out, 200, "application/json", state().toString(1).toByteArray())
            r == "/run" && method == "POST" -> {
                val text = String(body).let { b -> if (b.trimStart().startsWith("{")) runCatching { JSONObject(b).getString("script") }.getOrDefault(b) else b }
                reply(out, 200, "application/json", run(text).toString(1).toByteArray())
            }
            r == "/shot" -> shot(query["w"]?.toIntOrNull() ?: 360)?.let { reply(out, 200, "image/png", it) }
                ?: reply(out, 500, "text/plain", "no picture".toByteArray())
            else -> reply(out, 404, "text/plain", "GET /health, GET /api, GET /api/state, POST /api/run, GET /api/shot?w=360".toByteArray())
        }
    }

    /** The door's token: files/door.token, put there by `mremote keep` (run-as); never shown, never logged. */
    private fun token(): String = runCatching { java.io.File(app!!.filesDir, "door.token").readText().trim() }.getOrDefault("")

    private fun reply(out: java.io.OutputStream, code: Int, type: String, data: ByteArray) {
        out.write("HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(data); out.flush()
    }

    fun state(): JSONObject = (target?.describe() ?: JSONObject()).apply {
        put("version", BuildConfig.VERSION_NAME)
        put("buttons", JSONArray(buttons.keys.sorted()))
        put("window", activity.get() != null)
        put("debug", DebugKeeper.status(app ?: return@apply))
    }

    private fun onMain(block: () -> Unit) {
        val done = CountDownLatch(1)
        main.post { try { block() } finally { done.countDown() } }
        done.await(5, TimeUnit.SECONDS)
    }

    /** The app's whole window as the eye sees it (the VNC picture and the phone picture included), small. */
    fun shot(w: Int): ByteArray? {
        val a = activity.get() ?: return null
        val view = a.window.decorView
        if (view.width == 0) return null
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1); var ok = false
        main.post { PixelCopy.request(a.window, full, { r -> ok = r == PixelCopy.SUCCESS; done.countDown() }, main) }
        done.await(5, TimeUnit.SECONDS)
        if (!ok) return null
        val small = Bitmap.createScaledBitmap(full, w, full.height * w / full.width, true)
        return ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    // ---------------------------------------------------------------- the interpreter

    private class Stop(msg: String) : Exception(msg)

    fun run(script: String): JSONObject {
        val t0 = System.currentTimeMillis()
        val log = JSONArray()
        val res = JSONObject()
        try {
            exec(script.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith(";") }, log)
            res.put("ok", true)
        } catch (e: Exception) {
            res.put("ok", false); res.put("error", e.message ?: e.javaClass.simpleName)
        }
        return res.put("done", log).put("ms", System.currentTimeMillis() - t0)
    }

    private fun exec(lines: List<String>, log: JSONArray) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].substringBefore(" ;").trim()
            val m = Regex("""^Loop\s+(\d+)\s*\{?$""", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                // the block: up to the matching }
                var depth = if (line.endsWith("{")) 1 else 0
                var j = i + 1
                if (depth == 0) { if (lines.getOrNull(j) == "{") { depth = 1; j++ } else throw Stop("line ${i + 1}: Loop needs { }") }
                val start = j
                while (j < lines.size && depth > 0) {
                    if (lines[j].endsWith("{")) depth++
                    if (lines[j] == "}") depth--
                    j++
                }
                val body = lines.subList(start, j - 1)
                repeat(m.groupValues[1].toInt()) { exec(body, log) }
                i = j; continue
            }
            one(line, log)
            i++
        }
    }

    private fun args(rest: String): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var q = false; var k = 0
        while (k < rest.length) {
            val ch = rest[k]
            when {
                ch == '"' && q && rest.getOrNull(k + 1) == '"' -> { cur.append('"'); k++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out += cur.toString().trim(); cur.clear() }
                ch == '`' && q && k + 1 < rest.length -> { cur.append(when (rest[k + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> rest[k + 1] }); k++ }
                else -> cur.append(ch)
            }
            k++
        }
        if (cur.isNotEmpty() || rest.isNotBlank()) out += cur.toString().trim()
        return out
    }

    private fun live(): MacroTarget = target?.takeIf { it.live() } ?: throw Stop("no remote screen open: Press a machine first")
    private fun num(s: String, what: String) = s.toIntOrNull() ?: throw Stop("$what: a number, not \"$s\"")

    private fun one(line: String, log: JSONArray) {
        val cmd = line.substringBefore(' ').substringBefore(',').trim()
        val a = args(line.substring(cmd.length).trim().removePrefix(",").removePrefix("(").removeSuffix(")"))
        when (cmd.lowercase()) {
            "sleep" -> Thread.sleep(num(a[0], "Sleep").toLong().coerceIn(0, 120_000))
            "click", "mouseclick" -> {
                val t = live()
                val xs = if (cmd.equals("MouseClick", true)) a.drop(1) else a
                val right = (a.firstOrNull()?.equals("Right", true) == true && cmd.equals("MouseClick", true)) || xs.getOrNull(2)?.equals("Right", true) == true
                val x = num(xs[0], "Click x"); val y = num(xs[1], "Click y")
                val times = xs.getOrNull(2)?.toIntOrNull() ?: 1
                val b = if (right) 4 else 1
                t.pointer(x, y, 0)
                repeat(times) { t.pointer(x, y, b); Thread.sleep(40); t.pointer(x, y, 0); Thread.sleep(60) }
            }
            "mousemove" -> live().pointer(num(a[0], "x"), num(a[1], "y"), 0)
            "mouseclickdrag" -> {
                val t = live(); val b = if (a[0].equals("Right", true)) 4 else 1
                val x1 = num(a[1], "x1"); val y1 = num(a[2], "y1"); val x2 = num(a[3], "x2"); val y2 = num(a[4], "y2")
                t.pointer(x1, y1, 0); t.pointer(x1, y1, b)
                for (s in 1..12) { t.pointer(x1 + (x2 - x1) * s / 12, y1 + (y2 - y1) * s / 12, b); Thread.sleep(25) }
                t.pointer(x2, y2, 0)
            }
            "send" -> send(live(), a.getOrElse(0) { "" }, raw = false)
            "sendtext" -> send(live(), a.getOrElse(0) { "" }, raw = true)
            "press" -> {
                val word = a.getOrElse(0) { "" }
                val f = buttons[word] ?: buttons.entries.firstOrNull { it.key.equals(word, true) }?.value
                    ?: buttons.entries.firstOrNull { it.key.contains(word, true) }?.value
                    ?: throw Stop("Press \"$word\": no such button on screen; on screen: ${buttons.keys.sorted().joinToString()}")
                onMain(f); Thread.sleep(150)
            }
            "back" -> { onMain { target?.back() }; Thread.sleep(150) }
            // v34: pairs this app with the phone's own wireless debugging (the six digits and the port of "Pair device
            // with pairing code"), once; afterwards the keeper can bring adb on 5555 back by itself after a restart.
            // mremote pairself <phone> reads both from the dialog over USB and sends this line.
            "pairadb" -> {
                val port = num(a[0], "PairAdb port"); val code = a.getOrElse(1) { "" }
                val ok = com.mantra.remote.adb.AdbManager(app ?: throw Stop("no app")).let { m -> try { m.pair("127.0.0.1", port, code) } finally { runCatching { m.close() } } }
                if (!ok) throw Stop("pairing refused")
                app?.let { DebugKeeper.check(it) }
            }
            "log" -> {}
            else -> throw Stop("unknown command \"$cmd\"")
        }
        log.put(line)
    }

    // ---------------------------------------------------------------- Send, AutoHotkey's way

    private const val K_CTRL = 0xffe3; private const val K_ALT = 0xffe9; private const val K_SHIFT = 0xffe1; private const val K_CMD = 0xffeb
    private val NAMED = mapOf(
        "enter" to 0xff0d, "return" to 0xff0d, "tab" to 0xff09, "esc" to 0xff1b, "escape" to 0xff1b, "space" to 0x20,
        "bs" to 0xff08, "backspace" to 0xff08, "del" to 0xffff, "delete" to 0xffff, "ins" to 0xff63, "insert" to 0xff63,
        "up" to 0xff52, "down" to 0xff54, "left" to 0xff51, "right" to 0xff53, "home" to 0xff50, "end" to 0xff57,
        "pgup" to 0xff55, "pgdn" to 0xff56, "ctrl" to K_CTRL, "control" to K_CTRL, "alt" to K_ALT, "option" to K_ALT,
        "shift" to K_SHIFT, "cmd" to K_CMD, "win" to K_CMD, "lwin" to K_CMD, "command" to K_CMD,
        "!" to '!'.code, "^" to '^'.code, "+" to '+'.code, "#" to '#'.code, "{" to '{'.code, "}" to '}'.code,
    ) + (1..20).associate { "f$it" to 0xffbe + it - 1 }

    private fun tap(t: MacroTarget, ks: Int, mods: List<Int>) {
        mods.forEach { t.key(it, true) }
        t.key(ks, true); t.key(ks, false)
        mods.reversed().forEach { t.key(it, false) }
        Thread.sleep(8)
    }

    private fun charKey(t: MacroTarget, ch: Char, mods: List<Int>) {
        val ks = if (ch == '\n') 0xff0d else if (ch == '\t') 0xff09 else if (ch.code < 256) ch.code else 0x01000000 + ch.code
        val shift = ch.isUpperCase() || ch in "~!@#$%^&*()_+{}|:\"<>?"
        tap(t, ks, if (shift && K_SHIFT !in mods) mods + K_SHIFT else mods)
    }

    private fun send(t: MacroTarget, text: String, raw: Boolean) {
        if (raw) { text.forEach { charKey(t, it, emptyList()) }; return }
        var mods = mutableListOf<Int>()
        var k = 0
        while (k < text.length) {
            val ch = text[k]
            when {
                ch in "^!+#" && k + 1 < text.length -> mods += when (ch) { '^' -> K_CTRL; '!' -> K_ALT; '+' -> K_SHIFT; else -> K_CMD }
                ch == '{' -> {
                    val end = text.indexOf('}', k + 2).takeIf { it > 0 } ?: text.length
                    val inner = text.substring(k + 1, end)
                    val name = inner.substringBefore(' ').lowercase()
                    val arg = inner.substringAfter(' ', "").lowercase()
                    if (name.startsWith("wheel")) {
                        val n = arg.toIntOrNull() ?: 1
                        val (x, y) = (target?.size() ?: (0 to 0)).let { it.first / 2 to it.second / 2 }
                        val b = when (name) { "wheelup" -> 8; "wheeldown" -> 16; "wheelleft" -> 32; else -> 64 }
                        repeat(n) { t.pointer(x, y, b); t.pointer(x, y, 0); Thread.sleep(30) }
                    } else {
                        val ks = NAMED[name] ?: name.singleOrNull()?.code ?: throw Stop("Send: unknown key {$inner}")
                        when (arg) {
                            "down" -> t.key(ks, true)
                            "up" -> t.key(ks, false)
                            else -> repeat(arg.toIntOrNull() ?: 1) { tap(t, ks, mods) }
                        }
                    }
                    mods = mutableListOf(); k = end
                }
                else -> { charKey(t, ch, mods); mods = mutableListOf() }
            }
            k++
        }
    }
}
