package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate

class RepeatCodecTest {

    @Test
    fun `rules survive an encode-decode round trip`() {
        val rules = listOf(
            RepeatRule.Daily(1),
            RepeatRule.Weekly(2, setOf(TUESDAY, THURSDAY)),
            RepeatRule.MonthlyOnDay(1, 24),
            RepeatRule.MonthlyOnWeekday(1, 4, THURSDAY),
            RepeatRule.MonthlyOnWeekday(3, RepeatRule.MonthlyOnWeekday.LAST, MONDAY),
            RepeatRule.MonthlyLastDay(1),
            RepeatRule.AfterCompletion(3),
        )
        rules.forEach { RepeatCodec.decodeRule(RepeatCodec.encodeRule(it)) shouldBe it }
    }

    @Test
    fun `ends survive a round trip and bad input is safe`() {
        val ends = listOf(RepeatEnd.Never, RepeatEnd.OnDate(LocalDate.of(2026, 12, 31)), RepeatEnd.AfterCount(10))
        ends.forEach { RepeatCodec.decodeEnd(RepeatCodec.encodeEnd(it)) shouldBe it }
        RepeatCodec.decodeEnd(null) shouldBe RepeatEnd.Never
        RepeatCodec.decodeRule("garbage") shouldBe null
    }

    @Test
    fun `plain English descriptions`() {
        RepeatCodec.describe(RepeatRule.Daily(1)) shouldBe "Every day"
        RepeatCodec.describe(RepeatRule.Daily(2)) shouldBe "Every 2 days"
        RepeatCodec.describe(RepeatRule.Weekly(2, setOf(THURSDAY, TUESDAY))) shouldBe
            "Every 2 weeks on Tuesday and Thursday"
        RepeatCodec.describe(RepeatRule.Weekly(1, setOf(THURSDAY))) shouldBe "Every week on Thursday"
        RepeatCodec.describe(RepeatRule.MonthlyOnWeekday(1, 4, THURSDAY)) shouldBe
            "Every month on the fourth Thursday"
        RepeatCodec.describe(RepeatRule.AfterCompletion(3)) shouldBe "3 days after completion"
        RepeatCodec.describe(
            RepeatSchedule(RepeatRule.Daily(), LocalDate.of(2026, 9, 24), RepeatEnd.OnDate(LocalDate.of(2026, 12, 31))),
        ) shouldBe "Every day, until 31 Dec 2026"
    }

    @Test
    fun `yearly repeats land on the same date each year`() {
        val s = RepeatSchedule(RepeatRule.MonthlyOnDay(12, 24), LocalDate.of(2026, 9, 24))
        s.preview(LocalDate.of(2026, 9, 1), 3) shouldBe
            listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2027, 9, 24), LocalDate.of(2028, 9, 24))
    }

    @Test
    fun `every weekday is recognised`() {
        val weekdays = RepeatCodec.quickChoices(LocalDate.of(2026, 9, 24))[1]
        RepeatCodec.describe(weekdays) shouldBe "Every weekday"
    }

    @Test
    fun `quick choices adapt to the date`() {
        RepeatCodec.quickChoices(LocalDate.of(2026, 9, 24)).map(RepeatCodec::describe) shouldBe listOf(
            "Every day",
            "Every weekday",
            "Every week on Thursday",
            "Every 2 weeks on Thursday",
            "Every month on day 24",
            "Every month on the fourth Thursday",
            "Every year",
        )
    }
}
