package com.ivy.planner.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.DayTitleFmt
import com.ivy.planner.ui.EntryRow
import com.ivy.planner.ui.MonthCalendar
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RapidLog
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.WeekStrip
import com.ivy.planner.ui.timelineColor
import com.ivy.planner.ui.withWeek
import java.time.LocalDate

@Composable
fun PlannerDayScreenImpl(screen: PlannerDayScreen) {
    val viewModel: DayViewModel = screenScopedViewModel()
    LaunchedEffect(screen) {
        screen.epochDay?.let { viewModel.onEvent(DayEvent.SelectDate(LocalDate.ofEpochDay(it))) }
    }
    PlannerTheme { DayUi(state = viewModel.uiState(), onEvent = viewModel::onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayUi(state: DayState, onEvent: (DayEvent) -> Unit) {
    val nav = navigation()
    var monthOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day log", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { nav.navigateTo(PlannerSearchScreen) }) {
                        Icon(Icons.Outlined.Search, "Search")
                    }
                    IconButton(onClick = {
                        val w = state.date.isoWeek()
                        nav.navigateTo(PlannerWeekScreen(w.year, w.week))
                    }) { Icon(Icons.Outlined.DateRange, "Week log") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.navigateTo(PlannerEditScreen(epochDay = state.date.toEpochDay())) },
                containerColor = PlannerColors.Accent,
                contentColor = PlannerColors.OnAccent,
            ) { Icon(Icons.Filled.Add, "New entry") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                Header(state)
                WeekStrip(
                    selected = state.date,
                    today = state.today,
                    dotFor = { state.dots[it] },
                    onSelect = { onEvent(DayEvent.SelectDate(it)) },
                    onPrevWeek = { onEvent(DayEvent.SelectDate(state.date.minusWeeks(1))) },
                    onNextWeek = { onEvent(DayEvent.SelectDate(state.date.plusWeeks(1))) },
                    onOpenMonth = { monthOpen = true },
                )
            }
            if (state.date == state.today && state.today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
                item {
                    ReviewNudge { nav.navigateTo(PlannerReviewScreen) }
                }
            }
            if (state.overdue.isNotEmpty()) {
                item { OverdueCard(state.overdue, state.today, onEvent) }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    RapidLog(placeholder = "Log a task, event or note…", onSubmit = { k, t -> onEvent(DayEvent.QuickAdd(k, t)) })
                }
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        "Nothing logged for this day yet.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(state.rows, key = { _, row -> row.key }) { index, row ->
                val colors = state.rows.map { timelineColor(it.kind, it.state) }
                Column(Modifier.padding(horizontal = 12.dp)) {
                    EntryRow(
                        lineAbove = if (index > 0) colors[index - 1] else null,
                        lineBelow = if (index < state.rows.lastIndex) colors[index] else null,
                        kind = row.kind,
                        title = row.title,
                        description = row.description,
                        meta = row.meta,
                        state = row.state,
                        repeating = row.repeating,
                        onToggle = { onEvent(DayEvent.Toggle(row)) },
                        onClick = {
                            nav.navigateTo(
                                PlannerEditScreen(
                                    entryId = row.entryId,
                                    seriesId = row.seriesId,
                                    epochDay = row.date.toEpochDay(),
                                ),
                            )
                        },
                    )
                }
            }
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Today", false, {
                    onEvent(DayEvent.SelectDate(state.today))
                    monthOpen = false
                })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(state: DayState) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        SectionLabel("Week ${state.date.isoWeek().week}")
        Text(
            text = if (state.date == state.today) "Today · ${state.date.format(DayTitleFmt)}" else state.date.format(DayTitleFmt),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (state.summary.isNotBlank()) {
            Text(state.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ReviewNudge(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("New week: review last week's open tasks", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onOpen) { Text("Review", color = PlannerColors.Accent, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun OverdueCard(overdue: List<Entry>, today: LocalDate, onEvent: (DayEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf<Entry?>(null) }
    val shown = if (expanded) overdue else overdue.take(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Needs a decision · ${overdue.size}")
        shown.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(checked = false, onToggle = { onEvent(DayEvent.OverdueDone(entry.id)) })
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    entry.date?.let {
                        Text(
                            "from ${it.withWeek()}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("> Today", false, { onEvent(DayEvent.OverdueMove(entry.id, today)) })
                Pill("< Schedule", false, { scheduling = entry })
                Pill("Drop", false, { onEvent(DayEvent.OverdueDrop(entry.id)) })
            }
        }
        if (overdue.size > 1) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "+${overdue.size - 1} more", color = PlannerColors.Accent)
            }
        }
    }

    scheduling?.let { entry ->
        PlannerDatePicker(
            initial = today.plusDays(1),
            onPick = { onEvent(DayEvent.OverdueMove(entry.id, it)) },
            onDismiss = { scheduling = null },
        )
    }
}
