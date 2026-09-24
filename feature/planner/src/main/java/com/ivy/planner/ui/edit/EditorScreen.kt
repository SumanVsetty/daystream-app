package com.ivy.planner.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.ui.KindChip
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTimePicker
import com.ivy.planner.ui.SectionLabel
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
    EditorUi(viewModel.uiState(), viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorUi(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    val nav = navigation()
    LaunchedEffect(state.closed) { if (state.closed) nav.back() }

    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                    listOf(EntryKind.TASK, EntryKind.EVENT, EntryKind.NOTE).forEach { k ->
                        KindChip(k, selected = state.kind == k) { onEvent(EditorEvent.SetKind(k)) }
                    }
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = { onEvent(EditorEvent.SetTitle(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { onEvent(EditorEvent.SetDescription(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description (optional)") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
            )

            FieldRow(Icons.Outlined.CalendarToday, "Date", state.date.withWeek(), enabled = !state.isOccurrence) {
                pickDate = true
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FieldRow(Icons.Outlined.Schedule, "Time", state.time?.label() ?: "No time") { pickTime = true }
                }
                if (state.time != null) {
                    TextButton(onClick = { onEvent(EditorEvent.SetTime(null)) }) { Text("Clear") }
                }
            }
            if (state.kind == EntryKind.TASK || state.kind == EntryKind.EVENT) {
                FieldRow(Icons.Outlined.Repeat, "Repeat", state.repeatLabel, highlight = state.schedule != null) {
                    repeatMenu = true
                }
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
