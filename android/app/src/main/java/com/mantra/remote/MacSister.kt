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

    /** Switch to the app, or start it: true when the Mac did it. */
    fun open(host: String, id: String): Boolean = get(host, "/open?id=" + URLEncoder.encode(id, "UTF-8"), 4000) != null
}
