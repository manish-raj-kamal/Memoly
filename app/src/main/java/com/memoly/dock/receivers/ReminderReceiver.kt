package com.memoly.dock.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memoly.dock.MainActivity
import com.memoly.dock.R
import kotlinx.coroutines.launch

/**
 * Broadcast receiver for direct alarm-based reminders (backup to WorkManager).
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEMORY_ID = "memory_id"
        const val EXTRA_CONTENT = "content"
        const val ACTION_MARK_DONE = "com.memoly.dock.action.MARK_DONE"
        const val ACTION_RESCHEDULE = "com.memoly.dock.action.RESCHEDULE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getLongExtra(EXTRA_MEMORY_ID, -1)
        if (memoryId == -1L) return

        if (intent.action == ACTION_MARK_DONE) {
            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = com.memoly.dock.data.local.MemolyDatabase.getDatabase(context)
                    db.memoryItemDao().markReminderDone(memoryId)
                    NotificationManagerCompat.from(context).cancel(memoryId.toInt())
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (intent.action == ACTION_RESCHEDULE) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("memory_id", memoryId)
                putExtra("open_reschedule", true)
            }
            context.startActivity(openIntent)
            NotificationManagerCompat.from(context).cancel(memoryId.toInt())
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
        }
        val reschedulePendingIntent = PendingIntent.getBroadcast(
            context,
            memoryId.toInt(),
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "memoly_reminders"
        val channel = NotificationChannel(
            channelId,
            "Memoly Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Memoly Reminder")
            .setContentText(content.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
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
}
