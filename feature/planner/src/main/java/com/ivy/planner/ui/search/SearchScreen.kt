package com.ivy.planner.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.journal.ImportanceFilter
import com.ivy.planner.ui.journal.JournalViewModel
import com.ivy.planner.ui.journal.libraryTimeline
import com.ivy.planner.ui.journal.newestFirst
import com.ivy.planner.ui.journal.openEntry
import com.ivy.planner.ui.journal.toggleEntry
import kotlinx.coroutines.launch

/** Search across everything, with filters that combine freely. */
@Composable
fun PlannerSearchScreenImpl() {
    val vm: JournalViewModel = screenScopedViewModel()
    PlannerTheme { SearchUi(vm) }
}

@Composable
private fun SearchUi(vm: JournalViewModel) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<EntryKind?>(null) }
    var importance by remember { mutableStateOf(setOf<Int>()) }
    var personId by remember { mutableStateOf<String?>(null) }
    var collectionId by remember { mutableStateOf<String?>(null) }
    var photosOnly by remember { mutableStateOf(false) }

    val l = lib
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val filtering = words.isNotEmpty() || kind != null || importance.isNotEmpty() || personId != null ||
        collectionId != null || photosOnly
    val results = if (l == null || !filtering) emptyList() else l.entries
        .filter { e -> words.all { w -> e.title.lowercase().contains(w) || e.description.lowercase().contains(w) } }
        .filter { kind == null || it.kind == kind }
        .filter { importance.isEmpty() || it.importance in importance }
        .filter { personId == null || personId in l.peopleOf[it.id].orEmpty() }
        .filter { collectionId == null || collectionId in l.collectionsOf[it.id].orEmpty() }
        .filter { !photosOnly || l.photosOf[it.id].orEmpty().isNotEmpty() }
        .newestFirst()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search everything") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Menu(
                        label = kind?.let { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } } ?: "Type",
                        active = kind != null,
                        options = listOf<Pair<EntryKind?, String>>(null to "Any type") + EntryKind.values().map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onPick = { kind = it },
                    )
                    Menu(
                        label = personId?.let { l?.person(it)?.name } ?: "Person",
                        active = personId != null,
                        options = listOf<Pair<String?, String>>(null to "Anyone") + l?.people.orEmpty().map { it.id to it.name },
                        onPick = { personId = it },
                    )
                    Menu(
                        label = collectionId?.let { l?.collection(it)?.name } ?: "Collection or board",
                        active = collectionId != null,
                        options = listOf<Pair<String?, String>>(null to "Any") + l?.collections.orEmpty().map {
                            it.id to (it.name + if (it.type == CollectionType.BOARD) " (board)" else "")
                        },
                        onPick = { collectionId = it },
                    )
                    Pill("Has photos", photosOnly, { photosOnly = !photosOnly })
                }
            }
            item {
                ImportanceFilter(
                    selected = importance,
                    onToggle = { importance = if (it in importance) importance - it else importance + it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (filtering) {
                item {
                    Text(
                        "${results.size} results",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            if (l != null) {
                libraryTimeline(
                    entries = results,
                    lib = l,
                    photoFile = vm.library::photoFile,
                    onOpen = { openEntry(nav, it) },
                    onToggle = { e -> scope.launch { toggleEntry(vm.planner, e) } },
                )
            }
        }
    }
}

/** A filter chip that opens a small menu of choices. */
@Composable
private fun <T> Menu(label: String, active: Boolean, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill("$label ▾", active, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}
