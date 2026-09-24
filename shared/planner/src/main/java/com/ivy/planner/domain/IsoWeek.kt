package com.ivy.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * A week in the ISO 8601 system (the Swedish week numbering):
 * weeks start on Monday, and week 1 is the week containing the year's first Thursday.
 * Note that [year] is the week-based year, which can differ from the calendar year
 * for dates in late December or early January.
 */
data class IsoWeek(val year: Int, val week: Int) : Comparable<IsoWeek> {

    /** Monday of this week. */
    val monday: LocalDate
        get() = LocalDate.of(year, 1, 4) // Jan 4th is always in week 1
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Sunday of this week. */
    val sunday: LocalDate get() = monday.plusDays(6)

    /** All seven days, Monday to Sunday. */
    val days: List<LocalDate> get() = (0L..6L).map { monday.plusDays(it) }

    fun next(): IsoWeek = monday.plusWeeks(1).isoWeek()
    fun previous(): IsoWeek = monday.minusWeeks(1).isoWeek()
    fun plusWeeks(weeks: Long): IsoWeek = monday.plusWeeks(weeks).isoWeek()

    operator fun contains(date: LocalDate): Boolean = date.isoWeek() == this

    /** Short label, e.g. "W39". */
    fun label(): String = "W$week"

    override fun compareTo(other: IsoWeek): Int =
        compareValuesBy(this, other, { it.year }, { it.week })

    companion object {
        fun of(date: LocalDate): IsoWeek = date.isoWeek()
    }
}

fun LocalDate.isoWeek(): IsoWeek = IsoWeek(
    year = get(IsoFields.WEEK_BASED_YEAR),
    week = get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
)

/** Monday of the ISO week containing this date. */
fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
