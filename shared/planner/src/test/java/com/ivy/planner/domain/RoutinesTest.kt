package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test

class RoutinesTest {
    private val steps = listOf(
        RoutineStep("a", 0, "Warm-up", timer = TimerType.COUNTDOWN, timerSeconds = 300),
        RoutineStep("b", 1, "Push-ups", timer = TimerType.SETS, sets = 3, timerSeconds = 60),
        RoutineStep("c", 2, "Plan tomorrow"),
    )

    @Test
    fun `progress counts, next step and completion`() {
        val p = RoutineProgress(steps, mapOf("a" to StepState.DONE))
        p.label() shouldBe "1 of 3 steps"
        p.nextIndex shouldBe 1
        p.isComplete shouldBe false
        val all = RoutineProgress(steps, mapOf("a" to StepState.DONE, "b" to StepState.SKIPPED, "c" to StepState.DONE))
        all.isComplete shouldBe true
        all.nextIndex shouldBe null
        all.done shouldBe 2
        all.skipped shouldBe 1
    }

    @Test
    fun `resuming skips steps already handled`() {
        RoutineProgress(steps, mapOf("b" to StepState.DONE)).nextIndex shouldBe 0
    }

    @Test
    fun `estimated length from timers`() {
        // 5 min + (3 sets × 1 min + 2 rests × 1 min) + 1 min = 11 min
        Routines.estimatedMinutes(steps) shouldBe 11
        Routines.estimatedMinutes(emptyList()) shouldBe null
    }

    @Test
    fun `snapshot keeps the steps as they were`() {
        val snap = Routines.snapshot(RoutineProgress(steps, mapOf("a" to StepState.DONE, "b" to StepState.SKIPPED)))
        Routines.readSnapshot(snap) shouldBe listOf(
            "Warm-up" to StepState.DONE,
            "Push-ups" to StepState.SKIPPED,
            "Plan tomorrow" to null,
        )
    }

    @Test
    fun `timer facts and clock`() {
        Routines.timerFacts(steps[0]) shouldBe listOf("5 min")
        Routines.timerFacts(steps[1]) shouldBe listOf("3 sets", "1 min rest")
        Routines.timerFacts(steps[2]) shouldBe emptyList()
        Routines.clock(65) shouldBe "1:05"
    }

    @Test
    fun `checklist from description lines`() {
        var n = 0
        val items = checklistFromLines("BEL Kotdwar PBG\n- Chiprime advance\n\n2. Centum quote\n• IDFC OD") { "i${n++}" }
        items.map { it.text } shouldBe listOf("BEL Kotdwar PBG", "Chiprime advance", "Centum quote", "IDFC OD")
        items.map { it.id } shouldBe listOf("i0", "i1", "i2", "i3")
        listOf(ChecklistItem("a", "x", true), ChecklistItem("b", "y")).progressLabel() shouldBe "1 of 2"
    }
}
