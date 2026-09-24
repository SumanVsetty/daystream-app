package com.ivy.planner.ui

import androidx.compose.ui.graphics.Color
import com.ivy.planner.domain.isoWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Apeiro accent (the logo's orange dot) and status colours. */
object PlannerColors {
    val Accent = Color(0xFFE8674A)
    val OnAccent = Color(0xFF1A0E0A)
    val Done = Color(0xFF3FA66B)
    val Event = Color(0xFF5B8FD6)
    val Missed = Color(0xFFB0ADB8)
}

internal val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val DayTitleFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
internal val ShortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "W39 · Thu 24 Sep" */
internal fun LocalDate.withWeek(): String = "W${isoWeek().week} · ${format(ShortDayFmt)}"

internal fun LocalTime.label(): String = format(TimeFmt)

