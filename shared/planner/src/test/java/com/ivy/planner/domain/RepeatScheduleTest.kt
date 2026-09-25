package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate

class RepeatScheduleTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun `daily repeats every day from the start`() {
        val s = RepeatSchedule(RepeatRule.Daily(), start = d(9, 24))
        s.occursOn(d(9, 23)) shouldBe false
        s.occursOn(d(9, 24)) shouldBe true
        s.occursOn(d(12, 31)) shouldBe true
    }

    @Test
    fun `every 3 days`() {
        val s = RepeatSchedule(RepeatRule.Daily(3), start = d(9, 24))
        s.occurrencesBetween(d(9, 24), d(10, 3)) shouldBe listOf(d(9, 24), d(9, 27), d(9, 30), d(10, 3))
    }

    @Test
    fun `every 2 weeks on Tuesday and Thursday matches the mockup preview`() {
        val s = RepeatSchedule(RepeatRule.Weekly(2, setOf(TUESDAY, THURSDAY)), start = d(9, 24))
        s.preview(d(9, 24), 5) shouldBe listOf(d(9, 24), d(10, 6), d(10, 8), d(10, 20), d(10, 22))
    }

    @Test
    fun `every weekday skips weekends`() {
        val s = RepeatSchedule(RepeatRule.Weekly(1, setOf(MONDAY, TUESDAY, java.time.DayOfWeek.WEDNESDAY, THURSDAY, FRIDAY)), d(9, 24))
        s.occurrencesBetween(d(9, 24), d(9, 30)) shouldBe listOf(d(9, 24), d(9, 25), d(9, 28), d(9, 29), d(9, 30))
    }

    @Test
    fun `monthly on day 31 uses the last day of short months`() {
        val s = RepeatSchedule(RepeatRule.MonthlyOnDay(1, 31), start = d(1, 31))
        s.occursOn(d(2, 28)) shouldBe true
        s.occursOn(d(4, 30)) shouldBe true
        s.occursOn(d(5, 31)) shouldBe true
        s.occursOn(d(5, 30)) shouldBe false
    }

    @Test
    fun `fourth Thursday of the month`() {
        val s = RepeatSchedule(RepeatRule.MonthlyOnWeekday(1, 4, THURSDAY), start = d(9, 1))
        s.preview(d(9, 1), 3) shouldBe listOf(d(9, 24), d(10, 22), d(11, 26))
    }

    @Test
    fun `last Friday of the month`() {
        val s = RepeatSchedule(RepeatRule.MonthlyOnWeekday(1, RepeatRule.MonthlyOnWeekday.LAST, FRIDAY), d(9, 1))
        s.preview(d(9, 1), 3) shouldBe listOf(d(9, 25), d(10, 30), d(11, 27))
    }

    @Test
    fun `every 2 months on the last day`() {
        val s = RepeatSchedule(RepeatRule.MonthlyLastDay(2), start = d(1, 15))
        s.preview(d(1, 1), 3) shouldBe listOf(d(1, 31), d(3, 31), d(5, 31))
    }

    @Test
    fun `ends on a date (inclusive)`() {
        val s = RepeatSchedule(RepeatRule.Daily(), d(9, 24), RepeatEnd.OnDate(d(9, 26)))
        s.occurrencesBetween(d(9, 1), d(9, 30)) shouldBe listOf(d(9, 24), d(9, 25), d(9, 26))
    }

    @Test
    fun `ends after a number of times`() {
        val s = RepeatSchedule(RepeatRule.Weekly(1, setOf(THURSDAY)), d(9, 24), RepeatEnd.AfterCount(3))
        s.occurrencesBetween(d(9, 1), d(12, 31)) shouldBe listOf(d(9, 24), d(10, 1), d(10, 8))
    }

    @Test
    fun `after-completion schedules from the last completion`() {
        val s = RepeatSchedule(RepeatRule.AfterCompletion(3), d(9, 24))
        s.isCalendarBased shouldBe false
        s.occursOn(d(9, 24)) shouldBe false
        s.dueAfterCompletion(null) shouldBe d(9, 24)
        s.dueAfterCompletion(d(9, 26)) shouldBe d(9, 29)
    }

    @Test
    fun `a one-off schedule happens once`() {
        val s = RepeatSchedule.once(d(9, 26))
        s.isOnce shouldBe true
        s.occurrencesBetween(d(9, 1), d(10, 31)) shouldBe listOf(d(9, 26))
        RepeatCodec.describe(s) shouldBe "Once"
        RepeatSchedule(RepeatRule.Daily(), d(9, 26)).isOnce shouldBe false
    }
}
