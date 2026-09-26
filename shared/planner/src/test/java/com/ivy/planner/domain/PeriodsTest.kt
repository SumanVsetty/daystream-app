package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodsTest {
    private val today = LocalDate.of(2026, 9, 25)
    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 30)
    private fun d(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun `month keys`() {
        YearMonth.of(2026, 9).key() shouldBe 202609
        monthOf(202612) shouldBe YearMonth.of(2026, 12)
    }

    @Test
    fun `tasks and routines only count days that have happened`() {
        val entries = listOf(
            Entry("a", EntryKind.TASK, "Done", date = d(3), state = EntryState.DONE),
            Entry("b", EntryKind.TASK, "Open", date = d(10)),
            Entry("c", EntryKind.TASK, "Dropped", date = d(11), state = EntryState.DROPPED),
            Entry("d", EntryKind.TASK, "Future", date = d(28)),
            Entry("j", EntryKind.JOURNAL, "Big day", date = d(12), importance = 4),
        )
        val routine = Series("r", EntryKind.TASK, "Night routine", schedule = RepeatSchedule(RepeatRule.Daily(), d(20)), isRoutine = true)
        val records = mapOf("r" to listOf(
            OccurrenceRecord("r", d(20), EntryState.DONE),
            OccurrenceRecord("r", d(21), EntryState.DONE),
            OccurrenceRecord("r", d(22), EntryState.SKIPPED),
        ))
        val s = Periods.stats(from, to, today, entries, listOf(routine), records)
        s.tasksDone shouldBe 1
        s.tasksDue shouldBe 2
        // 20..25 = 6 days, one skipped: 5 due (today still open counts as due), 2 done
        s.routinesDue shouldBe 5
        s.routinesDone shouldBe 2
        s.memories shouldBe 1
        s.lifeChanging shouldBe 1
        s.taskPercent shouldBe 50
    }

    @Test
    fun `notable entry per day prefers journal, then importance`() {
        val entries = listOf(
            Entry("n", EntryKind.NOTE, "A note", date = d(4)),
            Entry("e", EntryKind.EVENT, "Kickoff", date = d(4)),
            Entry("j1", EntryKind.JOURNAL, "Small", date = d(12), importance = 1),
            Entry("j2", EntryKind.JOURNAL, "Big", date = d(12), importance = 3),
        )
        val m = Periods.notableByDay(from, to, entries)
        m[d(4)]?.id shouldBe "e"
        m[d(12)]?.id shouldBe "j2"
    }

    @Test
    fun `memories by month`() {
        val entries = listOf(
            Entry("a", EntryKind.JOURNAL, "x", date = LocalDate.of(2026, 1, 5)),
            Entry("b", EntryKind.JOURNAL, "y", date = LocalDate.of(2026, 9, 5)),
            Entry("c", EntryKind.JOURNAL, "z", date = LocalDate.of(2026, 9, 6)),
            Entry("d", EntryKind.NOTE, "note", date = LocalDate.of(2026, 9, 6)),
        )
        Periods.memoriesByMonth(2026, entries) shouldBe listOf(1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0)
    }
}
