package com.ivy.planner.ui.journal

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
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
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Gives the journal screens access to the planner's data. */
@HiltViewModel
class JournalViewModel @Inject constructor(
    val library: LibraryRepository,
    val planner: PlannerRepository,
) : ViewModel()

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
    var importance by remember { mutableStateOf(setOf<Int>()) }
    var addPerson by remember { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }

    Scaffold { padding ->
        val l = lib
        val entries = l?.entries.orEmpty()
            .filter { it.kind == EntryKind.JOURNAL || it.kind == EntryKind.NOTE }
            .filter { importance.isEmpty() || it.importance in importance }
            .newestFirst()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 110.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Journal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${l?.entries.orEmpty().count { it.kind == EntryKind.JOURNAL }} entries · " +
                                "${l?.people.orEmpty().size} people · " +
                                "${l?.collections.orEmpty().count { it.type == CollectionType.TOPIC }} collections",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { nav.navigateTo(PlannerSearchScreen) }) { Icon(Icons.Outlined.Search, "Search") }
                }
            }
            // people
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    Box(Modifier.padding(start = 20.dp)) { SectionLabel("People", MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        l?.people.orEmpty().forEach { p ->
                            Column(
                                Modifier.clickable { nav.navigateTo(PlannerTimelineScreen(personId = p.id)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PersonAvatar(p.name)
                                Text(p.name, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp).width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        Column(Modifier.clickable { addPerson = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add person", tint = PlannerColors.Accent) }
                            Text("Add", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            // collections
            item {
                Column {
                    Box(Modifier.padding(start = 20.dp)) { SectionLabel("Collections", MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        l?.collections.orEmpty().filter { it.type == CollectionType.TOPIC }.forEach { c ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { nav.navigateTo(PlannerTimelineScreen(collectionId = c.id)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(c.color)))
                                Spacer(Modifier.width(6.dp))
                                Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        TextButton(onClick = { addCollection = true }) { Text("+ New", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                ImportanceFilter(
                    selected = importance,
                    onToggle = { importance = if (it in importance) importance - it else importance + it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (l != null && entries.isEmpty()) {
                item {
                    Text(
                        if (importance.isEmpty()) "Your journal is empty. Tap the logo to write your first entry."
                        else "No entries with this importance.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (l != null) {
                libraryTimeline(
                    entries = entries,
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
