package com.mantra.remote

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The Mac's half of the phone's dock and of the landing line: the Dock Switcher (Mantra) listens on :8861 (its
 * Remote.swift). It knows the Dock's apps, opens or switches them as its square does, and counts every key the Mac sees,
 * so the phone learns the moment its key landed (Marko, 26.10.2026: "You need to have some sister app on my SwiftStar
 * menu to communicate with this device").
 */
object MacSister {
    const val PORT = 8861

    data class DockApp(val id: String, val name: String, val running: Boolean, val front: Boolean)

    private fun get(host: String, path: String, timeoutMs: Int): ByteArray? = runCatching {
        val c = URL("http://$host:$PORT$path").openConnection() as HttpURLConnection
        c.connectTimeout = 2000; c.readTimeout = timeoutMs; c.useCaches = false
        try { if (c.responseCode == 200) c.inputStream.readBytes() else null } finally { c.disconnect() }
    }.onFailure { android.util.Log.w("MantraRemote", "sister $host$path: $it") }.getOrNull()

    /** The number of keys the Mac has seen, answered as soon as it passes [after] (or after 3 s); null: no answer. */
    fun keys(host: String, after: Long): Long? =
        get(host, "/keys?after=$after", 5000)?.let { runCatching { JSONObject(String(it)).getLong("keys") }.getOrNull() }

    fun apps(host: String): List<DockApp>? = get(host, "/apps", 4000)?.let { bytes ->
        runCatching {
            val a = JSONObject(String(bytes)).getJSONArray("apps")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                DockApp(o.getString("id"), o.getString("name"), o.optBoolean("running"), o.optBoolean("front"))
            }
        }.getOrNull()
    }

    fun icon(host: String, id: String): ImageBitmap? = get(host, "/icon?id=" + URLEncoder.encode(id, "UTF-8"), 4000)?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
    }

    /** The front app's menu bar opened at its own menu, by Accessibility on the Mac (no ⌃F2: it is off there). */
    fun menu(host: String): Boolean = get(host, "/menu", 3000) != null
    /** Every open menu closed (AXCancel), so EXIT never needs an ESC that could land in the app. */
    fun menuClose(host: String): Boolean = get(host, "/menuclose", 3000) != null

    /**
     * v16 (Marko, 26.10.2026: "Yes, build that"): the phone of that name on the Mac's USB cable unlocked by the Mac
     * with its PIN, its adb on 5555 armed again. The PIN goes in the body (through Tailscale), never in the address.
     * (ok, the Mac's words), or null when the Mac does not answer.
     */
    fun unlockPhone(host: String, name: String, pin: String): Pair<Boolean, String>? = runCatching {
        val c = URL("http://$host:$PORT/unlock").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 4000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "name=" + URLEncoder.encode(name, "UTF-8") + "&pin=" + URLEncoder.encode(pin, "UTF-8")
        c.outputStream.use { it.write(body.toByteArray()) }
        val text = (if (c.responseCode < 400) c.inputStream else c.errorStream).use { String(it.readBytes()) }
        val j = JSONObject(text)
        j.optBoolean("ok") to j.optString("note")
    }.getOrNull()

    /**
     * v35 (Marko, 8.11.2026: "dock button actually unhide the dock"): the Dock's own hiding set on the Mac (show true:
     * stays shown; false: hides itself again; null: only asked), and where its bar stands, as fractions of its screen.
     */
    data class DockState(val hidden: Boolean, val x: Double, val y: Double, val w: Double, val h: Double,
                         /** v36: the bar in the VNC picture's pixels, and the picture's size by the Mac's rule (0: unknown) */
                         val fx: Double = 0.0, val fy: Double = 0.0, val fw: Double = 0.0, val fh: Double = 0.0,
                         val tw: Double = 0.0, val th: Double = 0.0)
    fun dock(host: String, show: Boolean?): DockState? =
        get(host, "/dock" + (show?.let { if (it) "?show=1" else "?show=0" } ?: ""), 4000)?.let { b ->
            runCatching {
                val j = JSONObject(String(b))
                if (!j.optBoolean("ok")) null
                else DockState(j.optBoolean("hidden"), j.optDouble("x", 0.25), j.optDouble("y", 0.94), j.optDouble("w", 0.5), j.optDouble("h", 0.06),
                    j.optDouble("fx", 0.0), j.optDouble("fy", 0.0), j.optDouble("fw", 0.0), j.optDouble("fh", 0.0),
                    j.optDouble("tw", 0.0), j.optDouble("th", 0.0))
            }.getOrNull()
        }

    /** Switch to the app, or start it: true when the Mac did it. */
    fun open(host: String, id: String): Boolean = get(host, "/open?id=" + URLEncoder.encode(id, "UTF-8"), 4000) != null
}
