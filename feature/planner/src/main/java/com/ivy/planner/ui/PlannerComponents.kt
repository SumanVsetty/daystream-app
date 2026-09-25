package com.ivy.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.isoWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SectionLabel(text: String, color: Color = PlannerColors.Accent) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
    )
}

/** The round Taskito-style checkbox. */
@Composable
fun CheckCircle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onToggle, enabled = enabled, modifier = modifier.size(40.dp)) {
        if (checked) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(PlannerColors.Done),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Mark not done", tint = PlannerColors.OnDone, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, if (enabled) PlannerColors.Done else PlannerColors.Missed, CircleShape),
            )
        }
    }
}

/** Leading symbol for an entry: checkbox for tasks, calendar for events, dash for notes. */
@Composable
fun EntryLeading(kind: EntryKind, state: EntryState, onToggle: () -> Unit) {
    when (kind) {
        EntryKind.TASK -> CheckCircle(
            checked = state == EntryState.DONE,
            onToggle = onToggle,
            enabled = state != EntryState.MISSED,
        )
        EntryKind.EVENT -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = "Event", tint = PlannerColors.Event, modifier = Modifier.size(20.dp))
        }
        EntryKind.NOTE -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(PlannerColors.Done))
        }
        EntryKind.JOURNAL -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Star, contentDescription = "Journal", tint = PlannerColors.Journal, modifier = Modifier.size(22.dp))
        }
    }
}

/** The colour of an entry's timeline segment. */
@Composable
fun timelineColor(kind: EntryKind, state: EntryState, isMoney: Boolean = false): Color = when {
    isMoney -> PlannerColors.Accent
    kind == EntryKind.EVENT -> PlannerColors.Event
    kind == EntryKind.JOURNAL -> PlannerColors.Journal
    kind == EntryKind.NOTE -> MaterialTheme.colorScheme.outlineVariant
    state == EntryState.MISSED || state == EntryState.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
    else -> PlannerColors.Done
}

/**
 * One line on the Day log. [lineAbove] and [lineBelow] draw the vertical timeline
 * that connects consecutive entries (null = no segment, e.g. first or last entry).
 */
@Composable
fun EntryRow(
    kind: EntryKind,
    title: String,
    description: String,
    meta: String,
    state: EntryState,
    repeating: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    lineAbove: Color? = null,
    lineBelow: Color? = null,
    moneyLabel: String? = null,
    moneyIsIncome: Boolean = false,
    dimmed: Boolean = false,
) {
    val faded = state == EntryState.DONE || state == EntryState.MISSED || state == EntryState.SKIPPED
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .alpha(if (dimmed) 0.5f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val stroke = 2.dp.toPx()
                    // the icon occupies roughly 8dp..32dp of the 40dp leading slot
                    lineAbove?.let { drawLine(it, Offset(x, 0f), Offset(x, 6.dp.toPx()), stroke) }
                    lineBelow?.let { drawLine(it, Offset(x, 34.dp.toPx()), Offset(x, size.height), stroke) }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (moneyLabel != null) MoneyBadge(moneyIsIncome) else EntryLeading(kind, state, onToggle)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(top = 8.dp, end = 8.dp, bottom = 16.dp)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (faded || kind == EntryKind.NOTE) muted else onSurface,
                textDecoration = if (state == EntryState.DONE) TextDecoration.LineThrough else null,
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontSize = 15.sp,
                    color = muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (meta.isNotBlank() || repeating) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (repeating) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", tint = muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(meta, fontSize = 13.sp, color = if (state == EntryState.MISSED) PlannerColors.Accent else muted)
                }
            }
        }
        if (moneyLabel != null) {
            Text(
                moneyLabel,
                modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (moneyIsIncome) PlannerColors.Done else PlannerColors.Accent,
            )
        }
    }
}

/** ₹ badge for expenses (peach) and income (mint). */
@Composable
fun MoneyBadge(isIncome: Boolean) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isIncome) PlannerColors.Done else PlannerColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("₹", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isIncome) PlannerColors.OnDone else PlannerColors.OnAccent)
        }
    }
}

