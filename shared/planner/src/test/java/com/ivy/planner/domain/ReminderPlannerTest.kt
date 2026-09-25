package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val now = LocalDateTime.of(2026, 9, 25, 18, 0)
    private fun target(id: String, date: LocalDate, time: LocalTime) =
        ReminderTarget(id, false, date, time, "Call Sunil", EntryKind.TASK, null)

    @Test
    fun `reminders before the time, only in the future and within the window`() {
        val t = target("a", now.toLocalDate(), LocalTime.of(19, 0))
        val alarms = ReminderPlanner.plan(now, listOf(t), mapOf("a" to listOf(0, 10, 120)), emptyMap())
        alarms.map { it.fireAt.toLocalTime() } shouldBe listOf(LocalTime.of(18, 50), LocalTime.of(19, 0))
    }

    @Test
    fun `nothing beyond 48 hours`() {
        val t = target("b", now.toLocalDate().plusDays(3), LocalTime.of(9, 0))
        ReminderPlanner.plan(now, listOf(t), mapOf("b" to listOf(0)), emptyMap()) shouldBe emptyList()
    }

    @Test
    fun `a snooze replaces the day's reminders`() {
        val t = target("c", now.toLocalDate(), LocalTime.of(19, 0))
        val snooze = now.plusMinutes(30)
        val alarms = ReminderPlanner.plan(now, listOf(t), mapOf("c" to listOf(0, 10)), mapOf(t.key to snooze))
        alarms.size shouldBe 1
        alarms[0].fireAt shouldBe snooze
        alarms[0].snoozed shouldBe true
    }

    @Test
    fun `labels and notification text`() {
        ReminderPlanner.label(0) shouldBe "At the time"
        ReminderPlanner.label(10) shouldBe "10 min before"
        ReminderPlanner.label(120) shouldBe "2 hours before"
        ReminderPlanner.label(1440) shouldBe "1 day before"
        val t = target("d", now.toLocalDate(), LocalTime.of(18, 10))
        ReminderPlanner.text(ReminderAlarm(t.key, t, now, 10), now) shouldBe "In 10 min · 18:10 · 15 min"
        val tomorrow = target("e", now.toLocalDate().plusDays(1), LocalTime.of(9, 0))
        ReminderPlanner.text(ReminderAlarm(tomorrow.key, tomorrow, now, 1440), now) shouldBe "Tomorrow · 09:00 · 15 min"
    }
}
