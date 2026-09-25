package com.ivy.planner.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerRoutineScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.EntryRow
import com.ivy.planner.ui.MonthCalendar
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.WeekStrip
import com.ivy.planner.ui.add.PlannerAddSheet
import com.ivy.planner.ui.label
import com.ivy.planner.ui.swipeDays
import com.ivy.planner.ui.timelineColor
import com.ivy.planner.ui.withWeek
import java.time.format.TextStyle
import java.util.Locale

/** Opens whatever a row is: a routine, an expense in the wallet, or the entry editor. */
internal fun openRow(nav: Navigation, row: DayRow) {
    val m = row.money
    val routineId = row.seriesId
    when {
        row.routine != null && routineId != null -> nav.navigateTo(PlannerRoutineScreen(routineId, row.date.toEpochDay()))
        m != null -> nav.navigateTo(
            EditTransactionScreen(initialTransactionId = m.id, type = if (m.isIncome) TransactionType.INCOME else TransactionType.EXPENSE),
        )
        else -> nav.navigateTo(PlannerEditScreen(entryId = row.entryId, seriesId = row.seriesId, epochDay = row.date.toEpochDay()))
    }
}

/**
 * The "Now & next" Day tab: still open, next up, later today (with free time),
 * and earlier today folded into one line. The log bar sits at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusDayUi(
    state: DayState,
    onEvent: (DayEvent) -> Unit,
    asTab: Boolean,
    topBanners: @Composable () -> Unit,
) {
    val nav = navigation()
    val f = state.focus
    var monthOpen by remember { mutableStateOf(false) }
    var earlierOpen by remember(state.date) { mutableStateOf(false) }
    var allOpen by remember(state.date) { mutableStateOf(false) }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.fillMaxSize()) {
                // header: day, and today at a glance
                Row(
                    Modifier.fillMaxWidth().padding(start = if (asTab) 20.dp else 4.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!asTab) IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f).clickable { monthOpen = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (state.date == state.today) state.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                else state.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + state.date.dayOfMonth + " " +
                                    state.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Icon(Icons.Filled.KeyboardArrowDown, "Month calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${state.date.dayOfMonth} ${state.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} · W${state.date.isoWeek().week}" +
                                if (state.date != state.today) "" else "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (f.total > 0 || f.spent != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (f.total > 0) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${f.done}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PlannerColors.Done)
                                    Text(" of ${f.total} done", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            f.spent?.let { Text("$it spent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        }
                    }
                    IconButton(onClick = { nav.navigateTo(PlannerSearchScreen) }) { Icon(Icons.Outlined.Search, "Search") }
                }
                WeekStrip(
                    selected = state.date,
                    today = state.today,
                    dotFor = { state.dots[it] },
                    onSelect = { onEvent(DayEvent.SelectDate(it)) },
                    onPrevWeek = { onEvent(DayEvent.SelectDate(state.date.minusWeeks(1))) },
                    onNextWeek = { onEvent(DayEvent.SelectDate(state.date.plusWeeks(1))) },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .swipeDays(
                            key = state.date,
                            onPrevious = { onEvent(DayEvent.SelectDate(state.date.minusDays(1))) },
                            onNext = { onEvent(DayEvent.SelectDate(state.date.plusDays(1))) },
                        ),
                    contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding() + if (asTab) 170.dp else 90.dp),
                ) {
                    item { topBanners() }
                    // still open: late today, and left from earlier days
                    if (f.stillOpen.isNotEmpty()) {
                        item {
                            val shown = if (allOpen) f.stillOpen else f.stillOpen.take(2)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 2.dp)) {
                                    SectionLabel("Still open · ${f.stillOpen.size}")
                                    Spacer(Modifier.weight(1f))
                                    if (f.stillOpen.size > 2) {
                                        Text(
                                            if (allOpen) "Show less" else "+${f.stillOpen.size - 2} more",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable { allOpen = !allOpen },
                                        )
                                    }
                                }
                                shown.forEach { row -> StillOpenRow(row, nav, onEvent) }
                            }
                        }
                    }
                    // next up
                    f.nextUp?.let { next ->
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { openRow(nav, next) }
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row {
                                    SectionLabel("Next up · ${f.nextUpCountdown}")
                                    Spacer(Modifier.weight(1f))
                                    Text(next.timeLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                                val details = listOf(next.trailing, next.meta).filter { it.isNotBlank() } + next.tags.map { "● " + it.name }
                                if (details.isNotEmpty()) {
                                    Text(details.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // checklist items, tickable right here
                                if (next.checklist.isNotEmpty()) {
                                    Column {
                                        next.checklist.take(6).forEach { item ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable { onEvent(DayEvent.ToggleChecklistItem(item.id, !item.done)) },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CheckCircle(checked = item.done, onToggle = { onEvent(DayEvent.ToggleChecklistItem(item.id, !item.done)) }, size = 16.dp)
                                                Text(
                                                    item.text,
                                                    fontSize = 14.sp,
                                                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                    textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                )
                                            }
                                        }
                                        if (next.checklist.size > 6) {
                                            Text("+${next.checklist.size - 6} more", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp))
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (next.routine != null) {
                                        Pill("▶ Start", true, { openRow(nav, next) })
                                    } else {
                                        Action("✓ Done", PlannerColors.Done) { onEvent(DayEvent.Toggle(next)) }
                                    }
                                    Action("Later", null) { onEvent(DayEvent.Later(next)) }
                                    Action("Tomorrow", null) { onEvent(DayEvent.Tomorrow(next)) }
                                }
                            }
                        }
                    }
                    // later today (or the whole day for other days)
                    if (f.later.isNotEmpty()) {
                        item {
                            Box(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp)) {
                                SectionLabel(
                                    when {
                                        f.isToday -> "Later today"
                                        state.date.isAfter(state.today) -> "Planned"
                                        else -> "The day"
                                    },
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val rows = f.later.filterIsInstance<LaterItem.Row>().map { it.row }
                        itemsIndexed(f.later, key = { i, it -> if (it is LaterItem.Row) it.row.key else "free:$i" }) { _, item ->
                            when (item) {
                                is LaterItem.Free -> FreeLine(item.label)
                                is LaterItem.Row -> {
                                    val i = rows.indexOf(item.row)
                                    DayEntryRow(item.row, rows, i, nav, onEvent)
                                }
                            }
                        }
                    }
                    // earlier today, folded into one line
                    if (f.isToday && f.earlier.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (earlierOpen) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { earlierOpen = !earlierOpen }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    SectionLabel("Earlier today", MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!earlierOpen && f.earlierSummary.isNotBlank()) Text(f.earlierSummary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                Icon(if (earlierOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (earlierOpen) "Fold" else "Unfold", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (earlierOpen) {
                            itemsIndexed(f.earlier, key = { _, r -> "e:" + r.key }) { i, row -> DayEntryRow(row, f.earlier, i, nav, onEvent) }
                        }
                    }
                    if (f.stillOpen.isEmpty() && f.nextUp == null && f.later.isEmpty() && f.earlier.isEmpty()) {
                        item {
                            Text(
                                "Nothing planned. Tap below to log something.",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // rapid log, within thumb reach; above the keyboard while typing
            BottomRapidLog(
                today = state.today,
                bottomPadding = padding.calculateBottomPadding() + if (asTab) 92.dp else 16.dp,
                onSubmit = { onEvent(DayEvent.RapidLog(it)) },
                onExpand = { text -> PlannerAddSheet.open(EntryKind.TASK, state.date, text) },
            )
        }
    }

    if (monthOpen) {
        ModalBottomSheet(onDismissRequest = { monthOpen = false }) {
            MonthCalendar(
                initial = state.date,
                today = state.today,
                onSelectDay = {
                    onEvent(DayEvent.SelectDate(it))
                    monthOpen = false
                },
                onSelectWeek = {
                    monthOpen = false
                    val w = it.isoWeek()
                    nav.navigateTo(PlannerWeekScreen(w.year, w.week))
                },
            )
            LayoutSwitch(state.focusLayout) {
                onEvent(DayEvent.ToggleLayout)
                monthOpen = false
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Switch between the "Now & next" layout and the classic timeline (kept as an alternative). */
@Composable
internal fun LayoutSwitch(focus: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Day layout", Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Pill("Now & next", focus, { if (!focus) onToggle() })
        Spacer(Modifier.width(6.dp))
        Pill("Timeline", !focus, { if (focus) onToggle() })
    }
}

