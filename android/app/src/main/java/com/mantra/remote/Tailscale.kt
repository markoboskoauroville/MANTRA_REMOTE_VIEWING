package com.mantra.remote

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * The phone's Tailscale, switched by the app: on when the app opens, off when it closes (Marko, 26.10.2026: "When I open
 * the app, tail scale is opening. When I close the app, tail scale is closing"). Tailscale's own receiver takes the two
 * actions; it can only switch on once Tailscale was signed in and allowed as the VPN one time by hand.
 *
 * v23 (Marko, 3.11.2026: "when I click to connect, checking Tailscale address and connecting to the address. It's
 * always refreshing when I want to connect"): every ENTER now (1) waits until Tailscale really stands, i.e. the VPN is
 * up AND this phone holds its own 100.x address, and says plainly when it does not; (2) asks Tailscale's own DNS
 * (MagicDNS, 100.100.100.100) for the machine's address of this moment, so an address that changed on the Mac's side
 * no longer leaves the app knocking at an old one.
 */
object Tailscale {
    const val PACKAGE = "com.tailscale.ipn"
    /** Tailscale's resolver inside the tunnel ("quad 100"). */
    private const val QUAD100 = "100.100.100.100"

    fun on(c: Context) = send(c, "$PACKAGE.CONNECT_VPN")
    fun off(c: Context) = send(c, "$PACKAGE.DISCONNECT_VPN")

    private fun send(c: Context, action: String) {
        runCatching { c.sendBroadcast(Intent(action).setClassName(PACKAGE, "$PACKAGE.IPNReceiver")) }
    }

    /** Tailscale is installed on this phone. */
    fun installed(c: Context) = runCatching { c.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    /** Opens the Tailscale app (to sign in again, or allow it as the VPN). */
    fun openApp(c: Context) {
        runCatching {
            c.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** The VPN network on the phone (Tailscale is the only one he uses), or null. */
    fun vpn(c: Context): Network? {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    /** A VPN is up on the phone. */
    fun isUp(c: Context): Boolean = vpn(c) != null

    /** This phone's own Tailscale address (100.64.0.0/10), or null while Tailscale is not really connected. */
    fun ownIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }.flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.firstOrNull(::isTailscaleIp)
    }.getOrNull()

    /** 100.64.0.0/10, the addresses Tailscale gives out. */
    fun isTailscaleIp(ip: String): Boolean {
        val p = ip.split('.'); if (p.size != 4) return false
        val a = p[0].toIntOrNull() ?: return false; val b = p[1].toIntOrNull() ?: return false
        return a == 100 && b in 64..127
    }

    /** Up = the VPN stands and the phone holds its own 100.x address. */
    fun ready(c: Context) = isUp(c) && ownIp() != null

    /** Switches it on and waits until the VPN stands, at most [waitMs]: a link opened before it would drop when it comes. */
    fun onAndWait(c: Context, waitMs: Long = 8000) { ensure(c, waitMs) }

    /**
     * Switches Tailscale on (again, every few seconds, in case the first broadcast was lost while Tailscale's process
     * was asleep) and waits until it is [ready], at most [waitMs]. True when it stands.
     */
    fun ensure(c: Context, waitMs: Long = 15000, say: (String) -> Unit = {}): Boolean {
        if (ready(c)) return true
        if (!installed(c)) return false
        val until = System.currentTimeMillis() + waitMs
        var lastAsk = 0L
        while (System.currentTimeMillis() < until) {
            if (System.currentTimeMillis() - lastAsk > 4000) { on(c); lastAsk = System.currentTimeMillis() }
            if (ready(c)) { Thread.sleep(800); return true }           // the routes settle a moment after the VPN appears
            say(if (isUp(c)) "> TAILSCALE: WAITING FOR THIS PHONE'S 100.x ADDRESS" else "> TAILSCALE: SWITCHING ON")
            Thread.sleep(250)
        }
        android.util.Log.w("MantraRemote", "tailscale not ready after $waitMs ms: vpn=${isUp(c)} own=${ownIp()}")
        return ready(c)
    }

    /** Why Tailscale is not standing, in words for the status line. */
    fun whyDown(c: Context): String = when {
        !installed(c) -> "TAILSCALE IS NOT INSTALLED ON THIS PHONE"
        !isUp(c) -> "TAILSCALE DID NOT SWITCH ON. OPEN TAILSCALE, SIGN IN / ALLOW THE VPN, THEN ENTER AGAIN"
        else -> "TAILSCALE IS ON BUT NOT CONNECTED (NO 100.x ADDRESS). OPEN TAILSCALE: SIGNED OUT OR KEY EXPIRED?"
    }

    /**
     * The machine's Tailscale address of this moment, by its name (e.g. "macbook-pro"): asked of Tailscale's own DNS
     * over the tunnel, and of the VPN network's resolver with its search domain. Empty when nothing answers.
     */
    fun resolve(c: Context, name: String): List<String> {
        val n = name.trim().lowercase().trimEnd('.')
        if (n.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        val net = vpn(c)
        // 1. the VPN network's own resolver: with MagicDNS on, it knows the short name (search domain *.ts.net)
        if (net != null) {
            val domains = runCatching {
                c.getSystemService(ConnectivityManager::class.java)?.getLinkProperties(net)?.domains
            }.getOrNull().orEmpty().split(',', ' ').filter { it.isNotBlank() }
            (listOf(n) + domains.map { "$n.$it" }).forEach { q ->
                runCatching { net.getAllByName(q) }.getOrNull()?.filterIsInstance<Inet4Address>()
                    ?.mapNotNull { it.hostAddress }?.filter(::isTailscaleIp)?.let(out::addAll)
            }
        }
        // 2. quad 100 itself, with the tailnet's domain learnt from this phone's own name (PTR of its 100.x address)
        if (out.isEmpty()) {
            val fqdn = if (n.endsWith(".ts.net")) n else tailnetDomain()?.let { "$n.$it" }
            if (fqdn != null) out += Dns.a(fqdn, net)
            if (out.isEmpty()) out += Dns.a(n, net)
        }
        android.util.Log.i("MantraRemote", "resolve $n → $out")
        return out.toList()
    }

    /** The tailnet's DNS domain (tailXXXX.ts.net), from the name Tailscale gives this phone's own address. */
    private fun tailnetDomain(): String? {
        val me = ownIp() ?: return null
        val ptr = Dns.ptr(me) ?: return null                       // "pixel-7.tail1234.ts.net"
        return ptr.trimEnd('.').substringAfter('.', "").ifEmpty { null }
    }

    /** A tiny DNS client for 100.100.100.100 (A and PTR), so MagicDNS answers even when the phone's DNS is not Tailscale's. */
    private object Dns {
        fun a(name: String, net: Network?): List<String> =
            ask(name, 1, net).mapNotNull { (t, data, _) -> if (t == 1 && data.size == 4) InetAddress.getByAddress(data).hostAddress else null }
                .filter(::isTailscaleIp)

        fun ptr(ip: String): String? {
            val q = ip.split('.').reversed().joinToString(".") + ".in-addr.arpa"
            return ask(q, 12, null).firstOrNull { it.first == 12 }?.third
        }

        /** (type, rdata, decoded name for PTR) of each answer. */
        private fun ask(name: String, type: Int, net: Network?): List<Triple<Int, ByteArray, String?>> = runCatching {
            val id = (System.nanoTime() and 0xffff).toInt()
            val q = ByteArrayOutputStream()
            q.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            name.split('.').filter { it.isNotEmpty() }.forEach { l -> val b = l.toByteArray(); q.write(b.size); q.write(b) }
            q.write(0); q.write(0); q.write(type); q.write(0); q.write(1)
            val req = q.toByteArray()
            DatagramSocket().use { s ->
                runCatching { net?.bindSocket(s) }
                s.soTimeout = 1500
                s.send(DatagramPacket(req, req.size, InetSocketAddress(QUAD100, 53)))
                val buf = ByteArray(1500); val p = DatagramPacket(buf, buf.size); s.receive(p)
                parse(buf.copyOf(p.length), id)
            }
        }.onFailure { android.util.Log.w("MantraRemote", "quad100 $name: $it") }.getOrDefault(emptyList())

        private fun parse(m: ByteArray, id: Int): List<Triple<Int, ByteArray, String?>> {
            fun u16(i: Int) = ((m[i].toInt() and 0xff) shl 8) or (m[i + 1].toInt() and 0xff)
            if (m.size < 12 || u16(0) != id) return emptyList()
            val qd = u16(4); val an = u16(6)
            var i = 12
            repeat(qd) { i = skipName(m, i) + 4 }
            val out = ArrayList<Triple<Int, ByteArray, String?>>()
            repeat(an) {
                i = skipName(m, i)
                val t = u16(i); val len = u16(i + 8); val start = i + 10
                out += Triple(t, m.copyOfRange(start, start + len), if (t == 12) readName(m, start) else null)
                i = start + len
            }
            return out
        }

        private fun skipName(m: ByteArray, from: Int): Int {
            var i = from
            while (true) {
                val l = m[i].toInt() and 0xff
                if (l == 0) return i + 1
                if (l and 0xc0 == 0xc0) return i + 2
                i += l + 1
            }
        }

        private fun readName(m: ByteArray, from: Int): String {
            val parts = ArrayList<String>(); var i = from; var hops = 0
            while (hops++ < 20) {
                val l = m[i].toInt() and 0xff
                if (l == 0) break
                if (l and 0xc0 == 0xc0) { i = ((l and 0x3f) shl 8) or (m[i + 1].toInt() and 0xff); continue }
                parts += String(m, i + 1, l); i += l + 1
            }
            return parts.joinToString(".")
        }
    }
}
