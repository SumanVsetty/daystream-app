package com.ivy.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Stores repeat rules as short, stable strings in the database and
 * describes them in plain English for the UI.
 *
 * Rule formats: "D;1"  "W;2;2,4"  "MD;1;24"  "MW;1;4;4"  "ML;1"  "AC;3"
 * End formats:  "N"    "U;2026-12-31"   "C;10"
 */
object RepeatCodec {

    fun encodeRule(rule: RepeatRule): String = when (rule) {
        is RepeatRule.Daily -> "D;${rule.interval}"
        is RepeatRule.Weekly ->
            "W;${rule.interval};" + rule.days.sortedBy { it.value }.joinToString(",") { it.value.toString() }
        is RepeatRule.MonthlyOnDay -> "MD;${rule.interval};${rule.dayOfMonth}"
        is RepeatRule.MonthlyOnWeekday -> "MW;${rule.interval};${rule.nth};${rule.dayOfWeek.value}"
        is RepeatRule.MonthlyLastDay -> "ML;${rule.interval}"
        is RepeatRule.AfterCompletion -> "AC;${rule.interval}"
    }

    fun decodeRule(value: String): RepeatRule? = runCatching {
        val p = value.split(";")
        val interval = p[1].toInt()
        when (p[0]) {
            "D" -> RepeatRule.Daily(interval)
            "W" -> RepeatRule.Weekly(interval, p[2].split(",").map { DayOfWeek.of(it.toInt()) }.toSet())
            "MD" -> RepeatRule.MonthlyOnDay(interval, p[2].toInt())
            "MW" -> RepeatRule.MonthlyOnWeekday(interval, p[2].toInt(), DayOfWeek.of(p[3].toInt()))
            "ML" -> RepeatRule.MonthlyLastDay(interval)
            "AC" -> RepeatRule.AfterCompletion(interval)
            else -> null
        }
    }.getOrNull()

    fun encodeEnd(end: RepeatEnd): String = when (end) {
        RepeatEnd.Never -> "N"
        is RepeatEnd.OnDate -> "U;${end.lastDate}"
        is RepeatEnd.AfterCount -> "C;${end.count}"
    }

    fun decodeEnd(value: String?): RepeatEnd = runCatching {
        val p = value!!.split(";")
        when (p[0]) {
            "U" -> RepeatEnd.OnDate(LocalDate.parse(p[1]))
            "C" -> RepeatEnd.AfterCount(p[1].toInt())
            else -> RepeatEnd.Never
        }
    }.getOrDefault(RepeatEnd.Never)

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val ordinals = mapOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", -1 to "last")
    private val endDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** Plain-English description, e.g. "Every 2 weeks on Tuesday and Thursday". */
    fun describe(schedule: RepeatSchedule): String =
        if (schedule.isOnce) "Once" else describe(schedule.rule) + describeEnd(schedule.end)

    fun describe(rule: RepeatRule): String {
        val n = rule.interval
        return when (rule) {
            is RepeatRule.Daily -> if (n == 1) "Every day" else "Every $n days"
            is RepeatRule.Weekly -> {
                if (n == 1 && rule.days == weekdays) return "Every weekday"
                if (n == 1 && rule.days.size == 7) return "Every day"
                val days = joinNames(rule.days.sortedBy { it.value }.map(::dayName))
                if (n == 1) "Every week on $days" else "Every $n weeks on $days"
            }
            is RepeatRule.MonthlyOnDay ->
                if (n == 12) "Every year" else "${everyMonths(n)} on day ${rule.dayOfMonth}"
            is RepeatRule.MonthlyOnWeekday ->
                "${everyMonths(n)} on the ${ordinals[rule.nth]} ${dayName(rule.dayOfWeek)}"
            is RepeatRule.MonthlyLastDay -> "${everyMonths(n)} on the last day"
            is RepeatRule.AfterCompletion ->
                if (n == 1) "1 day after completion" else "$n days after completion"
        }
    }

    private fun everyMonths(n: Int) = if (n == 1) "Every month" else "Every $n months"

    private fun describeEnd(end: RepeatEnd): String = when (end) {
        RepeatEnd.Never -> ""
        is RepeatEnd.OnDate -> ", until ${end.lastDate.format(endDate)}"
        is RepeatEnd.AfterCount -> if (end.count == 1) ", once" else ", ${end.count} times"
    }

    /**
     * The quick choices shown in the Repeat menu, adapted to the task's date,
     * like "Every week on Thursday" for a Thursday task.
     */
    fun quickChoices(date: LocalDate): List<RepeatRule> {
        val nth = (date.dayOfMonth - 1) / 7 + 1
        return listOf(
            RepeatRule.Daily(1),
            RepeatRule.Weekly(1, weekdays),
            RepeatRule.Weekly(1, setOf(date.dayOfWeek)),
            RepeatRule.Weekly(2, setOf(date.dayOfWeek)),
            RepeatRule.MonthlyOnDay(1, date.dayOfMonth),
        ) + (if (nth <= 4) listOf(RepeatRule.MonthlyOnWeekday(1, nth, date.dayOfWeek)) else emptyList()) +
            RepeatRule.MonthlyOnDay(12, date.dayOfMonth)
    }
}
