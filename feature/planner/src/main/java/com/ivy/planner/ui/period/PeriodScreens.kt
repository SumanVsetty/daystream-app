package com.ivy.planner.ui.period

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.ivy.navigation.Navigation
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerMonthReviewScreen
import com.ivy.navigation.PlannerMonthScreen
import com.ivy.navigation.PlannerTimelineScreen
import com.ivy.navigation.PlannerYearScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.Importance
import com.ivy.planner.domain.PeriodStats
import com.ivy.planner.domain.Periods
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.domain.key
import com.ivy.planner.domain.monthOf
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.MoneySource
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.add.PlannerAddSheet
import com.ivy.planner.ui.add.PlannerAddSheetHost
import com.ivy.planner.ui.formatShort
import com.ivy.planner.ui.importanceColor
import com.ivy.planner.ui.journal.PersonAvatar
import com.ivy.planner.ui.withWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PeriodViewModel @Inject constructor(
    val planner: PlannerRepository,
    val library: LibraryRepository,
    val money: MoneySource,
) : ViewModel()

private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

// ---------------------------------------------------------------- month log

@Composable
fun PlannerMonthScreenImpl(screen: PlannerMonthScreen) {
    val vm: PeriodViewModel = screenScopedViewModel()
    PlannerTheme {
        MonthUi(vm, screen.monthKey?.let(::monthOf) ?: YearMonth.now())
        PlannerAddSheetHost()
    }
}

