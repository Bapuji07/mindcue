package com.secondmemory.android.reminders

import com.secondmemory.android.data.DueDates
import com.secondmemory.android.data.MemoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    // Thursday 8 Oct 2026, 10:00 in India.
    private val now: Instant = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, zone).toInstant()

    private fun at(day: Int, hour: Int, minute: Int = 0): String =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toOffsetDateTime().toString()

    private fun task(id: String, due: String?, status: String = "OPEN") = MemoryItem(
        type = "TASK", title = "Task $id", content = "Details $id", resolutionStatus = status,
        dueAt = due, importance = null, confidence = null, id = id
    )

    @Test
    fun remindsAtTheDueTimeOfUpcomingOpenTasksSoonestFirst() {
        val plan = ReminderPlanner.plan(
            listOf(task("later", at(9, 15)), task("soon", at(8, 18)), task("done", at(8, 19), status = "DONE")),
            now, zone
        )
        assertEquals(listOf("soon", "later"), plan.map { it.memoryId })
        assertEquals(DueDates.parse(at(8, 18))!!.toEpochMilli(), plan.first().atMillis)
    }

    @Test
    fun wholeDayDueDatesRemindAtNineInsteadOfMidnight() {
        val plan = ReminderPlanner.plan(listOf(task("friday", at(9, 0))), now, zone)
        assertEquals(DueDates.parse(at(9, 9))!!.toEpochMilli(), plan.single().atMillis)
    }

    @Test
    fun skipsPastDueUndatedAndFarFutureTasks() {
        val plan = ReminderPlanner.plan(
            listOf(task("past", at(8, 9)), task("undated", null), task("next-month", "2026-12-01T10:00:00+05:30")),
            now, zone
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun keepsAtMostFiftyReminders() {
        val many = (1..80).map { task("t$it", ZonedDateTime.of(2026, 10, 9, 0, 0, 0, 0, zone).plusMinutes(it.toLong() * 7).toOffsetDateTime().toString()) }
        assertEquals(ReminderPlanner.MAX_REMINDERS, ReminderPlanner.plan(many, now, zone).size)
    }

    @Test
    fun digestSplitsTodayFromOverdueAndIgnoresTheRest() {
        val digest = ReminderPlanner.digest(
            listOf(
                task("today-later", at(8, 17)),
                task("today-whole-day", at(8, 0)),
                task("overdue-this-morning", at(8, 9)),
                task("overdue-yesterday", at(7, 0)),
                task("tomorrow", at(9, 12)),
                task("undated", null)
            ),
            now, zone
        )
        assertEquals(listOf("today-whole-day", "today-later"), digest.dueToday.map { it.id })
        assertEquals(listOf("overdue-yesterday", "overdue-this-morning"), digest.overdue.map { it.id })
        assertFalse(digest.isEmpty)
    }

    @Test
    fun wholeDayTasksAreOnlyOverdueOnceTheDayHasPassed() {
        val todayWholeDay = DueDates.parse(at(8, 0))!!
        assertFalse(DueDates.isOverdue(todayWholeDay, now, zone))
        assertTrue(DueDates.isOverdue(DueDates.parse(at(7, 0))!!, now, zone))
    }

    @Test
    fun nextSummaryIsTodayIfStillAheadOtherwiseTomorrow() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, zone).toInstant(),
            ReminderPlanner.nextDigestTime(now, zone, 8 * 60)
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 8, 18, 30, 0, 0, zone).toInstant(),
            ReminderPlanner.nextDigestTime(now, zone, 18 * 60 + 30)
        )
    }

    @Test
    fun anAlarmFiringOnTimeSchedulesTheNextDay() {
        val firing = ZonedDateTime.of(2026, 10, 8, 8, 0, 5, 0, zone).toInstant()
        assertEquals(
            ZonedDateTime.of(2026, 10, 9, 8, 0, 0, 0, zone).toInstant(),
            ReminderPlanner.nextDigestTime(firing, zone, 8 * 60)
        )
    }
}
