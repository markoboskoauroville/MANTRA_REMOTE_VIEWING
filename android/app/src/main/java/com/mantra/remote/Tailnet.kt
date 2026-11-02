package com.mantra.remote

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * v18 (Marko, 2.11.2026: "when my remote control is open, I want to see all session available ... with one button I
 * want to log in into that session. Everything is listed at the top. Right now I have only Pixel phone ... when also
 * my macOS is online through this application called Tailscale"): the places at the top of the first screen.
 *
 * Why only the Pixel showed: the app never read the tailnet. Its list was the phones saved in the app (only the Pixel 7
 * from the start), and the Mac was not in it at all, only the form below.
 *
 * Now: with a Tailscale OAuth client (devices:read, made by Marko in the admin console, kept encrypted on this phone
 * only) every device of his tailnet is listed, the Oracle machine too once it joins. Without one, the places the app
 * already knows (the Mac, the saved phones). Either way each one is tried on its own port, so "online" means "answers".
 */
object Tailnet {
    enum class Kind { MAC, PHONE, LINUX, OTHER }
    data class Place(val name: String, val ip: String, val os: String, val kind: Kind, val online: Boolean)

    const val ID = "tailscale-oauth-id"
    const val SECRET = "tailscale-oauth-secret"

    fun port(k: Kind) = when (k) { Kind.MAC -> 5900; Kind.PHONE -> 5555; Kind.LINUX -> 22; Kind.OTHER -> 22 }

    private fun kindOf(os: String) = when (os.lowercase()) {
        "macos" -> Kind.MAC; "android" -> Kind.PHONE; "linux" -> Kind.LINUX; else -> Kind.OTHER
    }

    /** This phone's own Tailscale addresses (100.64.0.0/10), so it does not list itself. */
    private fun own(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }.filter { it.startsWith("100.") }.toSet()
    }.getOrDefault(emptySet())

    private fun answers(ip: String, port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress(ip, port), 1500) }; true
    }.getOrDefault(false)

    /** The devices of the tailnet by the API; null without a key or when the API does not answer. */
    private fun fromApi(prefs: SharedPreferences?): List<Triple<String, String, String>>? {
        val id = Secrets.pin(prefs, ID); val secret = Secrets.pin(prefs, SECRET)
        if (id.isEmpty() || secret.isEmpty()) return null
        return runCatching {
            val t = URL("https://api.tailscale.com/api/v2/oauth/token").openConnection() as HttpURLConnection
            t.requestMethod = "POST"; t.doOutput = true; t.connectTimeout = 4000; t.readTimeout = 6000
            t.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            t.outputStream.use { it.write(("client_id=" + URLEncoder.encode(id, "UTF-8") + "&client_secret=" + URLEncoder.encode(secret, "UTF-8")).toByteArray()) }
            val token = JSONObject(t.inputStream.bufferedReader().readText()).getString("access_token")
            val d = URL("https://api.tailscale.com/api/v2/tailnet/-/devices").openConnection() as HttpURLConnection
            d.connectTimeout = 4000; d.readTimeout = 6000; d.setRequestProperty("Authorization", "Bearer $token")
            val arr = JSONObject(d.inputStream.bufferedReader().readText()).getJSONArray("devices")
            (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                val ip = o.optJSONArray("addresses")?.let { a -> (0 until a.length()).map { a.getString(it) }.firstOrNull { it.startsWith("100.") } } ?: return@mapNotNull null
                // seen by Tailscale within the last 10 minutes: worth trying
                val seen = runCatching { Instant.parse(o.optString("lastSeen")) }.getOrNull()
                val connected = o.optBoolean("connectedToControl", seen == null || seen.isAfter(Instant.now().minusSeconds(600)))
                if (!connected) return@mapNotNull null
                Triple(o.optString("hostname").ifEmpty { o.optString("name").substringBefore('.') }, ip, o.optString("os"))
            }
        }.onFailure { android.util.Log.w("MantraRemote", "tailnet api: $it") }.getOrNull()
    }

    /** Every place, tried in parallel; the ones that answer first in the list. Runs off the main thread. */
    fun places(prefs: SharedPreferences?): List<Place> {
        val me = own()
        val api = fromApi(prefs)
        // v19: the Oracle machine (chromium-vm, Marko 2.11.2026) is known even before the OAuth client is pasted
        val known = api ?: (listOf(Triple("macbook-pro", MacFinder.MAC_TAILSCALE_IP, "macOS"), Triple("chromium-vm", "100.102.86.30", "linux")) +
            Phones.all(prefs).map { Triple(it.name, it.address.substringBefore(':'), "android") })
        val pool = Executors.newFixedThreadPool(8)
        val list = known.filter { it.second !in me }.distinctBy { it.second }.map { (name, ip, os) ->
            pool.submit<Place> { val k = kindOf(os); Place(name.uppercase(), ip, os, k, answers(ip, port(k))) }
        }.mapNotNull { runCatching { it.get(5, TimeUnit.SECONDS) }.getOrNull() }
        pool.shutdownNow()
        return list.sortedWith(compareBy({ !it.online }, { it.kind.ordinal }, { it.name }))
    }

    fun keyed(prefs: SharedPreferences?) = Secrets.pin(prefs, ID).isNotEmpty() && Secrets.pin(prefs, SECRET).isNotEmpty()
}