@Composable
private fun Action(label: String, color: Color?, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color ?: MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (color != null) PlannerColors.OnDone else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StillOpenRow(row: DayRow, nav: Navigation, onEvent: (DayEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { openRow(nav, row) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.timeLabel,
            modifier = Modifier.width(52.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PlannerColors.Accent,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        if (row.routine != null) {
            com.ivy.planner.ui.RoutineRing(row.routine.first, row.routine.second)
        } else {
            CheckCircle(checked = false, onToggle = { onEvent(DayEvent.Toggle(row)) })
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(row.title, fontSize = 15.sp, maxLines = 2)
            val sub = listOf(row.meta).filter { it.isNotBlank() } + row.tags.map { "● " + it.name }
            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(
            "Later",
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable { onEvent(DayEvent.Later(row)) }.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Tmrw",
            modifier = Modifier.clickable { onEvent(DayEvent.Tomorrow(row)) }.padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FreeLine(label: String) {
    Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A row in the compact layout, joined to its neighbours by the timeline line. */
@Composable
private fun DayEntryRow(row: DayRow, rows: List<DayRow>, i: Int, nav: Navigation, onEvent: (DayEvent) -> Unit) {
    val colors = rows.map { timelineColor(it.kind, it.state, isMoney = it.money != null, importance = it.importance, isRoutine = it.routine != null) }
    Box(Modifier.padding(start = 2.dp, end = 8.dp)) {
        EntryRow(
            kind = row.kind,
            title = row.title,
            description = row.description,
            meta = row.meta,
            state = row.state,
            repeating = row.repeating,
            onToggle = { onEvent(DayEvent.Toggle(row)) },
            onClick = { openRow(nav, row) },
            timeLabel = row.timeLabel,
            lineAbove = if (i > 0) colors[i - 1] else null,
            lineBelow = if (i in 0 until rows.lastIndex) colors[i] else null,
            moneyLabel = row.money?.label,
            moneyIsIncome = row.money?.isIncome ?: false,
            importance = row.importance,
            tags = row.tags,
            photos = row.photos,
            trailing = row.trailing,
            routine = row.routine,
        )
    }
}

/**
 * Type a line and send: "Complete task 1 at 7pm". A hint above shows what was understood;
 * the expand button opens the full add sheet with the text so far.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.BottomRapidLog(
    today: java.time.LocalDate,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSubmit: (String) -> Unit,
    onExpand: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, today) { RapidLogParser.parse(text, today) }
    val keyboardOpen = WindowInsets.isImeVisible
    // the app draws edge to edge, so the screen doesn't shrink for the keyboard:
    // lift the bar by the keyboard's height while it's open
    val keyboardHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val submit = {
        if (parsed.title.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = if (keyboardOpen) keyboardHeight + 8.dp else bottomPadding),
    ) {
        val hint = listOfNotNull(
            when (parsed.kind) {
                EntryKind.NOTE -> "Note"
                EntryKind.EVENT -> "Event"
                else -> null
            },
            parsed.date?.let { if (it == today) "Today" else if (it == today.plusDays(1)) "Tomorrow" else it.withWeek() },
            parsed.time?.label(),
            parsed.durationMinutes?.let { "$it min" },
        ) + parsed.tags.map { "#$it" }
        if (text.isNotBlank() && hint.isNotEmpty()) {
            Text(
                hint.joinToString(" · "),
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PlannerColors.Accent,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text("Rapid log…", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(PlannerColors.Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
            }
            IconButton(onClick = {
                onExpand(text)
                text = ""
            }) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PlannerColors.Accent.copy(alpha = if (parsed.title.isNotBlank()) 1f else 0.45f))
                    .clickable(enabled = parsed.title.isNotBlank()) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Add", tint = PlannerColors.OnAccent)
            }
        }
    }
}
