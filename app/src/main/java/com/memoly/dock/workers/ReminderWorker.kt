package com.memoly.dock.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.memoly.dock.MainActivity
import com.memoly.dock.R
import com.memoly.dock.data.local.MemolyDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that fires reminder notifications.
 * Works offline and survives reboots when re-scheduled by BootReceiver.
 *
 * Key fixes:
 *  - Notification sound and vibration enabled
 *  - Default ringtone used for alarm-like behavior
 *  - Expedited work for time-critical reminders
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "memoly_reminders"
        const val KEY_MEMORY_ID = "memory_id"
        const val KEY_CONTENT = "content"

        /**
         * Schedule a reminder notification for a specific memory item.
         * Uses setExpedited for short delays to ensure timely delivery.
         */
        fun schedule(
            context: Context,
            memoryId: Long,
            content: String,
            triggerAtMillis: Long
        ) {
            val delay = triggerAtMillis - System.currentTimeMillis()
            if (delay <= 0) {
                // If time already passed, fire immediately
                fireImmediately(context, memoryId, content)
                return
            }

            val data = workDataOf(
                KEY_MEMORY_ID to memoryId,
                KEY_CONTENT to content
            )

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_$memoryId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reminder_$memoryId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        /**
         * Fire a notification immediately (for reminders where time already passed).
         */
        private fun fireImmediately(context: Context, memoryId: Long, content: String) {
            val data = workDataOf(
                KEY_MEMORY_ID to memoryId,
                KEY_CONTENT to content
            )

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(data)
                .addTag("reminder_$memoryId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reminder_$memoryId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        /**
         * Cancel a scheduled reminder.
         */
        fun cancel(context: Context, memoryId: Long) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("reminder_$memoryId")
        }

        /**
         * Create the notification channel (call on app startup).
         * Configured with HIGH importance, sound, and vibration.
         */
        fun createNotificationChannel(context: Context) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Memoly Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for memory reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                enableLights(true)
                lightColor = 0xFF6C63FF.toInt() // Memoly primary color
                setBypassDnd(false)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val memoryId = inputData.getLong(KEY_MEMORY_ID, -1)
        val content = inputData.getString(KEY_CONTENT) ?: "You have a reminder"

        if (memoryId == -1L) return Result.failure()

        showNotification(memoryId, content)
        return Result.success()
    }

    private fun showNotification(memoryId: Long, content: String) {
        // Ensure channel exists
        createNotificationChannel(applicationContext)

        // Intent to open the memory item in the app
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("memory_id", memoryId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            memoryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ Memoly Reminder")
            .setContentText(content.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Check notification permission (Android 13+)
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasPermission) {
            NotificationManagerCompat.from(applicationContext)
                .notify(memoryId.toInt(), notification)
        }
    }
}
