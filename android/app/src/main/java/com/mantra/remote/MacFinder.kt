package com.mantra.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

/**
 * Finds the Mac by itself, no address typed (Marko, 26.10.2026: "all connections must be done automatically without me
 * searching for addresses"). Every way to the Mac is tried at once and the first that answers as Apple's Screen
 * Sharing ("RFB 003.889") wins:
 *  - at home, the Mac's own Wi-Fi address, found by Bonjour (_rfb._tcp, the Mac announces Screen Sharing by itself),
 *    and the home address that worked last time;
 *  - anywhere, its Tailscale address and its Tailscale name;
 *  - what is written in HOST, if anything.
 */
object MacFinder {
    /** The MacBook Pro on Tailscale (measured 26.10.2026: `tailscale status`). */
    const val MAC_TAILSCALE_IP = "100.79.251.19"
    const val MAC_TAILSCALE_NAME = "macbook-pro"

    /** The first address that answers, or null after [timeoutMs]. [say] tells the status line what is happening. */
    fun find(c: Context, port: Int, extra: List<String>, timeoutMs: Long = 25000, say: (String) -> Unit = {}): String? {
        val candidates = ConcurrentHashMap.newKeySet<String>()
        extra.filter { it.isNotBlank() }.forEach { candidates += it }
        candidates += MAC_TAILSCALE_IP
        candidates += MAC_TAILSCALE_NAME
        val bonjour = Bonjour(c) { candidates += it }
        bonjour.start()
        val pool = Executors.newCachedThreadPool()
        try {
            val until = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < until) {
                say("> LOOKING FOR THE MAC: " + candidates.joinToString(", "))
                // all at once; the first that answers wins, a slow one never holds up a quick one
                val done = ExecutorCompletionService<String?>(pool)
                val hosts = candidates.toList()
                hosts.forEach { host -> done.submit { if (answers(host, port)) host else null } }
                repeat(hosts.size) { runCatching { done.take().get() }.getOrNull()?.let { return it } }
                Thread.sleep(1000)
            }
            return null
        } finally {
            bonjour.stop(); pool.shutdownNow()
        }
    }

    /** The port opens and the server greets as RFB within two seconds. */
    private fun answers(host: String, port: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 2000)
            s.soTimeout = 2000
            val hello = ByteArray(12)
            var n = 0
            while (n < 12) { val r = s.getInputStream().read(hello, n, 12 - n); if (r < 0) break; n += r }
            String(hello, 0, n).startsWith("RFB ")
        }
    }.getOrDefault(false)

    /** Bonjour on the Wi-Fi: every Screen Sharing that announces itself, resolved to its address. */
    private class Bonjour(c: Context, val found: (String) -> Unit) : NsdManager.DiscoveryListener {
        private val nsd = c.getSystemService(NsdManager::class.java)
        private var running = false

        fun start() { runCatching { nsd.discoverServices("_rfb._tcp", NsdManager.PROTOCOL_DNS_SD, this); running = true } }
        fun stop() { if (running) runCatching { nsd.stopServiceDiscovery(this) }; running = false }

        override fun onServiceFound(info: NsdServiceInfo) {
            nsd.registerServiceInfoCallback(info, { it.run() }, object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(s: NsdServiceInfo) {
                    s.hostAddresses.filterIsInstance<java.net.Inet4Address>().forEach { found(it.hostAddress ?: return@forEach) }
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            })
        }
        override fun onServiceLost(info: NsdServiceInfo) {}
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }
}
