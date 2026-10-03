package com.appsc.ca.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.appsc.ca.R

/** Keeps read-aloud going with the screen locked: a foreground service with a Pause/Play and Stop notification. */
class ReadAloudService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ReadAloud.init(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION, notification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (ReadAloud.playback.value.playing) ReadAloud.pause() else ReadAloud.resume()
            ACTION_STOP -> ReadAloud.stop()
        }
        if (!ReadAloud.playback.value.active) stopSelf()
        return START_NOT_STICKY
    }

    /** The app was swiped away from recents: stop reading. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        ReadAloud.stop()
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "read_aloud"
        private const val NOTIFICATION = 7
        private const val ACTION_TOGGLE = "com.appsc.ca.readaloud.TOGGLE"
        private const val ACTION_STOP = "com.appsc.ca.readaloud.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ReadAloudService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReadAloudService::class.java))
            NotificationManagerCompat.from(context).cancel(NOTIFICATION)
        }

        /** Updates the notification (title, Pause/Play). */
        fun refresh(context: Context) {
            if (!ReadAloud.playback.value.active) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION, notification(context)) }
        }

        private fun notification(context: Context): android.app.Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Read aloud", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Controls for reading current affairs aloud"
                            setShowBadge(false)
                        },
                    )
                }
            }
            val playing = ReadAloud.playback.value.playing
            fun action(action: String, code: Int) = PendingIntent.getService(
                context, code, Intent(context, ReadAloudService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val open = ReadAloud.openApp(context)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_listen)
                .setContentTitle(ReadAloud.title.ifBlank { "Reading current affairs aloud" })
                .setContentText(if (playing) "APPSC Daily CA · reading aloud" else "APPSC Daily CA · paused")
                .setContentIntent(open)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Play", action(ACTION_TOGGLE, 1),
                )
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACTION_STOP, 2))
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))
                .build()
        }
    }
}
