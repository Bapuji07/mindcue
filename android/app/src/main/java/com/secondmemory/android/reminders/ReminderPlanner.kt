package com.secondmemory.android.reminders

import com.secondmemory.android.data.DueDates
import com.secondmemory.android.data.MemoryItem
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A reminder to show for [memoryId] at [atMillis] (epoch ms). */
data class PlannedReminder(val memoryId: String, val title: String, val text: String, val atMillis: Long)

/** What the morning summary lists: open items due today, and open items already overdue. */
data class Digest(val dueToday: List<MemoryItem>, val overdue: List<MemoryItem>) {
    val isEmpty: Boolean get() = dueToday.isEmpty() && overdue.isEmpty()

    /** Overdue first, then today's, each soonest first: the order the Home card shows them. */
    val all: List<MemoryItem> get() = overdue + dueToday
}

/** Pure decisions about reminders and the summary, kept free of Android so they are unit-tested. */
object ReminderPlanner {
    /** Android limits how many alarms an app may hold; the nearest 50 are plenty. */
    const val MAX_REMINDERS = 50
    private val Horizon: Duration = Duration.ofDays(30)

    /** Open items with a future reminder time within the next 30 days, soonest first. */
    fun plan(memories: List<MemoryItem>, now: Instant, zone: ZoneId): List<PlannedReminder> =
        memories.asSequence()
            .filter { it.id != null && it.resolutionStatus == "OPEN" }
            .mapNotNull { memory -> DueDates.parse(memory.dueAt)?.let { memory to DueDates.reminderTime(it, zone) } }
            .filter { (_, at) -> at.isAfter(now) && at.isBefore(now.plus(Horizon)) }
            .sortedBy { (_, at) -> at }
            .take(MAX_REMINDERS)
            .map { (memory, at) -> PlannedReminder(memory.id!!, memory.title, memory.content, at.toEpochMilli()) }
            .toList()

    fun digest(memories: List<MemoryItem>, now: Instant, zone: ZoneId): Digest {
        val dated = memories
            .filter { it.resolutionStatus == "OPEN" }
            .mapNotNull { memory -> DueDates.parse(memory.dueAt)?.let { memory to it } }
            .sortedBy { (_, due) -> due }
        return Digest(
            dueToday = dated.filter { (_, due) -> DueDates.isDueToday(due, now, zone) }.map { it.first },
            overdue = dated.filter { (_, due) -> DueDates.isOverdue(due, now, zone) }.map { it.first }
        )
    }

    /** The next time the summary should arrive: today at [minuteOfDay] if that is still ahead, else tomorrow. */
    fun nextDigestTime(now: Instant, zone: ZoneId, minuteOfDay: Int): Instant {
        val local = now.atZone(zone)
        val today = local.with(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        // A minute of slack so an alarm firing at 8:00:00 doesn't schedule itself again for 8:00 today.
        val next = if (today.isAfter(local.plusMinutes(1))) today else today.plusDays(1)
        return next.toInstant()
    }
}
