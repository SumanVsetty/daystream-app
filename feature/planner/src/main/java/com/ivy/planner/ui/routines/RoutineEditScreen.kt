package com.ivy.planner.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.navigation.PlannerRoutineEditScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.data.RoutineRepository
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.ReminderPlanner
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatRule
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.domain.RoutineStep
import com.ivy.planner.domain.Routines
import com.ivy.planner.domain.Series
import com.ivy.planner.domain.TimerType
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.PlannerTimePicker
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.edit.CustomRepeatDialog
import com.ivy.planner.ui.edit.RepeatMenu
import com.ivy.planner.ui.label
import com.ivy.planner.ui.shortDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    val planner: PlannerRepository,
    val routines: RoutineRepository,
) : ViewModel() {
    var loaded by mutableStateOf(false)
    var series by mutableStateOf<Series?>(null)
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var time by mutableStateOf<LocalTime?>(LocalTime.of(6, 30))
    var schedule by mutableStateOf(RepeatSchedule(RepeatRule.Daily(), LocalDate.now()))
    var reminders by mutableStateOf(listOf(0))
    var steps by mutableStateOf(listOf<RoutineStep>())
    var closed by mutableStateOf(false)

    fun load(seriesId: String?, date: LocalDate) {
        if (loaded) return
        schedule = RepeatSchedule(RepeatRule.Daily(), date)
        viewModelScope.launch {
            if (seriesId != null) {
                planner.getSeries(seriesId)?.let { s ->
                    series = s
                    title = s.title
                    description = s.description
                    time = s.time
                    schedule = s.schedule
                }
                steps = routines.steps(seriesId)
                reminders = planner.reminders(seriesId)
            }
            loaded = true
        }
    }

    fun save() {
        if (title.isBlank()) return
        viewModelScope.launch {
            val s = series
            val saved = (s ?: Series(id = planner.newSeriesId(), kind = EntryKind.TASK, title = "", schedule = schedule))
                .copy(
                    title = title.trim(),
                    description = description.trim(),
                    time = time,
                    schedule = schedule,
                    isRoutine = true,
                    durationMinutes = Routines.estimatedMinutes(steps),
                )
            planner.saveSeries(saved)
            routines.saveSteps(saved.id, steps)
            planner.setReminders(saved.id, reminders)
            closed = true
        }
    }

    fun delete() {
        val s = series ?: return
        viewModelScope.launch {
            routines.saveSteps(s.id, emptyList())
            planner.deleteSeries(s.id)
            closed = true
        }
    }
}

