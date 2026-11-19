package com.mantra.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * v64: SHARE THIS PHONE (VNC). The screen is captured by MediaProjection (Android asks once per start: "Start
 * recording or casting with Mantra Remote Viewing?", entire screen) into an ImageReader and handed to [VncServer] on
 * port 5900. A foreground service of type mediaProjection with a STOP action; when Android ends the projection (its
 * own stop chip, the screen locked on some phones) the server goes off with it. The picture follows a turn of the
 * phone (the virtual display is resized, viewers get DesktopSize).
 */
class ScreenShareService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val worker = HandlerThread("vnc-capture").apply { start() }
    private val handler = Handler(worker.looper)
    private val main = Handler(Looper.getMainLooper())
    private var lastGrab = 0L
    private var grabPosted = false
    private var curW = 0
    private var curH = 0
    private var dpi = 0

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) handler.post { follow() } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish(); return START_NOT_STICKY }
        startForeground(NOTE_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        if (data == null || projection != null) { if (projection == null) finish(); return START_NOT_STICKY }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
        if (p == null) { VncServer.note = "ANDROID GAVE NO SCREEN"; finish(); return START_NOT_STICKY }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { finish() } }
        }, handler)
        dpi = resources.displayMetrics.densityDpi
        val (w, h) = size()
        curW = w; curH = h
        val r = newReader(w, h)
        display = p.createVirtualDisplay("mantra-vnc", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        VncServer.start(password(this))
        VncServer.on = true
        VncServer.note = "SHARING ${w}x$h"
        return START_NOT_STICKY
    }

    private fun size(): Pair<Int, Int> {
        val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun newReader(w: Int, h: Int): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ scheduleGrab() }, handler)
        reader = r
        return r
    }

    /** At most 15 pictures a second while someone watches, 2 a second otherwise; the newest one is always taken. */
    private fun scheduleGrab() {
        if (grabPosted) return
        grabPosted = true
        val interval = if (VncServer.hasViewers()) 66L else 500L
        val wait = (lastGrab + interval - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed({ grabPosted = false; grab() }, wait)
    }

    private fun grab() {
        val img = runCatching { reader?.acquireLatestImage() }.getOrNull() ?: return
        lastGrab = SystemClock.uptimeMillis()
        try {
            val plane = img.planes[0]
            // the reader of an old size may still deliver one picture after a turn
            if (img.width == curW && img.height == curH) VncServer.putFrame(img.width, img.height, plane.buffer, plane.rowStride)
        } catch (e: Exception) {
            android.util.Log.w("MantraRemote", "vnc grab: $e")
        } finally { img.close() }
    }

    /** The phone turned: the virtual display takes the new size, so the picture stays whole and upright. */
    private fun follow() {
        val d = display ?: return
        val (w, h) = size()
        if (w == curW && h == curH) return
        val old = reader
        curW = w; curH = h
        val r = newReader(w, h)
        d.resize(w, h, dpi)
        d.surface = r.surface
        old?.close()
        VncServer.note = "TURNED: ${w}x$h"
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sharing this phone (VNC)", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sharing this phone (VNC, port ${VncServer.PORT})")
            .setContentText("Password in the app. Tap STOP to end.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun finish() {
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        VncServer.stop()
        VncServer.on = false
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        val p = projection; projection = null
        runCatching { p?.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (projection != null) finish()
        worker.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "share"
        private const val NOTE_ID = 3
        private const val ACTION_STOP = "com.mantra.remote.SHARE_STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val PW_KEY = "vnc_server_password"

        fun start(c: Context, code: Int, data: Intent) {
            c.startForegroundService(Intent(c, ScreenShareService::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }
        fun stop(c: Context) {
            runCatching { c.startService(Intent(c, ScreenShareService::class.java).setAction(ACTION_STOP)) }
        }

        /** The server's VNC password: 8 characters (VNC uses no more), made once, shown in the app, new on demand. */
        fun password(c: Context): String {
            val p = c.getSharedPreferences("remote", Context.MODE_PRIVATE)
            p.getString(PW_KEY, null)?.takeIf { it.length == 8 }?.let { return it }
            return newPassword(c)
        }
        fun newPassword(c: Context): String {
            val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val rnd = SecureRandom()
            val pw = String(CharArray(8) { abc[rnd.nextInt(abc.length)] })
            c.getSharedPreferences("remote", Context.MODE_PRIVATE).edit().putString(PW_KEY, pw).apply()
            VncServer.password = pw
            return pw
        }

        /** This phone's IPv4 addresses (Tailscale 100.x first, then the Wi-Fi), for the card. */
        fun addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>()
                .mapNotNull { it.hostAddress }.sortedBy { if (it.startsWith("100.")) 0 else 1 }
        }.getOrDefault(emptyList())
    }
}
