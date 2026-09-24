package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class IsoWeekTest {

    @Test
    fun `24 Sep 2026 is in week 39`() {
        LocalDate.of(2026, 9, 24).isoWeek() shouldBe IsoWeek(2026, 39)
    }

    @Test
    fun `week 39 of 2026 runs Monday 21 to Sunday 27 September`() {
        val w = IsoWeek(2026, 39)
        w.monday shouldBe LocalDate.of(2026, 9, 21)
        w.sunday shouldBe LocalDate.of(2026, 9, 27)
        w.days.size shouldBe 7
    }

    @Test
    fun `1 Jan 2026 (a Thursday) is in week 1 of 2026`() {
        LocalDate.of(2026, 1, 1).isoWeek() shouldBe IsoWeek(2026, 1)
        IsoWeek(2026, 1).monday shouldBe LocalDate.of(2025, 12, 29)
    }

    @Test
    fun `29 Dec 2025 belongs to week 1 of 2026`() {
        LocalDate.of(2025, 12, 29).isoWeek() shouldBe IsoWeek(2026, 1)
    }

    @Test
    fun `1 Jan 2027 (a Friday) belongs to week 53 of 2026`() {
        LocalDate.of(2027, 1, 1).isoWeek() shouldBe IsoWeek(2026, 53)
        IsoWeek(2026, 53).next() shouldBe IsoWeek(2027, 1)
    }

    @Test
    fun `next and previous cross year boundaries`() {
        IsoWeek(2026, 1).previous() shouldBe IsoWeek(2025, 52)
        IsoWeek(2026, 39).plusWeeks(2) shouldBe IsoWeek(2026, 41)
        IsoWeek(2026, 39).label() shouldBe "W39"
    }

    @Test
    fun `contains checks membership`() {
        (LocalDate.of(2026, 9, 27) in IsoWeek(2026, 39)) shouldBe true
        (LocalDate.of(2026, 9, 28) in IsoWeek(2026, 39)) shouldBe false
    }
}
