package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlannerTest {
    private val today = LocalDate.of(2026, 9, 24)
    private val daily = Series(
        id = "s1",
        kind = EntryKind.TASK,
        title = "Daily Followup",
        time = LocalTime.of(18, 0),
        schedule = RepeatSchedule(RepeatRule.Daily(), LocalDate.of(2026, 9, 1)),
    )

    @Test
    fun `an unticked past occurrence is missed, not overdue`() {
        Planner.occurrenceState(today.minusDays(1), null, today) shouldBe EntryState.MISSED
        Planner.occurrenceState(today, null, today) shouldBe EntryState.OPEN
        val done = OccurrenceRecord("s1", today.minusDays(1), EntryState.DONE)
        Planner.occurrenceState(today.minusDays(1), done, today) shouldBe EntryState.DONE
    }

    @Test
    fun `day items merge one-offs and occurrences, all-day first then by time`() {
        val entries = listOf(
            Entry("e1", EntryKind.TASK, "HAL HYD A818 bid", date = today, time = LocalTime.of(10, 0)),
            Entry("e2", EntryKind.EVENT, "Birthday", date = today),
            Entry("e3", EntryKind.TASK, "Dropped", date = today, state = EntryState.DROPPED),
        )
        val items = Planner.dayItems(today, today, entries, listOf(daily), emptyMap())
        items.map { it.title } shouldBe listOf("Birthday", "HAL HYD A818 bid", "Daily Followup")
    }

    @Test
    fun `only-today overrides replace the master's title and time`() {
        val record = OccurrenceRecord("s1", today, EntryState.OPEN, titleOverride = "Followup + Centum", timeOverride = LocalTime.of(20, 0))
        val item = Planner.dayItems(today, today, emptyList(), listOf(daily), mapOf("s1" to listOf(record)))
            .single() as DayItem.Occurrence
        item.title shouldBe "Followup + Centum"
        item.sortTime shouldBe LocalTime.of(20, 0)
    }

    @Test
    fun `paused series don't appear`() {
        Planner.dayItems(today, today, emptyList(), listOf(daily.copy(paused = true)), emptyMap()) shouldBe emptyList()
    }

    @Test
    fun `overdue contains only open one-off tasks from earlier days`() {
        val entries = listOf(
            Entry("a", EntryKind.TASK, "Ametek", date = today.minusDays(8)),
            Entry("b", EntryKind.TASK, "Done", date = today.minusDays(2), state = EntryState.DONE),
            Entry("c", EntryKind.EVENT, "Old event", date = today.minusDays(2)),
            Entry("d", EntryKind.TASK, "Today", date = today),
        )
        Planner.overdue(entries, today).map { it.id } shouldBe listOf("a")
    }

    @Test
    fun `consistency counts done of scheduled, excluding skipped and unfinished today`() {
        val records = listOf(
            OccurrenceRecord("s1", today.minusDays(1), EntryState.DONE),
            OccurrenceRecord("s1", today.minusDays(2), EntryState.SKIPPED),
            OccurrenceRecord("s1", today.minusDays(3), EntryState.DONE),
        )
        Planner.consistency(daily, records, today, days = 5) shouldBe Consistency(done = 2, scheduled = 3)
    }

    @Test
    fun `after-completion task shows on its due date, then on today when late`() {
        val water = Series("w", EntryKind.TASK, "Water plants", schedule = RepeatSchedule(RepeatRule.AfterCompletion(3), LocalDate.of(2026, 9, 10)))
        val records = mapOf("w" to listOf(OccurrenceRecord("w", LocalDate.of(2026, 9, 18), EntryState.DONE)))
        // due 21 Sep, not done: shows on today (24th) marked late, not on the 21st
        Planner.dayItems(LocalDate.of(2026, 9, 21), today, emptyList(), listOf(water), records) shouldBe emptyList()
        val late = Planner.dayItems(today, today, emptyList(), listOf(water), records).single() as DayItem.Occurrence
        late.state shouldBe EntryState.OPEN
        late.dueSince shouldBe LocalDate.of(2026, 9, 21)
        // the completed one still shows on the day it was done
        val past = Planner.dayItems(LocalDate.of(2026, 9, 18), today, emptyList(), listOf(water), records).single() as DayItem.Occurrence
        past.state shouldBe EntryState.DONE
    }

    @Test
    fun `week tasks and review candidates`() {
        val w39 = IsoWeek(2026, 39)
        val entries = listOf(
            Entry("wk", EntryKind.TASK, "Centum quote", week = w39),
            Entry("dy", EntryKind.TASK, "Ametek", date = LocalDate.of(2026, 9, 23)),
            Entry("nx", EntryKind.TASK, "Next week", week = w39.next()),
            Entry("dn", EntryKind.TASK, "Done", date = LocalDate.of(2026, 9, 22), state = EntryState.DONE),
        )
        Planner.weekTasks(entries, w39).map { it.id } shouldBe listOf("wk")
        Planner.reviewCandidates(entries, w39).map { it.id } shouldBe listOf("dy", "wk")
    }

    @Test
    fun `a day of a repeating task moved to tomorrow shows there, and isn't missed`() {
        val sat = LocalDate.of(2026, 9, 26)
        val sun = sat.plusDays(1)
        val s = Series("hair", EntryKind.TASK, "Saturday hair routine", schedule = RepeatSchedule(RepeatRule.Weekly(1, setOf(java.time.DayOfWeek.SATURDAY)), sat), isRoutine = true)
        val moved = OccurrenceRecord("hair", sat, EntryState.OPEN, movedTo = sun)
        val records = mapOf("hair" to listOf(moved))
        Planner.dayItems(sat, sat, emptyList(), listOf(s), records) shouldBe emptyList()
        val onSunday = Planner.dayItems(sun, sat, emptyList(), listOf(s), records).single() as DayItem.Occurrence
        onSunday.date shouldBe sat
        onSunday.shownOn shouldBe sun
        onSunday.moved shouldBe true
        // on Sunday itself it's still open, not missed
        Planner.occurrenceState(sat, moved, sun) shouldBe EntryState.OPEN
        // and missed only after the day it was moved to
        Planner.occurrenceState(sat, moved, sun.plusDays(1)) shouldBe EntryState.MISSED
    }
}
