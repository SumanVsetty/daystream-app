package com.ivy.planner.ui.day

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerMonthScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerRoutineScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.domain.key
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
import com.ivy.planner.ui.ShortDayFmt
import com.ivy.planner.ui.WeekStrip
import com.ivy.planner.ui.add.PlannerAddSheetHost
import com.ivy.planner.ui.swipeDays
import com.ivy.planner.ui.timelineColor
import com.ivy.planner.ui.withWeek
import java.time.LocalDate

@Composable
fun PlannerDayScreenImpl(screen: PlannerDayScreen) {
    val viewModel: DayViewModel = screenScopedViewModel()
    LaunchedEffect(screen) {
        screen.epochDay?.let { viewModel.onEvent(DayEvent.SelectDate(LocalDate.ofEpochDay(it))) }
    }
    PlannerTheme {
        DayChooser(viewModel.uiState(), viewModel::onEvent, asTab = false)
        PlannerAddSheetHost()
    }
}

/** The Day log as the app's main "Day" tab (no back button; the bottom bar's logo button adds entries). */
@Composable
fun PlannerDayTab() {
    val viewModel: DayViewModel = screenScopedViewModel()
    PlannerTheme { DayChooser(viewModel.uiState(), viewModel::onEvent, asTab = true) }
}

/** "Now & next" by default; the classic timeline is kept and can be switched back to. */
@Composable
private fun DayChooser(state: DayState, onEvent: (DayEvent) -> Unit, asTab: Boolean) {
    if (state.focusLayout) {
        // refresh expenses when coming back, as the classic layout does
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onEvent(DayEvent.Refresh) }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        FocusDayUi(state, onEvent, asTab) {
            NotificationBanner()
            if (state.date == state.today && state.today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
                val nav = navigation()
                SlimBanner("New week: review last week", "Review") { nav.navigateTo(PlannerReviewScreen) }
            }
        }
    } else {
        DayUi(state = state, onEvent = onEvent, asTab = asTab)
    }
}

