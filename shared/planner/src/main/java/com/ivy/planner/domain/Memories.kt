package com.ivy.planner.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Why a memory is being shown today. */
enum class MemoryKind { ANNIVERSARY, ON_THIS_DAY, COMING_UP, THIS_WEEK, FIRST_WITH, FROM_A_YEAR }

/**
 * A resurfaced memory for the Journal tab's swipeable card.
 * [label] says why it's shown ("2 years ago today"); [entryId] is the memory itself.
 */
data class Memory(
    val kind: MemoryKind,
    val label: String,
    val entryId: String?,
    val title: String,
    val date: LocalDate?,
    val personId: String? = null,
)

/**
 * Picks today's memories, most meaningful first:
 * anniversaries of important moments, on this day, coming up, this week in past years,
 * a first memory with someone, and a memory from a past year.
 */
object Memories {
    const val MAX = 5

    private fun years(from: LocalDate, to: LocalDate) = ChronoUnit.YEARS.between(from, to).toInt()
    private fun ago(n: Int) = if (n == 1) "1 year ago" else "$n years ago"
    private fun sameDay(a: LocalDate, b: LocalDate) = a.monthValue == b.monthValue && a.dayOfMonth == b.dayOfMonth

    /** The date this year's anniversary of [d] falls on (29 Feb → 28 Feb in other years). */
    private fun anniversaryIn(year: Int, d: LocalDate): LocalDate =
        runCatching { d.withYear(year) }.getOrElse { LocalDate.of(year, d.monthValue, 28) }

    fun pick(
        today: LocalDate,
        entries: List<Entry>,
        peopleOf: Map<String, List<String>>,
        personName: (String) -> String?,
        yearlyEvents: List<Pair<String, LocalDate>> = emptyList(),
    ): List<Memory> {
        // memories are journal entries and events from earlier years
        val past = entries.filter { e ->
            (e.kind == EntryKind.JOURNAL || e.kind == EntryKind.EVENT) &&
                e.date != null && e.date.year < today.year && e.state != EntryState.DROPPED
        }
        val out = mutableListOf<Memory>()
        val used = mutableSetOf<String>()
        fun add(m: Memory) {
            if (out.size >= MAX) return
            if (m.entryId != null && !used.add(m.entryId)) return
            out += m
        }

        // 1. anniversaries of important and life-changing moments
        past.filter { it.importance >= Importance.IMPORTANT && sameDay(anniversaryIn(today.year, it.date!!), today) }
            .sortedByDescending { it.importance }
            .forEach { add(Memory(MemoryKind.ANNIVERSARY, "${years(it.date!!, today)} years since", it.id, it.title, it.date)) }

        // 2. on this day in earlier years
        past.filter { sameDay(anniversaryIn(today.year, it.date!!), today) }
            .sortedWith(compareByDescending<Entry> { it.importance }.thenBy { it.date })
            .forEach { add(Memory(MemoryKind.ON_THIS_DAY, "${ago(years(it.date!!, today))} today", it.id, it.title, it.date)) }

        // 3. coming up in the next 7 days: yearly events and anniversaries of important moments
        val upcoming = mutableListOf<Triple<Int, String, Memory>>()
        yearlyEvents.forEach { (title, next) ->
            val days = ChronoUnit.DAYS.between(today, next).toInt()
            if (days in 1..7) upcoming += Triple(days, title, Memory(MemoryKind.COMING_UP, inDays(days), null, title, next))
        }
        past.filter { it.importance >= Importance.IMPORTANT }.forEach { e ->
            var next = anniversaryIn(today.year, e.date!!)
            if (next.isBefore(today)) next = anniversaryIn(today.year + 1, e.date)
            val days = ChronoUnit.DAYS.between(today, next).toInt()
            if (days in 1..7) {
                upcoming += Triple(days, e.id, Memory(MemoryKind.COMING_UP, "${inDays(days)}: ${years(e.date, next)} years since", e.id, e.title, e.date))
            }
        }
        upcoming.sortedBy { it.first }.forEach { add(it.third) }

        // 4. this week in earlier years (same ISO week number)
        val week = today.isoWeek().week
        past.filter { it.date!!.isoWeek().week == week && !sameDay(anniversaryIn(today.year, it.date), today) }
            .sortedWith(compareByDescending<Entry> { it.importance }.thenByDescending { it.date })
            .take(1)
            .forEach { add(Memory(MemoryKind.THIS_WEEK, "This week in ${it.date!!.year}", it.id, it.title, it.date)) }

        // 5. a first memory with someone, rotating through people day by day
        val firsts = peopleOf.entries
            .flatMap { (entryId, people) -> people.map { it to entryId } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (personId, ids) ->
                val first = entries.filter { it.id in ids && it.date != null }.minByOrNull { it.date!! }
                first?.takeIf { it.date!!.year < today.year }?.let { personId to it }
            }
            .sortedBy { it.first }
        if (firsts.isNotEmpty()) {
            val (personId, e) = firsts[(today.toEpochDay() % firsts.size).toInt()]
            val name = personName(personId) ?: ""
            add(Memory(MemoryKind.FIRST_WITH, "Your first memory with $name, ${ago(years(e.date!!, today))}", e.id, e.title, e.date, personId))
        }

        // 6. a memory from a past year, favouring important ones; the pick changes daily
        val pool = past.filter { it.importance >= Importance.UNUSUAL }.ifEmpty { past }
        if (pool.isNotEmpty()) {
            val e = pool.sortedBy { it.id }[(today.toEpochDay() % pool.size).toInt()]
            add(Memory(MemoryKind.FROM_A_YEAR, "From ${e.date!!.year}", e.id, e.title, e.date))
        }
        return out
    }

    private fun inDays(days: Int) = when (days) {
        1 -> "Tomorrow"
        7 -> "Next week"
        else -> "In $days days"
    }
}
