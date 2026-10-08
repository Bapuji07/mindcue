package com.secondmemory.android.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.secondmemory.android.MainActivity
import com.secondmemory.android.R

/** The reminder and morning-summary notifications (the recording notification lives in its service). */
object Notifications {
    private const val CHANNEL_REMINDERS = "task_reminders"
    private const val CHANNEL_DIGEST = "morning_summary"
    private const val DIGEST_ID = 7001
    private const val MAX_DIGEST_LINES = 5

    /** Android 13+ needs the runtime permission; older versions only the app-level switch. */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun reminderId(memoryId: String): Int = "reminder:$memoryId".hashCode()

    @SuppressLint("MissingPermission") // guarded by allowed()
    fun showReminder(context: Context, memoryId: String, title: String, text: String) {
        if (!allowed(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_memory_foreground)
            .setContentTitle(title.ifBlank { context.getString(R.string.reminder_due_now) })
            .setContentText(context.getString(R.string.reminder_due_now))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { context.getString(R.string.reminder_due_now) }))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openTasks(context, reminderId(memoryId)))
            .addAction(0, context.getString(R.string.notification_action_done), action(context, ReminderActionReceiver.ACTION_DONE, memoryId))
            .addAction(0, context.getString(R.string.notification_action_tomorrow), action(context, ReminderActionReceiver.ACTION_TOMORROW, memoryId))
            .build()
        NotificationManagerCompat.from(context).notify(reminderId(memoryId), notification)
    }

    @SuppressLint("MissingPermission") // guarded by allowed()
    fun showDigest(context: Context, digest: Digest) {
        if (digest.isEmpty || !allowed(context)) return
        ensureChannels(context)
        val res = context.resources
        val title = listOfNotNull(
            digest.dueToday.size.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.digest_due_today, it, it) },
            digest.overdue.size.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.digest_overdue, it, it) }
        ).joinToString(" · ")
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        digest.all.take(MAX_DIGEST_LINES).forEach { style.addLine(it.title) }
        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_memory_foreground)
            .setContentTitle(title)
            .setContentText(digest.all.joinToString(", ") { it.title })
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openTasks(context, DIGEST_ID))
            .build()
        NotificationManagerCompat.from(context).notify(DIGEST_ID, notification)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminders_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, context.getString(R.string.channel_digest_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_digest_description) }
        )
    }

    private fun openTasks(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PAGE, MainActivity.PAGE_TASKS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(context: Context, action: String, memoryId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("mindcue://memory/" + Uri.encode(memoryId)))
            .putExtra(ReminderActionReceiver.EXTRA_MEMORY_ID, memoryId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
