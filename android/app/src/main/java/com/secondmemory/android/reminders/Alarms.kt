package com.secondmemory.android.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.secondmemory.android.data.AppSettings
import java.time.Instant
import java.time.ZoneId

/**
 * Reminder alarms, one per memory. Inexact (setAndAllowWhileIdle) so no special alarm permission
 * is needed; Android may deliver them a few minutes late to save battery.
 */
object ReminderAlarms {
    const val EXTRA_MEMORY_ID = "memory_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    fun schedule(context: Context, reminders: List<PlannedReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val wanted = reminders.map { it.memoryId }.toSet()
        (AppSettings.scheduledReminders(context) - wanted).forEach { cancel(context, alarms, it) }
        reminders.forEach { reminder ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pendingIntent(context, reminder.memoryId, reminder))
        }
        AppSettings.saveScheduledReminders(context, wanted)
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        AppSettings.scheduledReminders(context).forEach { cancel(context, alarms, it) }
        AppSettings.saveScheduledReminders(context, emptySet())
    }

    /** Called when an alarm has fired, so it is not cancelled or counted again later. */
    fun forget(context: Context, memoryId: String) =
        AppSettings.saveScheduledReminders(context, AppSettings.scheduledReminders(context) - memoryId)

    private fun cancel(context: Context, alarms: AlarmManager, memoryId: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, intent(context, memoryId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(context: Context, memoryId: String, reminder: PlannedReminder): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            intent(context, memoryId)
                .putExtra(EXTRA_MEMORY_ID, reminder.memoryId)
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_TEXT, reminder.text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // The data URI makes each memory's alarm a distinct PendingIntent.
    private fun intent(context: Context, memoryId: String) = Intent(context, ReminderReceiver::class.java)
        .setData(Uri.parse("mindcue://reminder/" + Uri.encode(memoryId)))
}

/** The daily morning-summary alarm; it re-arms itself each time it fires. */
object DigestAlarm {
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        if (!AppSettings.isLoggedIn(context) || !AppSettings.digestEnabled(context)) {
            alarms.cancel(pending)
            return
        }
        val at = ReminderPlanner.nextDigestTime(Instant.now(), ZoneId.systemDefault(), AppSettings.digestMinuteOfDay(context))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, DigestReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
