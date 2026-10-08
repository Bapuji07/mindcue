package com.secondmemory.android.data

import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Due-date rules shared by the UI, reminders and the morning summary. A due time of exactly
 * midnight (local) means a whole day ("by Friday"): it is not overdue until that day has passed,
 * and its reminder comes at 9:00 rather than in the middle of the night.
 */
object DueDates {
    private val DateOnlyReminderTime: LocalTime = LocalTime.of(9, 0)

    fun parse(value: String?): Instant? =
        value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    fun isDateOnly(due: Instant, zone: ZoneId): Boolean = due.atZone(zone).toLocalTime() == LocalTime.MIDNIGHT

    fun isOverdue(due: Instant, now: Instant, zone: ZoneId): Boolean =
        if (isDateOnly(due, zone)) due.atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate())
        else due.isBefore(now)

    fun isDueToday(due: Instant, now: Instant, zone: ZoneId): Boolean =
        due.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate() && !isOverdue(due, now, zone)

    fun reminderTime(due: Instant, zone: ZoneId): Instant =
        if (isDateOnly(due, zone)) due.atZone(zone).with(DateOnlyReminderTime).toInstant() else due
}
