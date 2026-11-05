package com.mantra.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.mantra.remote.adb.AdbManager
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * v30, KEEP DEBUGGING (Marko, 5.11.2026: "when the system turns off wireless debugging, you turn it on ... permanently be
 * in debugging mode ... if that's not possible, at least open the settings automatically for me to click every day").
 *
 * Three layers, each one used when the one before cannot:
 * 1. With WRITE_SECURE_SETTINGS (granted once from the Mac: `phones keep`, i.e. `pm grant com.mantra.remote
 *    android.permission.WRITE_SECURE_SETTINGS`), the app itself switches USB debugging and wireless debugging back on the
 *    moment the system turns them off (a watcher on both settings) and after every start of the phone.
 * 2. adb on TCP 5555 (what the Mac, the VM and the other phone use over Tailscale) is lost at every restart of the phone.
 *    The app then connects to its own phone through wireless debugging (its key was allowed once, "Always allow") and
 *    asks for `tcpip:5555`, the same as `adb tcpip 5555` from the Mac.
 * 3. Without the permission: a notification "Debugging is off" whose tap opens Developer options.
 */
object DebugKeeper {
    private const val TAG = "DebugKeeper"
    private const val WIFI = "adb_wifi_enabled"
    private const val CHANNEL = "debug"
    @Volatile private var restoring = false
    @Volatile var last = ""; private set

    fun canWrite(c: Context) = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    /** On, unless the phone says plainly 0. v31: Android 17 hides adb_enabled from apps (read as missing while adb
     *  works), so missing counts as on and only a real 0 is switched back. */
    private fun get(c: Context, k: String) = Settings.Global.getInt(c.contentResolver, k, -1) != 0

    fun port5555(): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 5555), 800) }; true
    }.getOrDefault(false)

    fun status(c: Context): JSONObject = JSONObject()
        .put("usb_debugging", get(c, Settings.Global.ADB_ENABLED))
        .put("wireless_debugging", get(c, WIFI))
        .put("tcp_5555", port5555())
        .put("tailscale", tailscaleUp())
        .put("can_write_settings", canWrite(c))
        .put("last", last)

    /** Everything back on; off the main thread (it may open a socket). */
    fun check(c: Context) = thread(name = "debug-keeper") {
        val usb = get(c, Settings.Global.ADB_ENABLED); val wifi = get(c, WIFI)
        if (canWrite(c)) {
            runCatching {
                if (!usb) Settings.Global.putInt(c.contentResolver, Settings.Global.ADB_ENABLED, 1)
                if (!wifi) Settings.Global.putInt(c.contentResolver, WIFI, 1)
            }.onFailure { Log.w(TAG, "settings", it) }
            if (!usb || !wifi) last = "switched debugging back on"
            quiet(c)
        } else if (!usb || !wifi) {
            remind(c); return@thread
        }
        // v34: Tailscale back after a restart (also set as the always-on VPN by mremote keep)
        if (!tailscaleUp()) runCatching { Tailscale.on(c) }
        if (!port5555()) restore5555(c)
    }

    fun tailscaleUp(): Boolean = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().any { i -> i.isUp && i.inetAddresses.toList().any { it.hostAddress?.startsWith("100.") == true } }
    }.getOrDefault(false)

    /** `adb tcpip 5555` asked of the phone itself, through its own wireless debugging. */
    private fun restore5555(c: Context) {
        if (restoring) return
        restoring = true
        try {
            Thread.sleep(3000)                // wireless debugging needs a moment after it is switched on
            val m = AdbManager(c)
            try {
                if (!m.autoConnect(c, 15_000)) { last = "wireless debugging not found (Wi-Fi off?)"; return }
                m.openStream("tcpip:5555").use { s -> runCatching { s.read(ByteArray(64), 0, 64) } }
                last = "adb on 5555 restored"
            } finally { runCatching { m.close() } }
        } catch (e: Throwable) {
            last = "5555: " + (e.message ?: e.javaClass.simpleName); Log.w(TAG, "5555", e)
        } finally { restoring = false }
    }

    private fun channel(c: Context): NotificationManager = c.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL, "Keep debugging", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun remind(c: Context) {
        val open = PendingIntent.getActivity(c, 7, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        channel(c).notify(7, Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Debugging is off").setContentText("Tap: Developer options, switch Wireless debugging on.")
            .setContentIntent(open).setAutoCancel(true).build())
        last = "reminded: debugging off, no permission to switch it"
    }

    private fun quiet(c: Context) = runCatching { c.getSystemService(NotificationManager::class.java).cancel(7) }

    fun start(c: Context) { runCatching { c.startForegroundService(Intent(c, KeeperService::class.java)) } }
}

/** Runs all the time: watches both debugging switches and looks at port 5555 every minute. */
class KeeperService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val watcher = object : ContentObserver(h) { override fun onChange(selfChange: Boolean) { DebugKeeper.check(this@KeeperService) } }
    private val tick = object : Runnable { override fun run() { DebugKeeper.check(this@KeeperService); h.postDelayed(this, 60_000) } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("keeper", "Debugging kept on", NotificationManager.IMPORTANCE_MIN))
        startForeground(8, Notification.Builder(this, "keeper").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Keeping debugging on").setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, watcher)
        contentResolver.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, watcher)
        h.post(tick)
        MacroDoor.open(this)      // v31: the door from the start of the phone, not only once the app was opened
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(watcher); h.removeCallbacks(tick); super.onDestroy()
    }
}

/** After every start of the phone and every update of the app. */
class KeeperBoot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { DebugKeeper.start(c) }
}
