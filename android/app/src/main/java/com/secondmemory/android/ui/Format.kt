package com.secondmemory.android.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.secondmemory.android.R
import com.secondmemory.android.data.ConversationSession
import com.secondmemory.android.data.DueDates
import com.secondmemory.android.data.MemoryItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** "Today, 9:41 PM", "Yesterday, 21:41" or "Mon 5 Oct, 9:41 PM" in the phone's time zone and clock format. */
@Composable
internal fun friendlyDateTime(value: String?): String {
    val instant = parseInstant(value)
        ?: return value?.replace('T', ' ')?.take(16)?.ifBlank { null } ?: stringResource(R.string.date_unknown)
    return formatWhen(instant, LocalContext.current)
}

/** A due date: whole-day ones ("by Friday") show just the day, timed ones the day and time. */
@Composable
internal fun friendlyDue(value: String?): String {
    val instant = DueDates.parse(value) ?: return friendlyDateTime(value)
    val context = LocalContext.current
    return if (DueDates.isDateOnly(instant, ZoneId.systemDefault())) formatDay(instant, context) else formatWhen(instant, context)
}

internal fun formatWhen(instant: Instant, context: Context): String =
    context.getString(R.string.date_with_time, formatDay(instant, context), DateFormat.getTimeFormat(context).format(Date.from(instant)))

/** "Today", "Yesterday", "Tomorrow", or "Mon 5 Oct" (with the year when it isn't this year). */
private fun formatDay(instant: Instant, context: Context): String {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> context.getString(R.string.date_today)
        today.minusDays(1) -> context.getString(R.string.date_yesterday)
        today.plusDays(1) -> context.getString(R.string.date_tomorrow)
        else -> {
            val skeleton = if (date.year == today.year) "EEEdMMM" else "dMMMyyyy"
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)).format(date)
        }
    }
}

/** "1 Nov" style date (in the phone's locale) for a reset instant, or the raw value if it can't be parsed. */
fun resetDate(instant: String): String = runCatching {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM"))
        .withZone(ZoneId.systemDefault()).format(Instant.parse(instant))
}.getOrDefault(instant)

internal fun formatDuration(seconds: Long): String =
    "%02d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)

internal fun parseInstant(value: String?): Instant? = DueDates.parse(value)

/** Open and past due; a whole-day due date only counts once that day has passed. */
internal fun isOverdue(memory: MemoryItem): Boolean =
    memory.resolutionStatus == "OPEN" &&
        DueDates.parse(memory.dueAt)?.let { DueDates.isOverdue(it, Instant.now(), ZoneId.systemDefault()) } == true

private val InProgressStatuses = setOf("AUDIO_RECEIVED", "TRANSCRIBING", "TRANSCRIPTION_COMPLETE", "PROCESSING")
private const val STALE_AFTER_MINUTES = 10L

/** Failed conversations, and in-progress ones that have not moved for a while (e.g. server restarted). */
internal fun canRetry(session: ConversationSession): Boolean {
    val status = session.status.uppercase(Locale.ROOT)
    if (status == "FAILED") return true
    if (status !in InProgressStatuses) return false
    val updated = parseInstant(session.updatedAt) ?: return false
    return Duration.between(updated, Instant.now()).toMinutes() >= STALE_AFTER_MINUTES
}
