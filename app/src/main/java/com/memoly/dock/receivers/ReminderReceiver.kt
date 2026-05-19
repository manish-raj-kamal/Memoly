package com.memoly.dock.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memoly.dock.MainActivity
import com.memoly.dock.R
import com.memoly.dock.utils.reminderNotificationText
import com.memoly.dock.workers.ReminderWorker
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Broadcast receiver for direct alarm-based reminders (backup to WorkManager).
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEMORY_ID = "memory_id"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_SNOOZE_HOURS = "snooze_hours"
        const val ACTION_MARK_DONE = "com.memoly.dock.action.MARK_DONE"
        const val ACTION_RESCHEDULE = "com.memoly.dock.action.RESCHEDULE"
        const val ACTION_SNOOZE_REMINDER = "com.memoly.dock.action.SNOOZE_REMINDER"
        private const val CHANNEL_ID = "memoly_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getLongExtra(EXTRA_MEMORY_ID, -1)
        if (memoryId == -1L) return

        if (intent.action == ACTION_MARK_DONE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.memoly.dock.data.local.MemolyDatabase.getDatabase(context)
                    db.memoryItemDao().markReminderDone(memoryId)
                    ReminderWorker.cancel(context, memoryId)
                    NotificationManagerCompat.from(context).cancel(memoryId.toInt())
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (intent.action == ACTION_RESCHEDULE) {
            showSnoozeOptionsNotification(
                context = context,
                memoryId = memoryId,
                content = intent.getStringExtra(EXTRA_CONTENT) ?: "You have a reminder"
            )
            return
        }

        if (intent.action == ACTION_SNOOZE_REMINDER) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.memoly.dock.data.local.MemolyDatabase.getDatabase(context)
                    val item = db.memoryItemDao().getItemByIdOnce(memoryId) ?: return@launch
                    val snoozeHours = intent.getIntExtra(EXTRA_SNOOZE_HOURS, 1).coerceAtLeast(1)
                    val newReminderTime = System.currentTimeMillis() + snoozeHours * 60L * 60L * 1000L
                    db.memoryItemDao().update(
                        item.copy(
                            reminderTime = newReminderTime,
                            isReminderDone = false,
                            lastModifiedAt = System.currentTimeMillis()
                        )
                    )
                    ReminderWorker.schedule(context, item.id, item.content, newReminderTime)
                    NotificationManagerCompat.from(context).cancel(memoryId.toInt())
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val content = intent.getStringExtra(EXTRA_CONTENT) ?: "You have a reminder"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("memory_id", memoryId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            memoryId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark Done Action
        val markDoneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_MEMORY_ID, memoryId)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            memoryId.toInt(),
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reschedule Action
        val rescheduleIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_RESCHEDULE
            putExtra(EXTRA_MEMORY_ID, memoryId)
            putExtra(EXTRA_CONTENT, content)
        }
        val reschedulePendingIntent = PendingIntent.getBroadcast(
            context,
            memoryId.toInt() + 1_000,
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Memoly Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Memoly Reminder")
            .setContentText(reminderNotificationText(content))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderNotificationText(content)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Reschedule", reschedulePendingIntent)
            .addAction(0, "Mark Done", markDonePendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(memoryId.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun showSnoozeOptionsNotification(
        context: Context,
        memoryId: Long,
        content: String
    ) {
        ReminderWorker.createNotificationChannel(context)
        val summary = reminderNotificationText(content)
        val actionHours = listOf(1, 2, 5, 12)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reschedule Reminder")
            .setContentText("Remind again for: $summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Choose when to be reminded again.\n\n$summary"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))

        actionHours.forEachIndexed { index, hours ->
            val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE_REMINDER
                putExtra(EXTRA_MEMORY_ID, memoryId)
                putExtra(EXTRA_SNOOZE_HOURS, hours)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                memoryId.toInt() + 2_000 + index,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "${hours}h", snoozePendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(memoryId.toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }
}
