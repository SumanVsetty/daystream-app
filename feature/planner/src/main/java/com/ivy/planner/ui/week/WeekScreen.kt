package com.ivy.planner.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ViewKanban
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivy.navigation.PlannerBoardsScreen
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerRoutinesScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RapidLog
import com.ivy.planner.ui.RoutineRing
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.ShortDayFmt
import com.ivy.planner.ui.shortDay
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
    PlannerTheme { WeekUi(viewModel.uiState(), viewModel::onEvent, asTab = false) }
}

/** The Week log as the app's main "Week" tab. */
@Composable
fun PlannerWeekTab() {
    val viewModel: WeekViewModel = screenScopedViewModel()
    PlannerTheme { WeekUi(viewModel.uiState(), viewModel::onEvent, asTab = true) }
}

private val RangeFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekUi(state: WeekState, onEvent: (WeekEvent) -> Unit, asTab: Boolean) {
    val nav = navigation()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onEvent(WeekEvent.Refresh) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + if (asTab) 110.dp else 48.dp,
            ),
        ) {
            item {
                Column(Modifier.padding(start = if (asTab) 20.dp else 4.dp, end = 4.dp, top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!asTab) {
                            IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Week ${state.week.week}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                        TextButton(onClick = { onEvent(WeekEvent.ToggleFilter) }) {
                            Text(
                                if (state.todoOnly) "To do" else "All",
                                color = if (state.todoOnly) PlannerColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = PlannerColors.Accent) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Boards") },
                                    leadingIcon = { Icon(Icons.Outlined.ViewKanban, null) },
                                    onClick = { menu = false; nav.navigateTo(PlannerBoardsScreen()) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Routines") },
                                    leadingIcon = { Icon(Icons.Outlined.PlayCircle, null) },
                                    onClick = { menu = false; nav.navigateTo(PlannerRoutinesScreen) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Weekly review") },
                                    leadingIcon = { Icon(Icons.Outlined.FactCheck, null) },
                                    onClick = { menu = false; nav.navigateTo(PlannerReviewScreen) },
                                )
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
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
                        placeholder = "Rapid log for this week…",
                        today = state.today,
                        onSubmit = { onEvent(WeekEvent.AddWeekTask(it)) },
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
                        Text(
                            if (state.todoOnly) "Nothing left to do" else "Nothing planned",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                    day.lines.forEach { line ->
                        WeekLine(line) { onEvent(WeekEvent.ToggleLine(line)) }
                    }
                    day.spend?.let {
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PlannerColors.Accent)
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
                        text = { Text(d.shortDay()) },
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


/** One line in a Week day: the same icons as the Day tab, smaller; tasks can be ticked here. */
@Composable
private fun WeekLine(line: WeekDayLine, onToggle: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val finished = line.state == EntryState.DONE || line.state == EntryState.MISSED || line.state == EntryState.SKIPPED
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when {
                line.isRoutine -> RoutineRing(if (line.state == EntryState.DONE) 1 else 0, 1, size = 16.dp)
                line.kind == EntryKind.TASK -> Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .then(
                            if (line.state == EntryState.DONE) Modifier.background(PlannerColors.Done)
                            else Modifier.border(1.5.dp, if (line.state == EntryState.MISSED) PlannerColors.Missed else PlannerColors.Done, CircleShape),
                        )
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (line.state == EntryState.DONE) {
                        Icon(Icons.Filled.Check, contentDescription = "Done", tint = PlannerColors.OnDone, modifier = Modifier.size(12.dp))
                    }
                }
                line.kind == EntryKind.EVENT -> Icon(Icons.Outlined.CalendarToday, contentDescription = "Event", tint = PlannerColors.Event, modifier = Modifier.size(16.dp))
                line.kind == EntryKind.JOURNAL -> Icon(Icons.Filled.Star, contentDescription = "Journal", tint = PlannerColors.Journal, modifier = Modifier.size(16.dp))
                else -> Box(Modifier.size(7.dp).clip(CircleShape).background(PlannerColors.Done))
            }
        }
        Spacer(Modifier.width(6.dp))
        line.time?.let {
            Text(it, fontSize = 13.sp, color = muted, modifier = Modifier.padding(end = 8.dp))
        }
        Text(
            line.title,
            fontSize = 14.sp,
            color = if (finished) muted else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (line.state == EntryState.DONE) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (line.repeating) {
            Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", tint = muted, modifier = Modifier.padding(start = 6.dp).size(13.dp))
        }
        if (line.moved) {
            Text("moved", fontSize = 12.sp, color = PlannerColors.Accent, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
