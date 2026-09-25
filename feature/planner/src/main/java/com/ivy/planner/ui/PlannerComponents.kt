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
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.isoWeek
import java.io.File
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

/** The round Taskito-style checkbox (20dp circle in a 28dp touch column). */
@Composable
fun CheckCircle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier.size(size).clip(CircleShape).background(PlannerColors.Done),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Mark not done", tint = PlannerColors.OnDone, modifier = Modifier.size(size * 0.66f))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .border(2.dp, if (enabled) PlannerColors.Done else PlannerColors.Missed, CircleShape),
            )
        }
    }
}

/** Importance colours, level 1–4 (0 = none, shown in the journal's butter yellow). */
fun importanceColor(level: Int): Color = when (level) {
    1 -> PlannerColors.Done
    2 -> PlannerColors.Journal
    3 -> PlannerColors.Accent
    4 -> Color(0xFFEF9A9A)
    else -> PlannerColors.Journal
}

/** Leading symbol: checkbox for tasks, calendar for events, dot for notes, star for journal. */
@Composable
fun EntryLeading(kind: EntryKind, state: EntryState, onToggle: () -> Unit, importance: Int = 0) {
    when (kind) {
        EntryKind.TASK -> CheckCircle(
            checked = state == EntryState.DONE,
            onToggle = onToggle,
            enabled = state != EntryState.MISSED,
        )
        EntryKind.EVENT -> Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = "Event", tint = PlannerColors.Event, modifier = Modifier.size(17.dp))
        }
        EntryKind.NOTE -> Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(PlannerColors.Done))
        }
        EntryKind.JOURNAL -> Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Star, contentDescription = "Journal", tint = importanceColor(importance), modifier = Modifier.size(19.dp))
        }
    }
}

/** The colour of an entry's timeline segment. */
@Composable
fun timelineColor(kind: EntryKind, state: EntryState, isMoney: Boolean = false, importance: Int = 0, isRoutine: Boolean = false): Color = when {
    isMoney -> PlannerColors.Accent
    isRoutine -> PlannerColors.Routine
    kind == EntryKind.EVENT -> PlannerColors.Event
    kind == EntryKind.JOURNAL -> importanceColor(importance)
    kind == EntryKind.NOTE -> MaterialTheme.colorScheme.outlineVariant
    state == EntryState.MISSED || state == EntryState.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
    else -> PlannerColors.Done
}

/** A small coloured tag, e.g. the board a task belongs to. */
@Immutable
data class RowTag(val name: String, val color: Color)

/**
 * One line on a timeline, in the compact layout: time on the left, then the icon on the
 * connecting line, then the text. [lineAbove] / [lineBelow] draw the line (null = none).
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
    timeLabel: String = "",
    lineAbove: Color? = null,
    lineBelow: Color? = null,
    moneyLabel: String? = null,
    moneyIsIncome: Boolean = false,
    importance: Int = 0,
    tags: List<RowTag> = emptyList(),
    photos: List<File> = emptyList(),
    /** Small muted text at the right end of the title line, e.g. "15 min" or "All day". */
    trailing: String = "",
    /** For routines: steps done and total, shown as a progress ring. */
    routine: Pair<Int, Int>? = null,
) {
    val faded = state == EntryState.DONE || state == EntryState.MISSED || state == EntryState.SKIPPED
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        // time column: one line, always; sized to fit "00:00" at the phone's text size
        Text(
            text = timeLabel,
            modifier = Modifier.width(timeColumnWidth()).padding(top = 5.dp),
            style = TimeStyle,
            color = muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(6.dp))
        // icon on the line
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val stroke = 2.dp.toPx()
                    // the icon sits in the top 28dp; its visible part spans about 4..24dp
                    lineAbove?.let { drawLine(it, Offset(x, 0f), Offset(x, 4.dp.toPx()), stroke) }
                    lineBelow?.let { drawLine(it, Offset(x, 25.dp.toPx()), Offset(x, size.height), stroke) }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                moneyLabel != null -> MoneyBadge(moneyIsIncome)
                routine != null -> RoutineRing(routine.first, routine.second)
                else -> EntryLeading(kind, state, onToggle, importance)
            }
        }
        Column(Modifier.weight(1f).padding(start = 6.dp, top = 4.dp, end = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (faded || kind == EntryKind.NOTE) muted else onSurface,
                    textDecoration = if (state == EntryState.DONE) TextDecoration.LineThrough else null,
                )
                val right = moneyLabel ?: trailing
                if (right.isNotBlank()) {
                    Text(
                        right,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp),
                        fontSize = if (moneyLabel != null) 14.sp else 12.sp,
                        fontWeight = if (moneyLabel != null) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            moneyLabel == null -> muted
                            moneyIsIncome -> PlannerColors.Done
                            else -> PlannerColors.Accent
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (meta.isNotBlank() || repeating || tags.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                    if (repeating) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", tint = muted, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (meta.isNotBlank()) {
                        Text(meta, fontSize = 12.sp, color = if (state == EntryState.MISSED) PlannerColors.Accent else muted)
                    }
                    tags.forEach { tag ->
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(tag.color))
                        Spacer(Modifier.width(4.dp))
                        Text(tag.name, fontSize = 12.sp, color = muted, maxLines = 1)
                    }
                }
            }
            if (photos.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.take(3).forEach { f ->
                        AsyncImage(
                            model = f,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

private val TimeStyle = ComposeTextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)

/** Width of the time column: "00:00" at the current text size, so times never wrap. */
@Composable
fun timeColumnWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(density.fontScale, density.density) {
        with(density) { measurer.measure("00:00", TimeStyle).size.width.toDp() } + 2.dp
    }
}

/** ₹ badge for expenses (peach) and income (mint). */
@Composable
fun MoneyBadge(isIncome: Boolean) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isIncome) PlannerColors.Done else PlannerColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CurrencyRupee,
                contentDescription = if (isIncome) "Income" else "Expense",
                tint = if (isIncome) PlannerColors.OnDone else PlannerColors.OnAccent,
                modifier = Modifier.size(13.dp),
            )
        }
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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
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
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Lavender progress ring with a small play symbol: a routine on the timeline. */
@Composable
fun RoutineRing(done: Int, total: Int, size: Dp = 20.dp) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .drawBehind {
                    val stroke = 2.5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    if (total > 0 && done > 0) {
                        drawArc(
                            PlannerColors.Routine, -90f, 360f * done / total, false, Offset(inset, inset), arcSize,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Routine", tint = PlannerColors.Routine, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/**
 * Swipe left for the next day, right for the previous one. Vertical scrolling is unaffected;
 * a swipe needs a clear sideways movement to count.
 */
fun Modifier.swipeDays(key: Any, onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(key) {
    var drag = 0f
    detectHorizontalDragGestures(
        onDragStart = { drag = 0f },
        onDragEnd = {
            val threshold = 72.dp.toPx()
            if (drag > threshold) onPrevious() else if (drag < -threshold) onNext()
            drag = 0f
        },
        onDragCancel = { drag = 0f },
        onHorizontalDrag = { _, amount -> drag += amount },
    )
}