/** Asks for notification permission on Android 13+, until it's allowed. */
@Composable
private fun NotificationBanner() {
    val context = LocalContext.current
    fun allowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(allowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    if (!ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SlimBanner("Allow notifications for reminders", "Allow") { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayUi(state: DayState, onEvent: (DayEvent) -> Unit, asTab: Boolean) {
    val nav = navigation()
    var monthOpen by remember { mutableStateOf(false) }
    val colors = state.rows.map { timelineColor(it.kind, it.state, isMoney = it.money != null, importance = it.importance, isRoutine = it.routine != null) }
    val listState = rememberLazyListState()

    // notifications need permission on Android 13+; a slim banner asks until it's allowed
    val context = LocalContext.current
    fun notificationsAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notifyAllowed by remember { mutableStateOf(notificationsAllowed()) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifyAllowed = it }

    // reload expenses whenever the screen comes back (e.g. after adding one in the wallet)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                onEvent(DayEvent.Refresh)
                notifyAllowed = notificationsAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // items above the timeline, to translate a row index into a list position
    val showBanner = state.date == state.today && state.today.dayOfWeek == java.time.DayOfWeek.MONDAY
    val leadingItems = (if (!notifyAllowed) 1 else 0) + (if (showBanner) 1 else 0) + (if (state.overdue.isNotEmpty()) 1 else 0) +
        (if (state.rows.isEmpty()) 1 else 0)
    // today opens scrolled to one hour before now; other days open at the top
    var scrolledFor by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(state.date, state.scrollIndex) {
        if (scrolledFor == state.date) return@LaunchedEffect
        val target = state.scrollIndex
        if (state.rows.isEmpty()) return@LaunchedEffect
        scrolledFor = state.date
        if (target != null) {
            listState.scrollToItem(leadingItems + target)
        } else {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!asTab) {
                FloatingActionButton(
                    onClick = { nav.navigateTo(PlannerEditScreen(epochDay = state.date.toEpochDay())) },
                    containerColor = PlannerColors.Accent,
                    contentColor = PlannerColors.OnAccent,
                ) { Icon(Icons.Filled.Add, "New entry") }
            }
        },
    ) { padding ->
      // fixed: date row, week strip and rapid log; only the timeline below scrolls
      Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        HeaderRow(
            state = state,
            showBack = !asTab,
            onBack = { nav.back() },
            onToday = { onEvent(DayEvent.SelectDate(state.today)) },
            onSearch = { nav.navigateTo(PlannerSearchScreen) },
            onMonth = { monthOpen = true },
            onToggleFilter = { onEvent(DayEvent.ToggleFilter) },
        )
        WeekStrip(
            selected = state.date,
            today = state.today,
            dotFor = { state.dots[it] },
            onSelect = { onEvent(DayEvent.SelectDate(it)) },
            onPrevWeek = { onEvent(DayEvent.SelectDate(state.date.minusWeeks(1))) },
            onNextWeek = { onEvent(DayEvent.SelectDate(state.date.plusWeeks(1))) },
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
            RapidLog(placeholder = "Rapid log…", today = state.today, onSubmit = { onEvent(DayEvent.RapidLog(it)) })
        }
        // a thin divider appears once the timeline has scrolled under the fixed part
        if (listState.canScrollBackward) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .swipeDays(
                    key = state.date,
                    onPrevious = { onEvent(DayEvent.SelectDate(state.date.minusDays(1))) },
                    onNext = { onEvent(DayEvent.SelectDate(state.date.plusDays(1))) },
                ),
            contentPadding = PaddingValues(
                top = 4.dp,
                // leave room for the app's bottom bar in tab mode, or the FAB otherwise
                bottom = padding.calculateBottomPadding() + if (asTab) 110.dp else 88.dp,
            ),
        ) {
            if (!notifyAllowed) {
                item {
                    SlimBanner("Allow notifications for reminders", "Allow") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
            if (showBanner) {
                item { SlimBanner("New week: review last week", "Review") { nav.navigateTo(PlannerReviewScreen) } }
            }
            if (state.overdue.isNotEmpty()) {
                item { OverdueSection(state.overdue, state.today, onEvent) }
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        if (state.todoOnly) "Nothing left to do." else "Nothing logged for this day yet.",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(state.rows, key = { _, row -> row.key }) { index, row ->
                Column(Modifier.padding(start = 2.dp, end = 8.dp)) {
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
                        moneyLabel = row.money?.label,
                        moneyIsIncome = row.money?.isIncome ?: false,
                        timeLabel = row.timeLabel,
                        importance = row.importance,
                        tags = row.tags,
                        photos = row.photos,
                        trailing = row.trailing,
                        routine = row.routine,
                        onClick = {
                            val m = row.money
                            val routineId = row.seriesId
                            if (row.routine != null && routineId != null) {
                                nav.navigateTo(PlannerRoutineScreen(routineId, row.date.toEpochDay()))
                            } else if (m != null) {
                                nav.navigateTo(
                                    EditTransactionScreen(
                                        initialTransactionId = m.id,
                                        type = if (m.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                    ),
                                )
                            } else {
                                nav.navigateTo(
                                    PlannerEditScreen(
                                        entryId = row.entryId,
                                        seriesId = row.seriesId,
                                        epochDay = row.date.toEpochDay(),
                                    ),
                                )
                            }
                        },
                    )
                }
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
            TextButton(onClick = {
                monthOpen = false
                nav.navigateTo(PlannerMonthScreen(java.time.YearMonth.from(state.date).key()))
            }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Open ${state.date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} month log", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
            }
            LayoutSwitch(state.focusLayout) {
                onEvent(DayEvent.ToggleLayout)
                monthOpen = false
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One compact row: date and week on the left, Today / search / month on the right. */
@Composable
private fun HeaderRow(
    state: DayState,
    showBack: Boolean,
    onBack: () -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
    onMonth: () -> Unit,
    onToggleFilter: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (showBack) 4.dp else 20.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = if (state.date == state.today) "Today, ${state.date.format(ShortDayFmt)}" else state.date.format(DayTitleFmt),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                listOf("Week ${state.date.isoWeek().week}", state.summary).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.date != state.today) {
            TextButton(onClick = onToday) { Text("Today", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
        }
        // Everything / To do
        TextButton(onClick = onToggleFilter) {
            Text(
                if (state.todoOnly) "To do" else "All",
                color = if (state.todoOnly) PlannerColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search") }
        IconButton(onClick = onMonth) { Icon(Icons.Outlined.DateRange, "Month calendar") }
    }
}

@Composable
private fun SlimBanner(text: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp)
        Text(action, color = PlannerColors.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** "Needs a decision": one slim line until tapped, then the tasks with their actions. */
@Composable
private fun OverdueSection(overdue: List<Entry>, today: LocalDate, onEvent: (DayEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf<Entry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(PlannerColors.Accent))
            Spacer(Modifier.width(10.dp))
            Text(
                "Needs a decision · ${overdue.size}",
                Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            overdue.forEach { entry ->
                Column(Modifier.padding(start = 4.dp, end = 12.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckCircle(checked = false, onToggle = { onEvent(DayEvent.OverdueDone(entry.id)) })
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            entry.date?.let {
                                Text("from ${it.withWeek()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("> Today", false, { onEvent(DayEvent.OverdueMove(entry.id, today)) })
                        Pill("< Schedule", false, { scheduling = entry })
                        Pill("Drop", false, { onEvent(DayEvent.OverdueDrop(entry.id)) })
                    }
                }
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
