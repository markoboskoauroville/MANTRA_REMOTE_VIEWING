package com.mantra.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Mac's common Star API (STAR_API, `star serve`, port 8863, X-Mantra-Token from Tailscale). v47 uses it for LOGIN
 * (Marko, 13.11.2026: "It needs to communicate with local accessibility ... When I click login, it searches, even the
 * dialog does not need to be in focus ... enter my password and always allow. The first option is always allow if there
 * is there, if not then just OK"): POST /login-dialog {"password"} → Macro Recorder v23 finds every password dialog
 * on the Mac through Accessibility, fills it and presses ALWAYS ALLOW, else OK.
 */
object StarApi {
    const val PORT = 8863

    /** ok: every dialog closed; found: false when the Mac saw no password dialog (or did not answer: then null). */
    data class Login(val ok: Boolean, val found: Boolean, val message: String)

    fun loginDialog(host: String, token: String, password: String): Login? = runCatching {
        val c = URL("http://$host:$PORT/login-dialog").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 3000; c.readTimeout = 70000; c.useCaches = false
        c.setRequestProperty("Content-Type", "application/json")
        if (token.isNotEmpty()) c.setRequestProperty("X-Mantra-Token", token)
        try {
            c.outputStream.use { it.write(JSONObject().put("password", password).toString().toByteArray()) }
            if (c.responseCode != 200) {
                android.util.Log.w("MantraRemote", "star api login-dialog: HTTP ${c.responseCode}")
                return@runCatching null
            }
            val o = JSONObject(c.inputStream.readBytes().toString(Charsets.UTF_8))
            Login(o.optBoolean("ok"), o.optBoolean("found", true), o.optString("message"))
        } finally { c.disconnect() }
    }.onFailure { android.util.Log.w("MantraRemote", "star api $host login-dialog: $it") }.getOrNull()

    /** The Mac's answer in a few words for the line above the keys. */
    fun summary(r: Login): String {
        val lines = r.message.lines().filter { it.isNotBlank() }
        if (!r.ok) return "LOGIN: " + (lines.firstOrNull { "STILL OPEN" in it } ?: lines.firstOrNull() ?: "THE MAC SAID NO").uppercase().take(70)
        val word = lines.firstOrNull()?.substringAfter("pressed ", "")?.substringBefore(" (")?.uppercase().orEmpty().ifEmpty { "DONE" }
        return "LOGIN: $word PRESSED" + if (lines.size > 1) " · ${lines.size} DIALOGS" else ""
    }
}
