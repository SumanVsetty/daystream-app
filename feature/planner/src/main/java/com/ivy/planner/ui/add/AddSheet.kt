package com.ivy.planner.ui.add

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.ImportanceLabels
import com.ivy.planner.domain.ReminderPlanner
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.PlannerTimePicker
import com.ivy.planner.ui.edit.CustomRepeatDialog
import com.ivy.planner.ui.edit.RepeatMenu
import com.ivy.planner.ui.importanceColor
import com.ivy.planner.ui.journal.NameDialog
import com.ivy.planner.ui.label
import com.ivy.planner.ui.shortDay
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Place once per screen host: shows the add sheet whenever [PlannerAddSheet.request] is set. */
@Composable
fun PlannerAddSheetHost() {
    val request = PlannerAddSheet.request ?: return
    val vm: AddSheetViewModel = screenScopedViewModel()
    LaunchedEffect(request) { vm.reset(request) }
    PlannerTheme { AddSheet(vm) { PlannerAddSheet.close() } }
}

private val kinds = listOf(
    EntryKind.TASK to "Task",
    EntryKind.EVENT to "Event",
    EntryKind.NOTE to "Note",
    EntryKind.JOURNAL to "Journal",
)

private fun kindColor(k: EntryKind) = when (k) {
    EntryKind.TASK -> PlannerColors.Done
    EntryKind.EVENT -> PlannerColors.Event
    EntryKind.NOTE -> Color(0xFFE4E6E1)
    EntryKind.JOURNAL -> PlannerColors.Journal
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(vm: AddSheetViewModel, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    val kind = vm.effectiveKind
    val accent = kindColor(kind)
    val today = LocalDate.now()

    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) vm.photos = vm.photos + uris
    }
    val send = { vm.save(onClose) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // type
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.forEach { (k, name) ->
                    val on = k == kind
                    Text(
                        name,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) kindColor(k) else Color.Transparent)
                            .border(1.dp, if (on) kindColor(k) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { vm.kind = k }
                            .padding(vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (on) Color(0xFF1B1D1B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            // title + send
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (vm.text.isEmpty()) {
                        Text(
                            when (kind) {
                                EntryKind.JOURNAL -> "What happened?"
                                EntryKind.NOTE -> "Write a note"
                                EntryKind.EVENT -> "Event, e.g. Lunch with Raj friday 1pm"
                                EntryKind.TASK -> "Task, e.g. Call Sunil tomorrow 7pm #quotes"
                            },
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // cursor at the end, including when opened with text from the rapid-log bar
                    var field by remember { mutableStateOf(TextFieldValue(vm.text, TextRange(vm.text.length))) }
                    LaunchedEffect(vm.text) {
                        if (vm.text != field.text) field = TextFieldValue(vm.text, TextRange(vm.text.length))
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            vm.text = it.text
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        textStyle = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(PlannerColors.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (vm.parsed.title.isNotBlank()) accent else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = vm.parsed.title.isNotBlank()) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Save", tint = Color(0xFF1B1D1B))
                }
            }
            if (vm.showDetails) {
                OutlinedTextField(
                    value = vm.description,
                    onValueChange = { vm.description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Details") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            } else {
                Text("+ Details", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { vm.showDetails = true })
            }

            // journal: photos, importance, people, collections
            if (kind == EntryKind.JOURNAL) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.photos.forEach { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.photos = vm.photos - uri },
                        )
                    }
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", fontSize = 22.sp, color = PlannerColors.Accent) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (1..4).forEach { l ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(importanceColor(l))
                                .then(if (vm.importance == l) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { vm.importance = if (vm.importance == l) 0 else l },
                        )
                    }
                    Text(
                        ImportanceLabels.label(vm.importance) ?: "Importance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.importance > 0) importanceColor(vm.importance) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // chips
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(vm.effectiveDate.let { if (it == today) "Today" else if (it == today.plusDays(1)) "Tomorrow" else it.shortDay() }, set = vm.date != null || vm.parsed.date != null) { pickDate = true }
                Chip(
                    vm.effectiveTime?.label() ?: when (kind) {
                        EntryKind.TASK -> "Auto time"
                        EntryKind.EVENT -> "All day"
                        else -> if (vm.effectiveDate == today) "Now" else "No time"
                    },
                    set = vm.effectiveTime != null,
                    color = PlannerColors.Accent,
                ) { pickTime = true }
                if (kind == EntryKind.TASK || kind == EntryKind.EVENT) {
                    Box {
                        Chip("${vm.effectiveDuration} min", set = vm.duration != null || vm.parsed.durationMinutes != null) { durationMenu = true }
                        DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                                DropdownMenuItem(text = { Text("$m min") }, onClick = { vm.duration = m; durationMenu = false })
                            }
                        }
                    }
                    Box {
                        val rem = vm.effectiveReminders
                        Chip(if (rem.isEmpty()) "No reminder" else "Remind " + ReminderPlanner.label(rem.max()).lowercase(), set = rem.isNotEmpty()) { reminderMenu = true }
                        DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                            ReminderPlanner.CHOICES.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(ReminderPlanner.label(m)) },
                                    leadingIcon = { Checkbox(checked = m in rem, onCheckedChange = null) },
                                    onClick = { vm.reminders = if (m in rem) rem - m else (rem + m).sorted() },
                                )
                            }
                        }
                    }
                    Chip(vm.schedule?.let { RepeatCodec.describe(it) } ?: "Repeat", set = vm.schedule != null) { repeatMenu = true }
                }
                if (kind == EntryKind.TASK) {
                    val boards = lib?.collections.orEmpty().filter { it.type == CollectionType.BOARD }
                    Box {
                        Chip(boards.firstOrNull { it.id == vm.boardId }?.let { "● " + it.name } ?: "Board", set = vm.boardId != null) { boardMenu = true }
                        DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { vm.boardId = null; boardMenu = false })
                            boards.forEach { b -> DropdownMenuItem(text = { Text(b.name) }, onClick = { vm.boardId = b.id; boardMenu = false }) }
                        }
                    }
                }
                if (kind != EntryKind.JOURNAL) {
                    Chip(if (vm.photos.isEmpty()) "Photo" else "${vm.photos.size} photo", set = vm.photos.isNotEmpty()) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
            if (kind == EntryKind.JOURNAL) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lib?.people.orEmpty().forEach { p ->
                        Chip(p.name, set = p.id in vm.peopleIds, color = PlannerColors.Event) {
                            vm.peopleIds = if (p.id in vm.peopleIds) vm.peopleIds - p.id else vm.peopleIds + p.id
                        }
                    }
                    Chip("+ Person", set = false) { addPerson = true }
                    lib?.collections.orEmpty().filter { it.type == CollectionType.TOPIC }.forEach { c ->
                        Chip(c.name, set = c.id in vm.collectionIds, color = Color(c.color)) {
                            vm.collectionIds = if (c.id in vm.collectionIds) vm.collectionIds - c.id else vm.collectionIds + c.id
                        }
                    }
                    Chip("+ Collection", set = false) { addCollection = true }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (pickDate) PlannerDatePicker(vm.effectiveDate, { vm.date = it }, { pickDate = false })
    if (pickTime) PlannerTimePicker(vm.effectiveTime ?: LocalTime.of(9, 0), { vm.time = it }, { pickTime = false })
    if (repeatMenu) {
        RepeatMenu(
            date = vm.effectiveDate,
            current = vm.schedule,
            onPick = {
                vm.schedule = it
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
        CustomRepeatDialog(vm.effectiveDate, vm.schedule, { vm.schedule = it; customRepeat = false }, { customRepeat = false })
    }
    if (addPerson) {
        NameDialog("New person", onDone = { name ->
            addPerson = false
            scope.launch { vm.peopleIds = vm.peopleIds + vm.library.savePerson(name) }
        }, onDismiss = { addPerson = false })
    }
    if (addCollection) {
        NameDialog("New collection", onDone = { name ->
            addCollection = false
            scope.launch { vm.collectionIds = vm.collectionIds + vm.library.saveCollection(name, CollectionType.TOPIC) }
        }, onDismiss = { addCollection = false })
    }
}

/** A tappable chip; "set" ones are filled so defaults and choices are easy to tell apart. */
@Composable
private fun Chip(label: String, set: Boolean, color: Color? = null, onClick: () -> Unit) {
    val filled = set && color != null
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color!! else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            filled -> Color(0xFF1B1D1B)
            set -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
    )
}
