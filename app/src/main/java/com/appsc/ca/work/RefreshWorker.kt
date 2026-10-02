package com.appsc.ca.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.appsc.ca.MainActivity
import com.appsc.ca.R
import com.appsc.ca.data.FeedStore
import com.appsc.ca.data.UserStore
import java.util.concurrent.TimeUnit

/**
 * Downloads new stories in the background, every 12 hours on any network —
 * the feed itself is updated at about 6 AM and 8 PM IST — and says how many
 * arrived, with the Andhra Pradesh count first.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = runCatching { FeedStore(applicationContext).refresh() }.getOrElse {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (result.newItems > 0 && !result.firstRun && UserStore(applicationContext).notify) {
            notifyNew(applicationContext, result.newItems, result.newAp)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "daily-refresh"
        private const val CHANNEL = "daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notifyNew(context: Context, count: Int, ap: Int) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily current affairs", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (ap > 0) "$ap Andhra Pradesh · ${count - ap} National & International" else "Tap to read today's updates"
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_news)
                .setContentTitle("$count new exam-relevant updates")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, n)
        }
    }
}
