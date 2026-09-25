package com.ivy.planner.ui

import androidx.compose.ui.graphics.Color
import com.ivy.planner.domain.isoWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Soft pastel palette. Accents stay readable on the soft-black background
 * and on light backgrounds; text placed on an accent uses the matching "On" colour.
 */
object PlannerColors {
    /** Pastel version of the logo's orange dot. */
    val Accent = Color(0xFFF2A48C)
    val OnAccent = Color(0xFF3A1D14)
    /** Pastel mint, for tasks and done. */
    val Done = Color(0xFF8FD4A3)
    val OnDone = Color(0xFF0F2A18)
    /** Pastel blue, for events. */
    val Event = Color(0xFF9DBBEF)
    /** Soft grey, for missed and skipped. */
    val Missed = Color(0xFF8C918B)
}

internal val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val DayTitleFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
internal val ShortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "W39 · Thu 24 Sep" */
internal fun LocalDate.withWeek(): String = "W${isoWeek().week} · ${format(ShortDayFmt)}"

internal fun LocalTime.label(): String = format(TimeFmt)

