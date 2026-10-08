package com.secondmemory.android.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.secondmemory.android.data.AppSettings

/** A task's due time arrived: show its reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getStringExtra(ReminderAlarms.EXTRA_MEMORY_ID) ?: return
        ReminderAlarms.forget(context, memoryId)
        if (!AppSettings.isLoggedIn(context) || !AppSettings.remindersEnabled(context)) return
        Notifications.showReminder(
            context, memoryId,
            intent.getStringExtra(ReminderAlarms.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(ReminderAlarms.EXTRA_TEXT).orEmpty()
        )
    }
}

/** "Done" or "Tomorrow" tapped on a reminder: dismiss it now, update the server in the background. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val memoryId = intent.getStringExtra(EXTRA_MEMORY_ID) ?: return
        NotificationManagerCompat.from(context).cancel(Notifications.reminderId(memoryId))
        when (intent.action) {
            ACTION_DONE -> MemoryActionWorker.enqueue(context, memoryId, MemoryActionWorker.DONE)
            ACTION_TOMORROW -> MemoryActionWorker.enqueue(context, memoryId, MemoryActionWorker.TOMORROW)
        }
    }

    companion object {
        const val ACTION_DONE = "com.secondmemory.reminder.DONE"
        const val ACTION_TOMORROW = "com.secondmemory.reminder.TOMORROW"
        const val EXTRA_MEMORY_ID = "memory_id"
    }
}

/** Morning-summary time: arm tomorrow's alarm, then build today's summary in the background. */
class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DigestAlarm.schedule(context)
        if (AppSettings.isLoggedIn(context) && AppSettings.digestEnabled(context)) DigestWorker.enqueue(context)
    }
}

/** Alarms don't survive a reboot or an app update; set them up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!AppSettings.isLoggedIn(context)) return
        ReminderSync.start(context)
    }
}
