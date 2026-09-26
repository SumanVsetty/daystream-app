package com.ivy.planner.ui.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.PlannerTimelineScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.ui.MoneyItem
import com.ivy.planner.ui.MoneySummary
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.shortDay
import com.ivy.planner.ui.summarize
import kotlinx.coroutines.launch

/** The timeline of one person or one collection, with importance and type filters. */
@Composable
fun PlannerTimelineScreenImpl(screen: PlannerTimelineScreen) {
    val vm: JournalViewModel = screenScopedViewModel()
    PlannerTheme { TimelineUi(vm, screen.personId, screen.collectionId, screen.importance) }
}

private enum class TypeFilter(val label: String, val kinds: Set<EntryKind>?) {
    ALL("All", null),
    JOURNAL("Journal", setOf(EntryKind.JOURNAL)),
    TASKS("Tasks", setOf(EntryKind.TASK)),
    EVENTS("Events", setOf(EntryKind.EVENT)),
    NOTES("Notes", setOf(EntryKind.NOTE)),
}

@Composable
private fun TimelineUi(vm: JournalViewModel, personId: String?, collectionId: String?, startImportance: Int?) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    var importance by remember { mutableStateOf(setOfNotNull(startImportance)) }
    var type by remember { mutableStateOf(TypeFilter.ALL) }
    var year by remember { mutableStateOf<Int?>(null) }
    val allMemories = personId == null && collectionId == null
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var tagsDialog by remember { mutableStateOf(false) }
    var showExpenses by remember { mutableStateOf(false) }
    var linksVersion by remember { mutableIntStateOf(0) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                personId?.let { vm.library.setPersonPhoto(it, uri) }
                collectionId?.let { vm.library.setCollectionCover(it, uri) }
            }
        }
    }

    val l = lib
    val person = personId?.let { l?.person(it) }
    val collection = collectionId?.let { l?.collection(it) }
    val name = person?.name ?: collection?.name ?: if (allMemories) "Memories" else ""
    val all = l?.entries.orEmpty().filter { e ->
        if (allMemories) {
            e.kind == EntryKind.JOURNAL
        } else {
            (personId != null && personId in l?.peopleOf?.get(e.id).orEmpty()) ||
                (collectionId != null && collectionId in l?.collectionsOf?.get(e.id).orEmpty())
        }
    }
    val years = all.mapNotNull { it.date?.year }.distinct().sortedDescending()
    // counts on the type chips follow the other filters (year and importance)
    val otherFilters = all
        .filter { year == null || it.date?.year == year }
        .filter { importance.isEmpty() || it.importance in importance }
    val shown = otherFilters
        .filter { type.kinds == null || it.kind in type.kinds!! }
        .newestFirst()
    val filtering = year != null || importance.isNotEmpty() || type != TypeFilter.ALL
    val since = all.mapNotNull { it.date }.minOrNull()

    // money: wallet tags linked by you, or by matching name
    val owner = personId ?: collectionId
    val walletTags by produceState(initialValue = emptyList<Pair<java.util.UUID, String>>()) { value = vm.money.walletTags() }
    val linkedTags = remember(owner, name, walletTags, linksVersion) {
        val chosen = owner?.let { vm.prefs.tagLinks[it] }
        if (chosen != null) {
            walletTags.filter { it.first.toString() in chosen }
        } else {
            walletTags.filter { it.second.trim().equals(name.trim(), ignoreCase = true) }
        }
    }
    val moneyItems by produceState(initialValue = emptyList<MoneyItem>(), linkedTags) {
        value = vm.money.tagged(linkedTags.map { it.first }.toSet())
    }
    val moneySummary = remember(moneyItems) { summarize(moneyItems, java.time.LocalDate.now().year) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
        ) {
            collection?.coverFile?.let { cover ->
                item {
                    AsyncImage(
                        model = vm.library.photoFile(cover),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    if (person != null) PersonAvatar(person.name, size = 40, photo = person.photoFile?.let(vm.library::photoFile))
                    if (collection != null) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(collection.color)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            (if (filtering) "${shown.size} of ${all.size} entries" else "${all.size} entries") + (since?.let { " · since ${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${it.year}" } ?: ""),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!allMemories) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Wallet tags") }, onClick = { menu = false; tagsDialog = true })
                            DropdownMenuItem(
                                text = { Text(if (person != null) "Change photo" else "Change cover photo") },
                                onClick = {
                                    menu = false
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = true })
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TypeFilter.values().forEach { t ->
                        val count = if (t.kinds == null) otherFilters.size else otherFilters.count { it.kind in t.kinds }
                        if (t == TypeFilter.ALL || count > 0) {
                            Pill(if (t == TypeFilter.ALL) "All" else "${t.label} $count", type == t, { type = t })
                        }
                    }
                }
            }
            if (!allMemories && moneyItems.isNotEmpty()) {
                item {
                    MoneyCard(
                        summary = moneySummary,
                        tags = linkedTags.map { it.second },
                        expanded = showExpenses,
                        onToggle = { showExpenses = !showExpenses },
                    )
                }
                if (showExpenses) {
                    items(moneyItems.take(50), key = { "m:" + it.id }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    nav.navigateTo(
                                        EditTransactionScreen(
                                            initialTransactionId = m.id,
                                            type = if (m.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                        ),
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.date.shortDay(), Modifier.width(96.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(m.title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                            Text(m.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (m.isIncome) PlannerColors.Done else PlannerColors.Accent)
                        }
                    }
                    if (moneyItems.size > 50) {
                        item { Text("Showing the latest 50 of ${moneyItems.size}", Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (years.size > 1) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Pill("All years", year == null, { year = null })
                        years.forEach { y -> Pill("$y", year == y, { year = if (year == y) null else y }) }
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
            if (l != null && shown.isEmpty()) {
                item {
                    Text(
                        when {
                            all.isNotEmpty() -> "No entries match these filters."
                            allMemories -> "No memories yet."
                            else -> "Nothing here yet. Tag entries with ${name.ifBlank { "this" }} in the editor."
                        },
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (l != null) {
                libraryTimeline(
                    entries = shown,
                    lib = l,
                    photoFile = vm.library::photoFile,
                    onOpen = { openEntry(nav, it) },
                    onToggle = { e -> scope.launch { toggleEntry(vm.planner, e) } },
                    hidePersonId = personId,
                    hideCollectionId = collectionId,
                )
            }
        }
    }

    if (renaming) {
        NameDialog("Rename", initial = name, confirm = "Save", onDone = { newName ->
            renaming = false
            scope.launch {
                if (person != null) vm.library.savePerson(newName, person.id)
                if (collection != null) vm.library.saveCollection(newName, collection.type, collection.id, collection.color)
            }
        }, onDismiss = { renaming = false })
    }
    if (tagsDialog && owner != null) {
        WalletTagsDialog(
            tags = walletTags,
            selected = linkedTags.map { it.first.toString() }.toSet(),
            onSave = { chosen ->
                vm.prefs.tagLinks = vm.prefs.tagLinks + (owner to chosen)
                linksVersion++
                tagsDialog = false
            },
            onDismiss = { tagsDialog = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete $name?") },
            text = { Text("Entries stay; they just lose this tag.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        if (person != null) vm.library.deletePerson(person.id)
                        if (collection != null) vm.library.deleteCollection(collection.id)
                        nav.back()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/** Money spent with a person or on a collection, from the linked wallet tags. */
@Composable
private fun MoneyCard(summary: MoneySummary, tags: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Money · " + tags.joinToString(", ") { "#$it" })
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "Hide" else "${summary.count} expenses", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(summary.thisYear ?: "Nothing", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PlannerColors.Accent)
            Text("  this year", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        summary.allTime?.let { Text("$it all time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (summary.topCategories.isNotEmpty()) {
            Text(summary.topCategories.joinToString(" · "), fontSize = 13.sp)
        }
    }
}

/** Choose which wallet tags belong to this person or collection. */
@Composable
private fun WalletTagsDialog(
    tags: List<Pair<java.util.UUID, String>>,
    selected: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wallet tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (tags.isEmpty()) {
                    Text("No tags in the wallet yet. Add tags to expenses in the Money tab, then link them here.")
                }
                Text(
                    "Expenses with these tags show on this page. Without a choice, the tag with the same name is used.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                tags.forEach { (id, tagName) ->
                    val key = id.toString()
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (key in chosen) chosen - key else chosen + key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = key in chosen, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("#$tagName")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
