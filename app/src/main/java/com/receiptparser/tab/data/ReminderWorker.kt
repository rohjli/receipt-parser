package com.receiptparser.tab.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.receiptparser.tab.MainActivity
import com.receiptparser.tab.R
import com.receiptparser.tab.domain.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.time.ZonedDateTime
import java.time.Duration
import java.util.concurrent.TimeUnit

@EntryPoint @InstallIn(SingletonComponent::class)
interface ReminderDependencies { fun backup(): BackupService }
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, ReminderDependencies::class.java)
            val generation = StorageGate.generation
            val (settings, snapshot) = deps.backup().readForReminder()
            val now = ZonedDateTime.now()
            if (Ledger.isQuietHour(now.hour, settings.quietStart, settings.quietEnd)) {
                var next = now.withHour(settings.quietEnd).withMinute(0).withSecond(0).withNano(0)
                if (!next.isAfter(now)) next = next.plusDays(1)
                WorkManager.getInstance(applicationContext).enqueueUniqueWork("tab-after-quiet", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build())
                return Result.success()
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("open-tabs", "Open tabs", NotificationManager.IMPORTANCE_DEFAULT))
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
            val threshold = System.currentTimeMillis() - settings.reminderHours * 3_600_000L
            val overdue = snapshot.friends.filter { snapshot.balance(it.id) > 0 && (snapshot.oldestOutstanding(it.id)?.let { date -> date <= threshold } == true) }
            if (overdue.isEmpty()) { manager.cancel(100); return Result.success() }
            val total = overdue.sumOf { snapshot.balance(it.id) }
            val detail = overdue.joinToString(" · ") { "${it.name} ${Money.format(snapshot.balance(it.id))}" }
            val pending = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java).putExtra("open_home", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return StorageGate.exclusive {
                if (StorageGate.blocked || StorageGate.generation != generation) Result.retry()
                else {
                    manager.notify(100, NotificationCompat.Builder(applicationContext, "open-tabs").setSmallIcon(R.drawable.ic_tungaon_monochrome).setContentTitle("${overdue.size} ${if (overdue.size == 1) "friend owes" else "friends owe"} you ${Money.format(total)}").setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(pending).setAutoCancel(true).build())
                    Result.success()
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.e("TabReminder", "Unable to check local balances", e); return if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("tab-daily-reminder", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.MINUTES).build()) }
    }
}
