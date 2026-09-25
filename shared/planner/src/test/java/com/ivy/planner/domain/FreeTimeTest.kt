package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalTime

class FreeTimeTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `gaps between busy stretches`() {
        val busy = listOf(t(20) to 15, t(20, 15) to 30, t(21, 30) to 15, t(22) to 15)
        FreeTime.gaps(t(19, 15), busy) shouldBe listOf(FreeTime.Gap(t(19, 15), t(20)), FreeTime.Gap(t(20, 45), t(21, 30)))
    }

    @Test
    fun `short gaps are ignored and overlaps handled`() {
        val busy = listOf(t(10) to 60, t(10, 30) to 60, t(11, 50) to 15)
        FreeTime.gaps(t(10), busy, minMinutes = 30) shouldBe emptyList()
    }

    @Test
    fun `evening gap only when asked`() {
        FreeTime.gaps(t(22), listOf(t(22) to 15)) shouldBe emptyList()
        FreeTime.gaps(t(22), listOf(t(22) to 15), includeEvening = true) shouldBe listOf(FreeTime.Gap(t(22, 15), null))
    }

    @Test
    fun `countdown`() {
        FreeTime.countdown(t(18, 35), t(19)) shouldBe "In 25 min"
        FreeTime.countdown(t(17), t(19, 10)) shouldBe "In 2 h 10 min"
        FreeTime.countdown(t(17), t(19)) shouldBe "In 2 h"
        FreeTime.countdown(t(19, 5), t(19)) shouldBe "Now"
    }
}
