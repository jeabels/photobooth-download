package com.foodpos.app.alerts

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.foodpos.app.MainActivity
import com.foodpos.app.PosApplication
import com.foodpos.app.R
import java.util.concurrent.TimeUnit

/**
 * System notifications for inventory alerts, so spoiling stock is noticed even when the app is closed.
 * Inside the app the same alerts show live in the Alerts screen, a badge and a snackbar.
 */
object AlertNotifier {
    private const val CHANNEL_ID = "inventory_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val WORK_NAME = "inventory-alert-check"
    private const val PREFS = "pos_alerts"
    private const val KEY_LAST = "last_signature"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.alerts_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts a summary when the set of alerts changed since the last notification. */
    @SuppressLint("MissingPermission")
    fun notifyIfChanged(context: Context, alerts: List<InventoryAlert>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = alerts.map { it.key }.sorted().joinToString("|")
        if (signature == prefs.getString(KEY_LAST, null)) return
        prefs.edit().putString(KEY_LAST, signature).apply()

        val manager = NotificationManagerCompat.from(context)
        if (alerts.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed || !manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ALERTS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        alerts.take(6).forEach { style.addLine("${it.type.label}: ${it.productName}") }
        if (alerts.size > 6) style.setSummaryText("+${alerts.size - 6} more")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("Inventory needs attention")
            .setContentText(InventoryAlerts.summary(alerts))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

class AlertCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosApplication).container
        AlertNotifier.notifyIfChanged(applicationContext, container.inventory.currentAlerts())
        return Result.success()
    }
}
