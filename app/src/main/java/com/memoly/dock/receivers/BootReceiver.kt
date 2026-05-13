package com.memoly.dock.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.workers.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules pending reminders after device reboot.
 * Only active when auto-startup is enabled in settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return

        // Re-schedule all pending reminders
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = MemolyDatabase.getDatabase(context)
                val items = db.memoryItemDao().getUpcomingReminders().first()

                for (item in items) {
                    item.reminderTime?.let { reminderTime ->
                        if (reminderTime > System.currentTimeMillis()) {
                            ReminderWorker.schedule(
                                context = context,
                                memoryId = item.id,
                                content = item.content,
                                triggerAtMillis = reminderTime
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