/** The thin "Now 14:20" line across the timeline. */
@Composable
fun NowMarker(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(PlannerColors.Accent))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PlannerColors.Accent)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.5.dp).background(PlannerColors.Accent.copy(alpha = 0.6f)))
    }
}

/**
 * Compact Mon–Sun strip for the week containing [selected]: one row, no header.
 * Swipe left or right to change week. Dots mark days that have items.
 */
@Composable
fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    dotFor: (LocalDate) -> Color?,
    onSelect: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val week = selected.isoWeek()
    var drag by remember { mutableStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .pointerInput(week) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 80f) onPrevWeek() else if (drag < -80f) onNextWeek()
                        drag = 0f
                    },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        week.days.forEach { day ->
            DayCell(day, selected = day == selected, isToday = day == today, dot = dotFor(day)) { onSelect(day) }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, selected: Boolean, isToday: Boolean, dot: Color?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PlannerColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg = if (selected) PlannerColors.OnAccent else MaterialTheme.colorScheme.onSurface
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) fg else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${day.dayOfMonth}",
            fontSize = 16.sp,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Medium,
            color = if (isToday && !selected) PlannerColors.Accent else fg,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(if (!selected && dot != null) dot else Color.Transparent),
        )
    }
}

/** Full month calendar with ISO week numbers down the left. */
@Composable
fun MonthCalendar(
    initial: LocalDate,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onSelectWeek: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
                Text(
                    "Weeks ${month.atDay(1).isoWeek().week} – ${month.atEndOfMonth().isoWeek().week}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "Next month") }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("WK", Modifier.width(40.dp), color = PlannerColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        var monday = month.atDay(1).isoWeek().monday
        while (!monday.isAfter(month.atEndOfMonth())) {
            val weekMonday = monday
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${weekMonday.isoWeek().week}",
                    modifier = Modifier.width(40.dp).clickable { onSelectWeek(weekMonday) }.padding(vertical = 10.dp),
                    color = PlannerColors.Accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                )
                (0L..6L).forEach { offset ->
                    val day = weekMonday.plusDays(offset)
                    val inMonth = YearMonth.from(day) == month
                    val isToday = day == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isToday) PlannerColors.Accent else Color.Transparent)
                            .clickable { onSelectDay(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${day.dayOfMonth}",
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                            color = when {
                                isToday -> PlannerColors.OnAccent
                                inMonth -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                        )
                    }
                }
            }
            monday = monday.plusWeeks(1)
        }
    }
}

/**
 * Rapid log: type a line and press enter. Understands day, time and duration
 * (see RapidLogParser); a small hint shows what it understood, only while it understood something.
 */
@Composable
fun RapidLog(
    placeholder: String,
    today: LocalDate,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, today) { RapidLogParser.parse(text, today) }
    val submit = {
        if (parsed.title.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = submit) {
                        Icon(Icons.Filled.Check, contentDescription = "Add", tint = PlannerColors.Done)
                    }
                }
            },
        )
        val hint = listOfNotNull(
            when (parsed.kind) {
                EntryKind.NOTE -> "Note"
                EntryKind.EVENT -> "Event"
                else -> null
            },
            parsed.date?.let { if (it == today) "Today" else if (it == today.plusDays(1)) "Tomorrow" else it.withWeek() },
            parsed.time?.label(),
            parsed.durationMinutes?.let { "$it min" },
        )
        if (text.isNotBlank() && hint.isNotEmpty()) {
            Text(
                hint.joinToString(" · "),
                modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PlannerColors.Accent,
            )
        }
    }
}

@Composable
fun KindChip(kind: EntryKind, selected: Boolean, onClick: () -> Unit) {
    val label = when (kind) {
        EntryKind.TASK -> "Task"
        EntryKind.EVENT -> "Event"
        EntryKind.NOTE -> "Note"
        EntryKind.JOURNAL -> "Journal"
    }
    Pill(label, selected, onClick)
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PlannerColors.Accent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) PlannerColors.OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
