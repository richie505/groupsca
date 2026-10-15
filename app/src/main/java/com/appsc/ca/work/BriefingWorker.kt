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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The 9 PM briefing notification. Every 30 minutes (Android may run it a little later), from the chosen
 * hour (IST) on, it downloads the briefings and, once today's is published, says so once:
 * "📰 Today's APPSC Current Affairs is ready". Tapping it opens the briefing.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = UserStore(applicationContext)
        val ist = ZoneId.of("Asia/Kolkata")
        val today = LocalDate.now(ist).toString()
        if (!user.notify || user.briefNotified == today) return Result.success()
        if (LocalTime.now(ist).hour < user.briefHour) return Result.success()
        val ready = runCatching { FeedStore(applicationContext).refreshBriefings() }.getOrDefault(false)
        if (ready) {
            notifyReady(applicationContext, today)
            user.briefNotified = today
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "briefing-notify"
        private const val CHANNEL = "briefing"
        const val EXTRA_BRIEFING = "open_briefing"

        /** The 9 PM AI briefing is gone (the day's items come from uploads): its periodic check is cancelled. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BriefingWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notifyReady(context: Context, date: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily APPSC briefing", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_BRIEFING, date),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_news)
                .setContentTitle("📰 Today's APPSC Current Affairs is ready")
                .setContentText("International + National + Andhra Pradesh. Tap to revise today's briefing.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(2, n)
        }
    }
}