@Composable
private fun MonthUi(vm: PeriodViewModel, start: YearMonth) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(start) }
    val today = LocalDate.now()
    val from = month.atDay(1)
    val to = month.atEndOfMonth()
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    val goals by remember(month) { vm.planner.observeMonthGoals(month) }.collectAsState(initial = emptyList())
    val stats by produceState<PeriodStats?>(null, month, lib) {
        val (entries, series, records) = vm.planner.periodData(from, to)
        value = Periods.stats(from, to, today, entries, series, records)
    }
    val money by produceState(initialValue = emptyList<com.ivy.planner.ui.MoneyItem>(), month) { value = vm.money.between(from, to) }
    val spentByDay = money.filter { !it.isIncome }.groupBy { it.date }
    val spentTotal = money.filter { !it.isIncome }.let { l -> if (l.isEmpty()) null else formatShort(l.sumOf { it.amount }, l.first().currency) }
    val entries = lib?.entries.orEmpty()
    val notable = Periods.notableByDay(from, to, entries)
    val days = (notable.keys + spentByDay.keys).distinct().sorted()
    val openTasks = entries.count { it.kind == EntryKind.TASK && it.state == EntryState.OPEN && it.date?.let { d -> !d.isBefore(from) && !d.isAfter(to) && d.isBefore(today) } == true } +
        goals.count { it.state == EntryState.OPEN }
    var newGoal by remember { mutableStateOf("") }
    val addGoal = {
        if (newGoal.isNotBlank()) {
            val t = newGoal
            scope.launch { vm.planner.addMonthGoal(t, month) }
        }
        newGoal = ""
    }

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 40.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Month log")
                        Text("${monthName(month)} ${month.year}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("W${from.isoWeek().week}–W${to.isoWeek().week}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous month") }
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.KeyboardArrowRight, "Next month") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stats?.taskPercent?.let { "$it%" } ?: "–", "tasks done", PlannerColors.Done, Modifier.weight(1f))
                    Stat(stats?.routinePercent?.let { "$it%" } ?: "–", "routines", PlannerColors.Routine, Modifier.weight(1f))
                    Stat(spentTotal ?: "–", "spent", PlannerColors.Accent, Modifier.weight(1f))
                    Stat("${stats?.memories ?: 0}", "memories", PlannerColors.Journal, Modifier.weight(1f))
                }
            }
            // goals
            item { Header("This month · goals") }
            goals.sortedBy { it.state == EntryState.DONE }.forEach { g ->
                item(key = "g:" + g.id) {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigateTo(PlannerEditScreen(entryId = g.id)) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckCircle(checked = g.state == EntryState.DONE, onToggle = {
                            scope.launch { vm.planner.setState(g.id, if (g.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE) }
                        })
                        Text(
                            g.title,
                            Modifier.padding(start = 6.dp),
                            fontSize = 15.sp,
                            color = if (g.state == EntryState.DONE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (g.state == EntryState.DONE) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = newGoal,
                    onValueChange = { newGoal = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Add a goal for ${monthName(month)}") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addGoal() }),
                )
            }
            // days
            item { Header("Days") }
            if (days.isEmpty()) {
                item { Text("Nothing logged this month yet.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            days.forEach { day ->
                item(key = "d:$day") {
                    val e = notable[day]
                    val spent = spentByDay[day]?.let { l -> formatShort(l.sumOf { it.amount }, l.first().currency) }
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigateTo(PlannerDayScreen(day.toEpochDay())) }.padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + day.dayOfMonth,
                            Modifier.width(56.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (day == today) PlannerColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(
                                when (e?.kind) {
                                    EntryKind.JOURNAL -> importanceColor(e?.importance ?: 0)
                                    EntryKind.EVENT -> PlannerColors.Event
                                    EntryKind.NOTE -> PlannerColors.Done
                                    else -> Color.Transparent
                                },
                            ),
                        )
                        Text(e?.title ?: "", Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp, maxLines = 1)
                        if (spent != null) Text(spent, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PlannerColors.Accent)
                    }
                }
            }
            // close the month
            if (!from.isAfter(today)) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel("Close the month")
                        Text(
                            if (openTasks == 0) "Nothing left open. Take a moment to reflect on ${monthName(month)}."
                            else "$openTasks ${if (openTasks == 1) "task" else "tasks"} still open. Migrate them to ${monthName(month.plusMonths(1))}, or let them go.",
                            fontSize = 14.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (openTasks > 0) {
                                ActionChip("Review $openTasks", PlannerColors.Accent) { nav.navigateTo(PlannerMonthReviewScreen(month.key())) }
                            }
                            ActionChip("Write a reflection", null) {
                                PlannerAddSheet.open(EntryKind.JOURNAL, minOf(to, today), "${monthName(month)} reflection: ")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- year in review

@Composable
fun PlannerYearScreenImpl(screen: PlannerYearScreen) {
    val vm: PeriodViewModel = screenScopedViewModel()
    PlannerTheme {
        YearUi(vm, screen.year ?: LocalDate.now().year)
        PlannerAddSheetHost()
    }
}

@Composable
private fun YearUi(vm: PeriodViewModel, startYear: Int) {
    val nav = navigation()
    var year by remember { mutableStateOf(startYear) }
    val today = LocalDate.now()
    val from = LocalDate.of(year, 1, 1)
    val to = LocalDate.of(year, 12, 31)
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    val stats by produceState<PeriodStats?>(null, year, lib) {
        val (entries, series, records) = vm.planner.periodData(from, to)
        value = Periods.stats(from, to, today, entries, series, records)
    }
    val l = lib
    val inYear = l?.entries.orEmpty().filter { it.date?.year == year }
    val journal = inYear.filter { it.kind == EntryKind.JOURNAL }
    val moments = journal.filter { it.importance == Importance.LIFE_CHANGING }.ifEmpty { journal.filter { it.importance == Importance.IMPORTANT } }
        .sortedBy { it.date }
    val daysLogged = inYear.mapNotNull { it.date }.distinct().size
    val people = l?.people.orEmpty()
        .map { p -> p to inYear.count { p.id in l?.peopleOf?.get(it.id).orEmpty() } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(4)
    val collections = l?.collections.orEmpty().filter { it.type == CollectionType.TOPIC }
        .map { c -> c to inYear.count { c.id in l?.collectionsOf?.get(it.id).orEmpty() } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(3)
    val byMonth = Periods.memoriesByMonth(year, l?.entries.orEmpty())

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 40.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Year in review")
                        Text("$year", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "A year of ${journal.size} ${if (journal.size == 1) "memory" else "memories"} across $daysLogged days",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { year -= 1 }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous year") }
                    IconButton(onClick = { year += 1 }, enabled = year < today.year) { Icon(Icons.Filled.KeyboardArrowRight, "Next year") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${stats?.tasksDone ?: 0}", "tasks done", PlannerColors.Done, Modifier.weight(1f))
                    Stat(stats?.routinePercent?.let { "$it%" } ?: "–", "routines", PlannerColors.Routine, Modifier.weight(1f))
                    Stat("${journal.size}", "memories", PlannerColors.Journal, Modifier.weight(1f))
                    Stat("${stats?.lifeChanging ?: 0}", "life-changing", importanceColor(4), Modifier.weight(1f))
                }
            }
            if (moments.isNotEmpty() && l != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Header(if (moments.first().importance == Importance.LIFE_CHANGING) "Moments that changed things" else "Important moments") }
                        TextButton(onClick = { nav.navigateTo(PlannerTimelineScreen(importance = moments.first().importance)) }) {
                            Text("All ${moments.size}", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        moments.forEach { e -> MomentCard(e, l, vm, nav) }
                    }
                }
            }
            if (people.isNotEmpty()) {
                item {
                    Header("People who appeared most")
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        people.forEach { (p, count) ->
                            Column(
                                Modifier.width(64.dp).clickable { nav.navigateTo(PlannerTimelineScreen(personId = p.id)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PersonAvatar(p.name, size = 44, photo = p.photoFile?.let(vm.library::photoFile))
                                Text(p.name, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                Text("$count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Header("Memories by month")
                val max = (byMonth.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    byMonth.forEachIndexed { i, count ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(14.dp)
                                    .height((4 + 56 * count / max).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (count > 0) PlannerColors.Journal else MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text("JFMAMJJASOND"[i].toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (collections.isNotEmpty()) {
                item {
                    Header("Most active collections")
                    collections.forEach { (c, count) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.navigateTo(PlannerTimelineScreen(collectionId = c.id)) }.padding(horizontal = 20.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(c.color)))
                            Text(c.name, Modifier.padding(start = 10.dp), fontSize = 14.sp)
                            Text("  · $count", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { PlannerAddSheet.open(EntryKind.JOURNAL, to, "$year in review: ") }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionLabel("Your reflection")
                    Text("What made this year yours? It becomes a journal entry on 31 Dec.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MomentCard(e: Entry, lib: com.ivy.planner.data.Library, vm: PeriodViewModel, nav: Navigation) {
    val photo = lib.photosOf[e.id]?.firstOrNull()?.let(vm.library::photoFile)
    val people = lib.peopleOf[e.id].orEmpty().mapNotNull { lib.person(it)?.name }
    Column(
        Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { nav.navigateTo(com.ivy.navigation.PlannerEntryViewScreen(e.id)) },
    ) {
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(110.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                if (e.importance == Importance.LIFE_CHANGING) "LIFE-CHANGING" else "IMPORTANT",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = importanceColor(e.importance),
            )
            Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text((listOfNotNull(e.date?.withWeek()) + people).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Header(text: String) {
    Box(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)) { SectionLabel(text, MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ActionChip(label: String, color: Color?, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color ?: MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (color != null) PlannerColors.OnAccent else MaterialTheme.colorScheme.onSurface,
    )
}
