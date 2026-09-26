package com.ivy.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Understands a rapid-log line like "call Mr. Sunil regd L&T project today at 7pm for 30 min".
 * Works fully offline. Recognised parts are removed from the title.
 *
 * Kind: "- " note, "o " event, otherwise a task.
 * Day: today, tonight, tomorrow, day after tomorrow, (on/next/this) monday…, 28 sep, sep 28, 28/9,
 * and with a year for past or future dates: 29 oct 2010, oct 29 2010, 29/10/2010.
 * Time: 7pm, 7:30 pm, 19:30, at 19, at 7 (1–7 means pm), noon, morning, afternoon, evening.
 * Duration: for 30 min, for 1 hour, for 1.5 hours, for 45m.
 */
object RapidLogParser {

    data class Result(
        val kind: EntryKind,
        val title: String,
        val date: LocalDate?,
        val time: LocalTime?,
        val durationMinutes: Int?,
        /** Hashtags, e.g. "#quotes" → "quotes": the board or collection to put it in. */
        val tags: List<String> = emptyList(),
        /** "! " at the start: one of today's 3. */
        val focus: Boolean = false,
    ) {
        val understoodSomething get() = date != null || time != null || durationMinutes != null
    }

    private val days = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val months = mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH, "apr" to Month.APRIL,
        "may" to Month.MAY, "jun" to Month.JUNE, "jul" to Month.JULY, "aug" to Month.AUGUST,
        "sep" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "oct" to Month.OCTOBER,
        "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER,
    )
    private const val DAY_NAMES = "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thu|friday|fri|saturday|sat|sunday|sun"
    private const val MONTH_NAMES =
        "january|february|march|april|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sept|sep|oct|nov|dec"

    private fun monthOf(word: String): Month? = months[word.lowercase().take(if (word.lowercase().startsWith("sept")) 4 else 3)]

    fun parse(input: String, today: LocalDate): Result {
        var text = " " + input.trim() + " "
        var kind = EntryKind.TASK
        val trimmed = input.trimStart()
        when {
            trimmed.startsWith("- ") || trimmed.startsWith("– ") -> { kind = EntryKind.NOTE; text = " " + trimmed.drop(2) + " " }
            trimmed.startsWith("o ") || trimmed.startsWith("○ ") -> { kind = EntryKind.EVENT; text = " " + trimmed.drop(2) + " " }
        }

        // "! Close L&T proposal": one of the day's 3 most important tasks
        var focus = false
        Regex("""^\s*!\s+""").find(text)?.let {
            focus = true
            text = " " + text.substring(it.range.last + 1)
        }
        var date: LocalDate? = null
        var time: LocalTime? = null
        var duration: Int? = null

        // hashtags: "#quotes", "#tl-quotes"
        val tags = Regex("""(?<=\s)#([\p{L}\p{N}][\p{L}\p{N}_\-]*)""").findAll(text).map { it.groupValues[1] }.toList()
        text = text.replace(Regex("""(?<=\s)#[\p{L}\p{N}][\p{L}\p{N}_\-]*"""), " ")

        fun take(regex: Regex, handle: (MatchResult) -> Boolean) {
            val m = regex.find(text) ?: return
            if (handle(m)) text = text.removeRange(m.range).let { " " + it.trim() + " " }
        }
        val o = RegexOption.IGNORE_CASE

        // duration: "for 30 min", "for 1.5 hours", "for 45m"
        take(Regex("""\s(?:for\s+)(\d+(?:\.\d+)?)\s*(minutes?|mins?|m|hours?|hrs?|h)\b""", o)) { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
            true
        }

        // time with am/pm: "at 7pm", "7:30 pm"
        take(Regex("""\s(?:at\s+|@\s*)?(\d{1,2})(?::|\.)?(\d{2})?\s*(am|pm)\b""", o)) { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val pm = m.groupValues[3].lowercase() == "pm"
            if (h !in 1..12 || min > 59) return@take false
            if (pm && h != 12) h += 12
            if (!pm && h == 12) h = 0
            time = LocalTime.of(h, min)
            true
        }
        // 24h time: "19:30", "at 7:15"
        if (time == null) {
            take(Regex("""\s(?:at\s+|@\s*)?(\d{1,2})[:.](\d{2})\b""", o)) { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h > 23 || min > 59) return@take false
                time = LocalTime.of(h, min)
                true
            }
        }
        // bare hour after "at": "at 7" (1–7 → pm), "at 19"
        if (time == null) {
            take(Regex("""\s(?:at|@)\s*(\d{1,2})\b(?![/\-])""", o)) { m ->
                var h = m.groupValues[1].toInt()
                if (h > 23) return@take false
                if (h in 1..7) h += 12
                time = LocalTime.of(h, 0)
                true
            }
        }
        if (time == null) {
            take(Regex("""\s(?:at\s+)?noon\b""", o)) { time = LocalTime.NOON; true }
        }

        // day words
        take(Regex("""\s(?:the\s+)?day\s+after\s+tomorrow\b""", o)) { date = today.plusDays(2); true }
        if (date == null) take(Regex("""\s(?:by\s+|on\s+)?(today|tonight|tomorrow|tmrw|tmr)\b""", o)) { m ->
            when (m.groupValues[1].lowercase()) {
                "today" -> date = today
                "tonight" -> { date = today; if (time == null) time = LocalTime.of(20, 0) }
                else -> date = today.plusDays(1)
            }
            true
        }
        // weekday: "friday", "on friday", "next monday", "this sat". Short forms need a prefix,
        // so words like "sat" or "sun" in ordinary sentences aren't mistaken for days.
        if (date == null) take(Regex("""\s(?:(on|by|next|this|coming)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", o)) { m ->
            date = weekday(today, days.getValue(m.groupValues[2].lowercase()), m.groupValues[1])
            true
        }
        if (date == null) take(Regex("""\s(on|by|next|this|coming)\s+($DAY_NAMES)\b""", o)) { m ->
            date = weekday(today, days[m.groupValues[2].lowercase()] ?: return@take false, m.groupValues[1])
            true
        }
        // "28 sep", "28th september 2010", "on sep 28", "oct 29 2010" (a 4-digit year may follow)
        if (date == null) take(Regex("""\s(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH_NAMES)\b(?:,?\s+(\d{4})\b)?""", o)) { m ->
            val month = monthOf(m.groupValues[2]) ?: return@take false
            date = dateOf(today, month, m.groupValues[1].toInt(), m.groupValues[3]) ?: return@take false
            true
        }
        if (date == null) take(Regex("""\s(?:on\s+)?($MONTH_NAMES)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4})\b)?""", o)) { m ->
            val month = monthOf(m.groupValues[1]) ?: return@take false
            date = dateOf(today, month, m.groupValues[2].toInt(), m.groupValues[3]) ?: return@take false
            true
        }
        // "28/9", "28-09", "29/10/2010" (day first)
        if (date == null) take(Regex("""\s(?:on\s+)?(\d{1,2})[/\-](\d{1,2})(?:[/\-](\d{4}))?\b""")) { m ->
            val month = m.groupValues[2].toInt()
            if (month !in 1..12) return@take false
            date = dateOf(today, Month.of(month), m.groupValues[1].toInt(), m.groupValues[3]) ?: return@take false
            true
        }
        // parts of the day, only when no clock time was given
        if (time == null) take(Regex("""\s(?:in\s+the\s+|this\s+)?(morning|afternoon|evening)\b""", o)) { m ->
            time = when (m.groupValues[1].lowercase()) {
                "morning" -> LocalTime.of(9, 0)
                "afternoon" -> LocalTime.of(14, 0)
                else -> LocalTime.of(18, 0)
            }
            true
        }

        // capitalisation is left to the keyboard, so the title keeps exactly what was typed
        val title = text.trim()
            .replace(Regex("""\s{2,}"""), " ")
            .trimEnd(',', ';', '-', '–')
            .replace(Regex("""\s+(at|on|by|for)$""", o), "")
            .trim()
        return Result(kind, title, date, time, duration, tags, focus && kind == EntryKind.TASK)
    }

    /** The coming [dow] after today; "this friday" on a Friday means today. */
    private fun weekday(today: LocalDate, dow: DayOfWeek, prefix: String): LocalDate =
        if (prefix.lowercase() == "this" && today.dayOfWeek == dow) today
        else today.with(TemporalAdjusters.next(dow))

    /** An exact date when a year was typed, otherwise the next upcoming one. */
    private fun dateOf(today: LocalDate, month: Month, day: Int, year: String): LocalDate? =
        if (year.isNotEmpty()) runCatching { LocalDate.of(year.toInt(), month, day) }.getOrNull()
        else futureDate(today, month, day)

    /** The next occurrence (today or later) of [day] [month]; next year if it has passed. */
    private fun futureDate(today: LocalDate, month: Month, day: Int): LocalDate? {
        fun of(year: Int) = runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = of(today.year) ?: return null
        return if (thisYear.isBefore(today)) of(today.year + 1) else thisYear
    }
}
