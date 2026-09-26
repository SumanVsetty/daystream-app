package com.ivy.planner.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.ReminderPlanner
import com.ivy.planner.ui.KindChip
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.PlannerTimePicker
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.journal.ImportanceNamesDialog
import com.ivy.planner.ui.journal.ImportancePicker
import com.ivy.planner.ui.journal.NameDialog
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PlannerEditScreenImpl(screen: PlannerEditScreen) {
    val viewModel: EditorViewModel = screenScopedViewModel()
    LaunchedEffect(screen) {
        viewModel.onEvent(
            EditorEvent.Load(
                entryId = screen.entryId,
                seriesId = screen.seriesId,
                date = screen.epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
                kind = runCatching { EntryKind.valueOf(screen.kind) }.getOrDefault(EntryKind.TASK),
            ),
        )
    }
    PlannerTheme { EditorUi(viewModel.uiState(), viewModel::onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorUi(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    val nav = navigation()
    LaunchedEffect(state.closed) { if (state.closed) nav.back() }

    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }
    var editNames by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.isNew -> "New entry"
                            state.isOccurrence -> "Repeating ${state.kind.name.lowercase()}"
                            else -> "Edit ${state.kind.name.lowercase()}"
                        },
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    Button(
                        onClick = { onEvent(EditorEvent.Save) },
                        enabled = state.title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlannerColors.Accent,
                            contentColor = PlannerColors.OnAccent,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isOccurrence) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EntryKind.TASK, EntryKind.EVENT, EntryKind.NOTE, EntryKind.JOURNAL).forEach { k ->
                        KindChip(k, selected = state.kind == k) { onEvent(EditorEvent.SetKind(k)) }
                    }
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = { onEvent(EditorEvent.SetTitle(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { onEvent(EditorEvent.SetDescription(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
            )

            // settings as compact chips, like the add sheet
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EditorChip(Icons.Outlined.CalendarToday, state.date.withWeek(), set = true, enabled = !state.isOccurrence) { pickDate = true }
                EditorChip(
                    Icons.Outlined.Schedule,
                    state.time?.label() ?: when (state.kind) {
                        EntryKind.TASK -> "Auto time"
                        EntryKind.EVENT -> "All day"
                        else -> "Now"
                    },
                    set = state.time != null,
                    onClear = if (state.time != null) ({ onEvent(EditorEvent.SetTime(null)) }) else null,
                ) { pickTime = true }
                if (state.kind == EntryKind.TASK || state.kind == EntryKind.EVENT) {
                    Box {
                        EditorChip(Icons.Outlined.Timer, durationLabel(state.durationMinutes), set = true) { durationMenu = true }
                        DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(durationLabel(m)) },
                                    onClick = {
                                        durationMenu = false
                                        onEvent(EditorEvent.SetDuration(m))
                                    },
                                )
                            }
                        }
                    }
                    EditorChip(Icons.Outlined.Repeat, if (state.schedule != null) state.repeatLabel else "Repeat", set = state.schedule != null) {
                        repeatMenu = true
                    }
                    RemindersField(state, onEvent)
                }
                if (state.kind == EntryKind.TASK) {
                    BoardField(state, onEvent)
                    EditorChip(Icons.Filled.Star, "Today's 3", set = state.focus) { onEvent(EditorEvent.ToggleFocus) }
                }
            }
            if (state.kind == EntryKind.TASK && !state.isOccurrence) {
                ChecklistField(state, onEvent)
            }
            if (state.kind == EntryKind.JOURNAL) {
                HorizontalDivider()
                SectionLabel("Importance", MaterialTheme.colorScheme.onSurfaceVariant)
                ImportancePicker(state.importance, { onEvent(EditorEvent.SetImportance(it)) }, onEditNames = { editNames = true })
                SectionLabel("People", MaterialTheme.colorScheme.onSurfaceVariant)
                ChipPicker(
                    items = state.library?.people.orEmpty().map { it.id to it.name },
                    selected = state.peopleIds,
                    addLabel = "+ Person",
                    onToggle = { onEvent(EditorEvent.TogglePerson(it)) },
                    onAdd = { addPerson = true },
                )
                SectionLabel("Collections", MaterialTheme.colorScheme.onSurfaceVariant)
                ChipPicker(
                    items = state.library?.collections.orEmpty().filter { it.type == CollectionType.TOPIC }.map { it.id to it.name },
                    selected = state.collectionIds,
                    addLabel = "+ Collection",
                    onToggle = { onEvent(EditorEvent.ToggleCollection(it)) },
                    onAdd = { addCollection = true },
                )
            }
            if (!state.isOccurrence) {
                SectionLabel("Photos and files", MaterialTheme.colorScheme.onSurfaceVariant)
                PhotosField(state, onEvent)
                FilesField(state, onEvent)
            }
            if (state.isOccurrence) {
                HorizontalDivider()
                SectionLabel("This repeating ${state.kind.name.lowercase()}", MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Editing ${state.date.withWeek()}. When you save, you'll choose whether the change is for " +
                        "this day only or for this day and all future days. Past days keep their history.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEvent(EditorEvent.SkipToday) }) { Text("Skip this day") }
                    TextButton(onClick = { onEvent(EditorEvent.EndSeries) }) { Text("End after this day") }
                }
            }
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(
                        if (state.isOccurrence) "Delete series and its history" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (pickDate) {
        PlannerDatePicker(state.date, { onEvent(EditorEvent.SetDate(it)) }, { pickDate = false })
    }
    if (pickTime) {
        PlannerTimePicker(state.time ?: LocalTime.of(9, 0), { onEvent(EditorEvent.SetTime(it)) }, { pickTime = false })
    }
    if (repeatMenu) {
        RepeatMenu(
            date = state.date,
            current = state.schedule,
            onPick = {
                onEvent(EditorEvent.SetRepeat(it))
                repeatMenu = false
            },
            onCustom = {
                repeatMenu = false
                customRepeat = true
            },
            onDismiss = { repeatMenu = false },
        )
    }
    if (customRepeat) {
        CustomRepeatDialog(
            start = state.date,
            initial = state.schedule,
            onDone = {
                onEvent(EditorEvent.SetRepeat(it))
                customRepeat = false
            },
            onDismiss = { customRepeat = false },
        )
    }
    if (state.askScope) {
        AlertDialog(
            onDismissRequest = { onEvent(EditorEvent.DismissScope) },
            title = { Text("Save changes to a repeating task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScopeOption("Only ${state.date.withWeek()}", "Other days stay as they are.") {
                        onEvent(EditorEvent.ConfirmScope(EditScope.ONLY_TODAY))
                    }
                    ScopeOption("This day and all future days", "Updates the series. Past days keep their history.") {
                        onEvent(EditorEvent.ConfirmScope(EditScope.TODAY_AND_FUTURE))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onEvent(EditorEvent.DismissScope) }) { Text("Cancel") } },
        )
    }
    if (editNames) {
        ImportanceNamesDialog(onSave = {
            onEvent(EditorEvent.RenameImportance(it))
            editNames = false
        }, onDismiss = { editNames = false })
    }
    if (addPerson) {
        NameDialog("New person", onDone = {
            addPerson = false
            onEvent(EditorEvent.AddPerson(it))
        }, onDismiss = { addPerson = false })
    }
    if (addCollection) {
        NameDialog("New collection", onDone = {
            addCollection = false
            onEvent(EditorEvent.AddCollection(it))
        }, onDismiss = { addCollection = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (state.isOccurrence) "Delete this repeating task?" else "Delete this entry?") },
            text = {
                Text(
                    if (state.isOccurrence) {
                        "All its days, including past history, will be removed. To stop it but keep history, use \"End after this day\" instead."
                    } else {
                        "This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onEvent(EditorEvent.Delete)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) PlannerColors.Accent else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ScopeOption(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Board for a task: a dropdown of boards, or none. */
@Composable
private fun BoardField(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val boards = state.library?.collections.orEmpty().filter { it.type == CollectionType.BOARD }
    val current = boards.firstOrNull { it.id == state.boardId }
    Box {
        EditorChip(Icons.Outlined.ViewKanban, current?.name ?: "Board", set = current != null) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { open = false; onEvent(EditorEvent.SetBoard(null)) })
            boards.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(b.color))) },
                    onClick = { open = false; onEvent(EditorEvent.SetBoard(b.id)) },
                )
            }
        }
    }
}

