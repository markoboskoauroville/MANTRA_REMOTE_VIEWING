package com.mantra.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Keeps the app's process awake while a link is up, so going to the background does not freeze it and drop the Mac
 * (Marko, 25.10.2026: "when it goes to the background and I bring it back up, I need to reconnect"). It holds nothing
 * itself: the link lives in [Hold.session]; this only tells Android the app is still working, with a quiet notification.
 */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Link to the Mac", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Mantra Remote: link to the Mac")
            .setContentText("In the background at 0.2 FPS. Tap to come back.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_NOT_STICKY
    }

    /** Swiped away from the recent apps: the app is closed, so the link and Tailscale go off (Marko, 26.10.2026). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        closeApp(); stopSelf()
    }

    companion object {
        private const val CHANNEL = "link"
        fun start(c: Context) { runCatching { c.startForegroundService(Intent(c, LinkService::class.java)) } }
        fun stop(c: Context) { runCatching { c.stopService(Intent(c, LinkService::class.java)) } }
    }
}
