package com.ivy.planner.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RapidLog
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.ShortDayFmt
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlannerWeekScreenImpl(screen: PlannerWeekScreen) {
    val viewModel: WeekViewModel = screenScopedViewModel()
    LaunchedEffect(screen) {
        val year = screen.year
        val week = screen.week
        if (year != null && week != null) {
            viewModel.onEvent(WeekEvent.SetWeek(IsoWeek(year, week)))
        }
    }
    PlannerTheme { WeekUi(viewModel.uiState(), viewModel::onEvent) }
}

private val RangeFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekUi(state: WeekState, onEvent: (WeekEvent) -> Unit) {
    val nav = navigation()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Week log", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { nav.navigateTo(PlannerReviewScreen) }) {
                        Text("Review", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Week ${state.week.week}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${state.week.monday.format(RangeFmt)} – ${state.week.sunday.format(RangeFmt)} ${state.week.sunday.year}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEvent(WeekEvent.SetWeek(state.week.previous())) }) {
                            Icon(Icons.Filled.KeyboardArrowLeft, "Previous week")
                        }
                        IconButton(onClick = { onEvent(WeekEvent.SetWeek(state.week.next())) }) {
                            Icon(Icons.Filled.KeyboardArrowRight, "Next week")
                        }
                    }
                    if (state.total > 0) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { state.done.toFloat() / state.total },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = PlannerColors.Done,
                            )
                            Spacer(Modifier.padding(start = 12.dp))
                            Text("${state.done} / ${state.total}", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("This week · no day yet", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.weekTasks, key = { it.id }) { task ->
                WeekTaskRow(task, state, onEvent)
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    RapidLog(
                        placeholder = "Add a task for this week…",
                        onSubmit = { _, t -> onEvent(WeekEvent.AddWeekTask(t)) },
                        kinds = listOf(com.ivy.planner.domain.EntryKind.TASK),
                    )
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("Days", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigateTo(PlannerDayScreen(day.date.toEpochDay())) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        day.date.format(ShortDayFmt) + if (day.date == state.today) " · Today" else "",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (day.date == state.today) PlannerColors.Accent else MaterialTheme.colorScheme.onSurface,
                    )
                    if (day.lines.isEmpty()) {
                        Text("Nothing planned", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    day.lines.forEach { line ->
                        Text(
                            "${line.symbol}  ${line.title}",
                            fontSize = 14.sp,
                            color = if (line.state == EntryState.OPEN) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (line.state == EntryState.DONE) TextDecoration.LineThrough else null,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun WeekTaskRow(task: Entry, state: WeekState, onEvent: (WeekEvent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked = task.state == EntryState.DONE, onToggle = { onEvent(WeekEvent.ToggleWeekTask(task)) })
        Text(
            task.title,
            Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (task.state == EntryState.DONE) TextDecoration.LineThrough else null,
        )
        Box {
            TextButton(onClick = { menu = true }) { Text("→ Day", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                state.week.days.forEach { d ->
                    DropdownMenuItem(
                        text = { Text(d.format(ShortDayFmt)) },
                        onClick = {
                            menu = false
                            onEvent(WeekEvent.AssignToDay(task.id, d))
                        },
                    )
                }
            }
        }
    }
}