/** Toggleable chips with an add button (people, collections). */
@Composable
private fun ChipPicker(
    items: List<Pair<String, String>>,
    selected: Set<String>,
    addLabel: String,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (id, name) -> Pill(name, id in selected, { onToggle(id) }) }
        TextButton(onClick = onAdd) { Text(addLabel, color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
    }
}

/** Photo thumbnails with remove buttons, and "+ Photo" using the system photo picker. */
@Composable
private fun PhotosField(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) onEvent(EditorEvent.AddPhotos(uris))
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.photos.forEach { (id, file) -> Thumb(file, onRemove = { onEvent(EditorEvent.RemovePhoto(id)) }) }
        state.pendingPhotos.forEach { uri -> Thumb(uri, onRemove = { onEvent(EditorEvent.RemovePendingPhoto(uri)) }) }
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Photo", color = PlannerColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Thumb(model: Any, onRemove: () -> Unit) {
    Box(Modifier.size(72.dp)) {
        AsyncImage(
            model = model,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xCC1B1D1B))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Color.White, fontSize = 11.sp) }
    }
}

/** Reminders: several allowed, e.g. "10 min before" and "At the time". */
@Composable
private fun RemindersField(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val summary = when (state.reminders.size) {
        0 -> "Reminder"
        1 -> ReminderPlanner.label(state.reminders.first())
        else -> "${state.reminders.size} reminders"
    }
    Box {
        EditorChip(Icons.Outlined.Notifications, summary, set = state.reminders.isNotEmpty()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReminderPlanner.CHOICES.forEach { m ->
                DropdownMenuItem(
                    text = { Text(ReminderPlanner.label(m)) },
                    leadingIcon = {
                        Checkbox(checked = m in state.reminders, onCheckedChange = { onEvent(EditorEvent.ToggleReminder(m)) })
                    },
                    onClick = { onEvent(EditorEvent.ToggleReminder(m)) },
                )
            }
        }
    }
}

