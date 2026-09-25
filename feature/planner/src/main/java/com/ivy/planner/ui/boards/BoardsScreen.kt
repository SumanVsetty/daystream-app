package com.ivy.planner.ui.boards

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ivy.navigation.PlannerBoardsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.CollectionColors
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.ui.EntryRow
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RapidLog
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.ShortDayFmt
import com.ivy.planner.ui.journal.JournalViewModel
import com.ivy.planner.ui.journal.NameDialog
import com.ivy.planner.ui.journal.openEntry
import com.ivy.planner.ui.journal.toggleEntry
import com.ivy.planner.ui.label
import com.ivy.planner.ui.timelineColor
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PlannerBoardsScreenImpl(screen: PlannerBoardsScreen) {
    val vm: JournalViewModel = screenScopedViewModel()
    PlannerTheme { BoardsUi(vm, screen.boardId) }
}

@Composable
private fun BoardsUi(vm: JournalViewModel, initialBoard: String?) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    var selectedId by remember { mutableStateOf(initialBoard) }
    var showDone by remember { mutableStateOf(false) }
    var newBoard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var coloring by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val boards = lib?.collections.orEmpty().filter { it.type == CollectionType.BOARD }
    LaunchedEffect(boards.map { it.id }) {
        if (selectedId == null || boards.none { it.id == selectedId }) selectedId = boards.firstOrNull()?.id
    }
    val board = boards.firstOrNull { it.id == selectedId }
    val tasks = lib?.entries.orEmpty()
        .filter { it.kind == EntryKind.TASK && it.state != EntryState.DROPPED }
        .filter { e -> board != null && board.id in lib?.collectionsOf?.get(e.id).orEmpty() }
    val open = tasks.filter { it.state == EntryState.OPEN }
        .sortedWith(compareBy<Entry>({ it.date == null }, { it.date }, { it.time }))
    val done = tasks.filter { it.state == EntryState.DONE }.sortedByDescending { it.completedAt }
    val overdue = open.count { it.date?.isBefore(today) == true }
    // timeline colours are worked out here: the list builder below can't call composables
    val colors = open.map { timelineColor(it.kind, it.state) }
    val doneColors = done.map { timelineColor(it.kind, it.state) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 48.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    if (board != null) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(board.color)))
                    Spacer(Modifier.width(8.dp))
                    Text(board?.name ?: "Boards", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (board != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Board options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text("Colour") }, onClick = { menu = false; coloring = true })
                                DropdownMenuItem(text = { Text("Delete board") }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                }
            }
            // board chips
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    boards.forEach { b ->
                        val on = b.id == selectedId
                        Text(
                            b.name,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Color(b.color) else Color.Transparent)
                                .border(1.5.dp, Color(b.color), RoundedCornerShape(12.dp))
                                .clickable { selectedId = b.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) Color(0xFF1B1D1B) else Color(b.color),
                        )
                    }
                    TextButton(onClick = { newBoard = true }) { Text("+ New board", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
                }
            }
            if (board == null) {
                item {
                    Text(
                        if (lib == null) "" else "No boards yet. Create one, or type a task with #board-name in rapid log.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        if (tasks.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { done.size.toFloat() / tasks.size },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PlannerColors.Done,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("${done.size * 100 / tasks.size}%", fontWeight = FontWeight.ExtraBold, color = PlannerColors.Done)
                            }
                            Text(
                                "✓ ${done.size}/${tasks.size}" + if (overdue > 0) "   ·   $overdue overdue" else "",
                                fontSize = 13.sp,
                                color = if (overdue > 0) PlannerColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        RapidLog(
                            placeholder = "Add to ${board.name}…",
                            today = today,
                            onSubmit = { text -> scope.launch { vm.planner.rapidLog(text, null, vm.library, boardId = board.id) } },
                        )
                    }
                }
                itemsIndexed(open, key = { _, e -> e.id }) { i, e -> BoardRow(e, today, i, colors, vm, nav) }
                if (done.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(start = 12.dp)) {
                            Text(if (showDone) "Hide done" else "Show ${done.size} done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (showDone) {
                        itemsIndexed(done, key = { _, e -> "done:" + e.id }) { i, e -> BoardRow(e, today, i, doneColors, vm, nav) }
                    }
                }
            }
        }
    }

    if (newBoard) {
        NameDialog("New board", onDone = { name ->
            newBoard = false
            scope.launch { selectedId = vm.library.saveCollection(name, CollectionType.BOARD) }
        }, onDismiss = { newBoard = false })
    }
    if (renaming && board != null) {
        NameDialog("Rename board", initial = board.name, confirm = "Save", onDone = { name ->
            renaming = false
            scope.launch { vm.library.saveCollection(name, board.type, board.id, board.color) }
        }, onDismiss = { renaming = false })
    }
    if (coloring && board != null) {
        AlertDialog(
            onDismissRequest = { coloring = false },
            title = { Text("Board colour") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    CollectionColors.all.forEach { c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c)).clickable {
                                coloring = false
                                scope.launch { vm.library.saveCollection(board.name, board.type, board.id, c) }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { coloring = false }) { Text("Close") } },
        )
    }
    if (deleting && board != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${board.name}?") },
            text = { Text("The board is removed; its tasks stay in your planner.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch { vm.library.deleteCollection(board.id) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/** A task on a board: its date (or "Unplanned") in the time column. */
@Composable
private fun BoardRow(
    e: Entry,
    today: LocalDate,
    index: Int,
    colors: List<Color>,
    vm: JournalViewModel,
    nav: com.ivy.navigation.Navigation,
) {
    val scope = rememberCoroutineScope()
    val late = e.state == EntryState.OPEN && e.date?.isBefore(today) == true
    Box(Modifier.padding(start = 2.dp, end = 8.dp)) {
        EntryRow(
            kind = e.kind,
            title = e.title,
            description = e.description,
            meta = listOfNotNull(
                e.date?.let { if (it == today) "Today" else it.format(ShortDayFmt) },
                if (late) "overdue" else null,
            ).joinToString(" · "),
            state = e.state,
            repeating = false,
            onToggle = { scope.launch { toggleEntry(vm.planner, e) } },
            onClick = { openEntry(nav, e) },
            timeLabel = e.time?.label() ?: "",
            trailing = if (e.time != null) "${e.durationMinutes ?: 15} min" else "",
            lineAbove = if (index > 0) colors[index - 1] else null,
            lineBelow = if (index < colors.lastIndex) colors[index] else null,
        )
    }
}