@Composable
fun PlannerRoutineEditScreenImpl(screen: PlannerRoutineEditScreen) {
    val vm: RoutineEditViewModel = screenScopedViewModel()
    LaunchedEffect(screen) { vm.load(screen.seriesId, screen.epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    PlannerTheme { RoutineEditUi(vm) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditUi(vm: RoutineEditViewModel) {
    val nav = navigation()
    LaunchedEffect(vm.closed) { if (vm.closed) nav.back() }
    var editing by remember { mutableStateOf<RoutineStep?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text(if (vm.series == null) "New routine" else "Edit routine", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(
                    onClick = { vm.save() },
                    enabled = vm.title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PlannerColors.Routine, contentColor = androidx.compose.ui.graphics.Color(0xFF241A3D)),
                ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
            }
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Routine name, e.g. Night routine") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description (optional)") },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            // when: chips wrap onto the next line instead of squeezing
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(vm.time?.label() ?: "Any time", false, { pickTime = true })
                Pill(RepeatCodec.describe(vm.schedule), false, { repeatMenu = true })
                if (vm.schedule.isOnce) {
                    Pill(vm.schedule.start.shortDay(), false, { pickDate = true })
                }
                Box {
                    Pill(if (vm.reminders.isEmpty()) "No reminder" else ReminderPlanner.label(vm.reminders.max()), false, { reminderMenu = true })
                    DropdownMenu(expanded = reminderMenu, onDismissRequest = { reminderMenu = false }) {
                        ReminderPlanner.CHOICES.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(ReminderPlanner.label(m)) },
                                leadingIcon = { Checkbox(checked = m in vm.reminders, onCheckedChange = null) },
                                onClick = { vm.reminders = if (m in vm.reminders) vm.reminders - m else (vm.reminders + m).sorted() },
                            )
                        }
                    }
                }
            }
            Routines.estimatedMinutes(vm.steps)?.let {
                Text("About $it min · ${vm.steps.size} steps", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionLabel("Steps")
            vm.steps.forEachIndexed { i, step ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { editing = step }
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(PlannerColors.Routine),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = androidx.compose.ui.graphics.Color(0xFF241A3D)) }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(step.heading, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        val facts = step.facts + Routines.timerFacts(step)
                        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.steps = vm.steps.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { vm.steps = vm.steps.move(i, 1) }, enabled = i < vm.steps.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                }
            }
            TextButton(onClick = { editing = RoutineStep(vm.routines.newStepId(), vm.steps.size, "") }) {
                Text("+ Add step", color = PlannerColors.Routine, fontWeight = FontWeight.Bold)
            }
            if (vm.series != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete routine and its history", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    editing?.let { step ->
        StepEditor(
            initial = step,
            isNew = vm.steps.none { it.id == step.id },
            onSave = { saved ->
                vm.steps = if (vm.steps.any { it.id == saved.id }) vm.steps.map { if (it.id == saved.id) saved else it } else vm.steps + saved
                editing = null
            },
            onDelete = {
                vm.steps = vm.steps.filterNot { it.id == step.id }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (pickTime) {
        PlannerTimePicker(vm.time ?: LocalTime.of(6, 30), { vm.time = it }, { pickTime = false })
    }
    if (pickDate) {
        PlannerDatePicker(vm.schedule.start, { vm.schedule = RepeatSchedule.once(it) }, { pickDate = false })
    }
    if (repeatMenu) {
        RepeatMenu(
            date = vm.schedule.start,
            current = if (vm.schedule.isOnce) null else vm.schedule,
            onPick = {
                // "Never" makes a one-off routine: it happens once, on its day
                vm.schedule = it?.copy(start = vm.schedule.start) ?: RepeatSchedule.once(vm.schedule.start)
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
            start = vm.schedule.start,
            initial = vm.schedule,
            onDone = {
                vm.schedule = it
                customRepeat = false
            },
            onDismiss = { customRepeat = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this routine?") },
            text = { Text("Its steps and every day's history will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun <T> List<T>.move(index: Int, by: Int): List<T> {
    val to = index + by
    if (to !in indices) return this
    return toMutableList().apply { add(to, removeAt(index)) }
}

/** Full-screen editor for one step: heading, description, quick facts, highlight box, timer, link. */
@Composable
private fun StepEditor(
    initial: RoutineStep,
    isNew: Boolean,
    onSave: (RoutineStep) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var heading by remember { mutableStateOf(initial.heading) }
    var description by remember { mutableStateOf(initial.description) }
    var facts by remember { mutableStateOf(initial.facts) }
    var newFact by remember { mutableStateOf("") }
    var highlightTitle by remember { mutableStateOf(initial.highlightTitle.orEmpty()) }
    var highlightText by remember { mutableStateOf(initial.highlightText.orEmpty()) }
    var timer by remember { mutableStateOf(initial.timer) }
    var seconds by remember { mutableStateOf(((initial.timerSeconds ?: 60)).toString()) }
    var sets by remember { mutableStateOf((initial.sets ?: 3).toString()) }
    var link by remember { mutableStateOf(initial.link.orEmpty()) }
    val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                    Text(if (isNew) "New step" else "Edit step", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onSave(
                                initial.copy(
                                    heading = heading.trim(),
                                    description = description.trim(),
                                    facts = facts,
                                    highlightTitle = highlightTitle.ifBlank { null },
                                    highlightText = highlightText.ifBlank { null },
                                    timer = timer,
                                    timerSeconds = seconds.toIntOrNull(),
                                    sets = sets.toIntOrNull(),
                                    link = link.ifBlank { null },
                                ),
                            )
                        },
                        enabled = heading.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = PlannerColors.Routine, contentColor = androidx.compose.ui.graphics.Color(0xFF241A3D)),
                    ) { Text("Done", fontWeight = FontWeight.ExtraBold) }
                }
                OutlinedTextField(heading, { heading = it }, Modifier.fillMaxWidth(), label = { Text("Heading") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = caps)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3, shape = RoundedCornerShape(14.dp), keyboardOptions = caps)

                SectionLabel("Quick facts · optional, any label")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    facts.forEach { f -> Pill("$f  ✕", false, { facts = facts - f }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newFact, { newFact = it }, Modifier.weight(1f), placeholder = { Text("e.g. 10 min, At the desk") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = caps)
                    TextButton(onClick = {
                        if (newFact.isNotBlank()) facts = facts + newFact.trim()
                        newFact = ""
                    }) { Text("Add", color = PlannerColors.Routine, fontWeight = FontWeight.Bold) }
                }

                SectionLabel("Highlight box · optional")
                OutlinedTextField(highlightTitle, { highlightTitle = it }, Modifier.fillMaxWidth(), placeholder = { Text("Title, e.g. Precautions, Remember") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = caps)
                OutlinedTextField(highlightText, { highlightText = it }, Modifier.fillMaxWidth(), placeholder = { Text("Text") }, minLines = 2, shape = RoundedCornerShape(14.dp), keyboardOptions = caps)

                SectionLabel("Timer")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("None", timer == TimerType.NONE, { timer = TimerType.NONE })
                    Pill("Countdown", timer == TimerType.COUNTDOWN, { timer = TimerType.COUNTDOWN })
                    Pill("Sets + rest", timer == TimerType.SETS, { timer = TimerType.SETS })
                }
                if (timer != TimerType.NONE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timer == TimerType.SETS) {
                            OutlinedTextField(sets, { v -> sets = v.filter { it.isDigit() }.take(2) }, Modifier.width(96.dp), label = { Text("Sets") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp))
                        }
                        OutlinedTextField(
                            seconds,
                            { v -> seconds = v.filter { it.isDigit() }.take(4) },
                            Modifier.width(150.dp),
                            label = { Text(if (timer == TimerType.SETS) "Rest (seconds)" else "Seconds") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
                SectionLabel("Link · optional")
                OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), placeholder = { Text("https://…") }, singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Delete step", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