/** A task's checklist: tick items, add, remove, or turn the description's lines into items. */
@Composable
private fun ChecklistField(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    var newItem by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                if (state.checklist.isEmpty()) "Checklist" else "Checklist · ${state.checklist.count { it.done }} of ${state.checklist.size}",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (state.checklist.isEmpty() && state.description.lines().count { it.isNotBlank() } >= 2) {
                TextButton(onClick = { onEvent(EditorEvent.ChecklistFromDescription) }) {
                    Text("Make description a checklist", fontSize = 12.sp, color = PlannerColors.Accent)
                }
            }
        }
        state.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.done, onCheckedChange = { onEvent(EditorEvent.ToggleChecklistItem(item.id)) })
                Text(
                    item.text,
                    Modifier.weight(1f),
                    textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { onEvent(EditorEvent.RemoveChecklistItem(item.id)) }) {
                    Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add an item") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    onEvent(EditorEvent.AddChecklistItem(newItem))
                    newItem = ""
                }),
            )
            TextButton(onClick = {
                onEvent(EditorEvent.AddChecklistItem(newItem))
                newItem = ""
            }) { Text("Add", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
        }
    }
}

/** PDFs and other files: open, remove, or add with the system file picker. */
@Composable
private fun FilesField(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onEvent(EditorEvent.AddFiles(uris))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.files.forEach { (id, file, mime) ->
            FileChip(mime.substringAfter(';', "").ifBlank { "PDF" }, onOpen = { com.ivy.planner.ui.openAttachment(context, file, mime) }) {
                onEvent(EditorEvent.RemoveFile(id))
            }
        }
        state.pendingFiles.forEach { uri ->
            FileChip("New file", onOpen = {}) { onEvent(EditorEvent.RemovePendingFile(uri)) }
        }
        TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
            Text("+ PDF", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FileChip(name: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PlannerColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
        IconButton(onClick = onRemove) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A compact setting: icon and value in a chip; "set" values are highlighted. [onClear] adds a small ✕. */
@Composable
private fun EditorChip(
    icon: ImageVector,
    label: String,
    set: Boolean,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 10.dp, end = if (onClear != null) 2.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (set) PlannerColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (set) FontWeight.Bold else FontWeight.Medium,
            color = if (set) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        if (onClear != null) {
            Text(
                "✕",
                modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 8.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
