package com.ivy.planner.ui.journal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.PlannerTimelineScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.Memories
import com.ivy.planner.domain.Memory
import com.ivy.planner.domain.RepeatRule
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.importanceColor
import com.ivy.planner.ui.withWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Gives the journal screens access to the planner's data. */
@HiltViewModel
class JournalViewModel @Inject constructor(
    val library: LibraryRepository,
    val planner: PlannerRepository,
    val prefs: com.ivy.planner.data.PlannerPrefs,
    val money: com.ivy.planner.ui.MoneySource,
) : ViewModel() {
    init {
        com.ivy.planner.ui.ImportanceNames.labels = prefs.importanceLabels
    }
}

/** Opens an entry in the editor. */
internal fun openEntry(nav: com.ivy.navigation.Navigation, e: Entry) =
    nav.navigateTo(PlannerEditScreen(entryId = e.id, epochDay = e.date?.toEpochDay(), kind = e.kind.name))

/** Ticks a task off (other kinds just open). */
internal suspend fun toggleEntry(planner: PlannerRepository, e: Entry) {
    if (e.kind != EntryKind.TASK) return
    planner.setState(e.id, if (e.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE)
}

@Composable
fun PlannerJournalTab() {
    val vm: JournalViewModel = screenScopedViewModel()
    PlannerTheme { JournalUi(vm) }
}

@Composable
private fun JournalUi(vm: JournalViewModel) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    val series by remember { vm.planner.observeSeries() }.collectAsState(initial = emptyList())
    var addPerson by remember { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold { padding ->
        val l = lib
        val journal = l?.entries.orEmpty().filter { it.kind == EntryKind.JOURNAL || it.kind == EntryKind.NOTE }
        val memories = remember(l, series, today) {
            if (l == null) emptyList() else Memories.pick(
                today = today,
                entries = l.entries,
                peopleOf = l.peopleOf,
                personName = { id -> l.person(id)?.name },
                yearlyEvents = series
                    .filter { it.kind == EntryKind.EVENT && (it.schedule.rule as? RepeatRule.MonthlyOnDay)?.interval == 12 }
                    .mapNotNull { s -> s.schedule.nextOnOrAfter(today.plusDays(1))?.let { s.title to it } },
            )
        }
        val since = journal.mapNotNull { it.date?.year }.minOrNull()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 110.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Journal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${journal.count { it.kind == EntryKind.JOURNAL }} memories" + (since?.let { " · since $it" } ?: ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { nav.navigateTo(PlannerSearchScreen) }) { Icon(Icons.Outlined.Search, "Search") }
                }
            }
            // importance cards: tap to see that level
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cool", "Unusual", "Important", "Life-changing").forEachIndexed { i, label ->
                        val level = i + 1
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { nav.navigateTo(PlannerTimelineScreen(importance = level)) }
                                .padding(10.dp),
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(importanceColor(level)))
                            Text("${journal.count { it.importance == level }}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (l != null && memories.isNotEmpty()) {
                item { MemoriesCard(memories, l, vm, nav) }
            }
            // people
            item {
                SectionHeader("People")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    l?.people.orEmpty().forEach { p ->
                        val ids = l?.peopleOf.orEmpty().filterValues { p.id in it }.keys
                        val theirs = l?.entries.orEmpty().filter { it.id in ids }
                        val latest = theirs.filter { it.date != null }.maxByOrNull { it.date!! }
                        InfoCard(
                            onClick = { nav.navigateTo(PlannerTimelineScreen(personId = p.id)) },
                            top = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PersonAvatar(p.name, size = 38, photo = p.photoFile?.let(vm.library::photoFile))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${theirs.size} memories", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            bottom = latest?.let { "Latest: ${it.title}" },
                        )
                    }
                    AddCard("Add person") { addPerson = true }
                }
            }
            // collections: milestone timelines
            item {
                SectionHeader("Collections")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    l?.collections.orEmpty().filter { it.type == CollectionType.TOPIC }.forEach { c ->
                        val theirs = l?.entries.orEmpty().filter { c.id in l?.collectionsOf?.get(it.id).orEmpty() }
                        val latest = theirs.filter { it.date != null }.maxByOrNull { it.date!! }
                        val first = theirs.mapNotNull { it.date?.year }.minOrNull()
                        InfoCard(
                            onClick = { nav.navigateTo(PlannerTimelineScreen(collectionId = c.id)) },
                            accent = Color(c.color),
                            cover = c.coverFile?.let(vm.library::photoFile),
                            top = {
                                Column {
                                    Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        "${theirs.size} milestones" + (first?.let { " · since $it" } ?: ""),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            bottom = latest?.let { "Latest: ${it.title}" },
                        )
                    }
                    AddCard("New collection") { addCollection = true }
                }
            }
            // recent
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionLabel("Recent", MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { nav.navigateTo(PlannerTimelineScreen()) }) {
                        Text("Timeline", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (l != null && journal.isEmpty()) {
                item {
                    Text(
                        "Your journal is empty. Tap the logo to write your first entry, or log an older memory with its date.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (l != null) {
                libraryTimeline(
                    entries = journal.newestFirst().take(10),
                    lib = l,
                    photoFile = vm.library::photoFile,
                    onOpen = { openEntry(nav, it) },
                    onToggle = { e -> scope.launch { toggleEntry(vm.planner, e) } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (addPerson) {
        NameDialog("New person", onDone = { name ->
            addPerson = false
            scope.launch { vm.library.savePerson(name) }
        }, onDismiss = { addPerson = false })
    }
    if (addCollection) {
        NameDialog("New collection", onDone = { name ->
            addCollection = false
            scope.launch { vm.library.saveCollection(name, CollectionType.TOPIC) }
        }, onDismiss = { addCollection = false })
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) {
        SectionLabel(title, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A wallet-style card: a top part, an optional "Latest: …" line, and a coloured top edge. */
@Composable
private fun InfoCard(
    onClick: () -> Unit,
    top: @Composable () -> Unit,
    bottom: String?,
    accent: Color? = null,
    cover: java.io.File? = null,
) {
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (cover != null) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(64.dp))
        } else if (accent != null) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top()
            if (bottom != null) {
                Text(bottom, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
private fun AddCard(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(110.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ $label", color = PlannerColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Swipe sideways through today's memories; dots show how many there are. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoriesCard(
    memories: List<Memory>,
    lib: com.ivy.planner.data.Library,
    vm: JournalViewModel,
    nav: com.ivy.navigation.Navigation,
) {
    val pager = rememberPagerState(pageCount = { memories.size })
    Column(Modifier.padding(top = 4.dp)) {
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { page ->
            val m = memories[page]
            val entry = m.entryId?.let { id -> lib.entries.firstOrNull { it.id == id } }
            val photo = m.entryId?.let { lib.photosOf[it]?.firstOrNull() }?.let(vm.library::photoFile)
            val people = m.entryId?.let { lib.peopleOf[it] }.orEmpty().mapNotNull { lib.person(it)?.name }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = entry != null) { entry?.let { openEntry(nav, it) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = importanceColor(entry?.importance ?: 0), modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PlannerColors.Accent, letterSpacing = 0.8.sp, maxLines = 2)
                    Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        (listOfNotNull(m.date?.withWeek()) + people).joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (memories.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(memories.size) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pager.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) PlannerColors.Accent else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}
