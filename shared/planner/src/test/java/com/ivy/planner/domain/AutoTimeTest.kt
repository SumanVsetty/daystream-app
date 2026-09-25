package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AutoTimeTest {
    private val today = LocalDate.of(2026, 9, 24)
    private val tomorrow = today.plusDays(1)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `an empty future day starts at 8`() {
        AutoTime.findSlot(tomorrow, today, t(15), emptyList()) shouldBe t(8)
    }

    @Test
    fun `today starts from now, rounded up to the quarter hour`() {
        AutoTime.findSlot(today, today, t(14, 7), emptyList()) shouldBe t(14, 15)
        AutoTime.findSlot(today, today, t(6, 30), emptyList()) shouldBe t(8)
    }

    @Test
    fun `skips busy stretches`() {
        val busy = listOf(AutoTime.Busy(t(8), 30), AutoTime.Busy(t(8, 30), 15), AutoTime.Busy(t(9), 60))
        AutoTime.findSlot(tomorrow, today, t(0), busy) shouldBe t(8, 45)
        AutoTime.findSlot(tomorrow, today, t(0), busy, duration = 30) shouldBe t(10)
    }

    @Test
    fun `a full day falls back to the last slot before midnight`() {
        val busy = listOf(AutoTime.Busy(t(8), 16 * 60))
        AutoTime.findSlot(tomorrow, today, t(0), busy) shouldBe null
        AutoTime.assign(tomorrow, today, t(0), busy) shouldBe t(23, 45)
    }

    @Test
    fun `a task must end by midnight`() {
        AutoTime.findSlot(today, today, t(23, 50), emptyList()) shouldBe null
    }
}
