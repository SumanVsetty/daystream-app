package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class MemoriesTest {
    private val today = LocalDate.of(2026, 9, 25)
    private fun j(id: String, title: String, date: LocalDate, importance: Int = 0) =
        Entry(id, EntryKind.JOURNAL, title, date = date, importance = importance)

    @Test
    fun `anniversaries of important moments come first`() {
        val entries = listOf(
            j("a", "Nyra's first steps", LocalDate.of(2024, 9, 25), importance = 1),
            j("b", "Founded TEDLinx", LocalDate.of(2012, 9, 25), importance = 4),
        )
        val m = Memories.pick(today, entries, emptyMap(), { null })
        m[0].kind shouldBe MemoryKind.ANNIVERSARY
        m[0].label shouldBe "14 years since"
        m[1].kind shouldBe MemoryKind.ON_THIS_DAY
        m[1].label shouldBe "2 years ago today"
    }

    @Test
    fun `this year's entries are not memories yet`() {
        Memories.pick(today, listOf(j("a", "Today", today)), emptyMap(), { null }) shouldBe emptyList()
    }

    @Test
    fun `coming up within a week`() {
        val entries = listOf(j("w", "Wedding", LocalDate.of(2011, 9, 28), importance = 4))
        val m = Memories.pick(today, entries, emptyMap(), { null }, yearlyEvents = listOf("Sundari Krishna's birthday" to today.plusDays(1)))
        // the wedding is already shown as "coming up", so it is not repeated as "From 2011"
        m.map { it.label } shouldBe listOf("Tomorrow", "In 3 days: 15 years since")
    }

    @Test
    fun `this week in past years uses the ISO week`() {
        // 22 Sep 2019 is in week 38; 23 Sep 2019 is in week 39 like 25 Sep 2026
        val entries = listOf(j("x", "Office move", LocalDate.of(2019, 9, 23)), j("y", "Other week", LocalDate.of(2019, 9, 22)))
        Memories.pick(today, entries, emptyMap(), { null }).first().label shouldBe "This week in 2019"
    }

    @Test
    fun `first memory with a person`() {
        val entries = listOf(j("p1", "Met Nyra", LocalDate.of(2023, 3, 1)), j("p2", "Later", LocalDate.of(2024, 3, 1)))
        val m = Memories.pick(today, entries, mapOf("p1" to listOf("nyra"), "p2" to listOf("nyra")), { "Nyra" })
        m.first { it.kind == MemoryKind.FIRST_WITH }.label shouldBe "Your first memory with Nyra, 3 years ago"
        m.first { it.kind == MemoryKind.FIRST_WITH }.entryId shouldBe "p1"
    }

    @Test
    fun `no duplicates and at most five`() {
        val entries = (1..10).map { j("e$it", "Memory $it", LocalDate.of(2010 + it, 9, 25), importance = 3) }
        val m = Memories.pick(today, entries, emptyMap(), { null })
        m.size shouldBe 5
        m.map { it.entryId }.toSet().size shouldBe 5
    }

    @Test
    fun `29 February is remembered on 28 February`() {
        val e = j("leap", "Leap day", LocalDate.of(2024, 2, 29), importance = 3)
        Memories.pick(LocalDate.of(2027, 2, 28), listOf(e), emptyMap(), { null }).first().kind shouldBe MemoryKind.ANNIVERSARY
    }
}
