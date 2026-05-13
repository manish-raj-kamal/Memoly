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

/**
 * Broadcast receiver for direct alarm-based reminders (backup to WorkManager).
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEMORY_ID = "memory_id"
        const val EXTRA_CONTENT = "content"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getLongExtra(EXTRA_MEMORY_ID, -1)
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: "You have a reminder"

        if (memoryId == -1L) return

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
            .build()

        try {
            NotificationManagerCompat.from(context).notify(memoryId.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}
