package com.mantra.remote

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * The phone's Tailscale, switched by the app: on when the app opens, off when it closes (Marko, 26.10.2026: "When I open
 * the app, tail scale is opening. When I close the app, tail scale is closing"). Tailscale's own receiver takes the two
 * actions; it can only switch on once Tailscale was signed in and allowed as the VPN one time by hand.
 */
object Tailscale {
    private const val PACKAGE = "com.tailscale.ipn"

    fun on(c: Context) = send(c, "$PACKAGE.CONNECT_VPN")
    fun off(c: Context) = send(c, "$PACKAGE.DISCONNECT_VPN")

    private fun send(c: Context, action: String) {
        runCatching { c.sendBroadcast(Intent(action).setClassName(PACKAGE, "$PACKAGE.IPNReceiver")) }
    }

    /** A VPN is up on the phone (Tailscale is the only one he uses). */
    fun isUp(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    /** Switches it on and waits until the VPN stands, at most [waitMs]: a link opened before it would drop when it comes. */
    fun onAndWait(c: Context, waitMs: Long = 8000) {
        if (isUp(c)) return
        on(c)
        val until = System.currentTimeMillis() + waitMs
        while (!isUp(c) && System.currentTimeMillis() < until) Thread.sleep(200)
        if (isUp(c)) Thread.sleep(800)          // the routes settle a moment after the VPN appears
    }
}
