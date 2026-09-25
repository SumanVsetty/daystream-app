package com.ivy.planner.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.ivy.navigation.PlannerRoutineEditScreen
import com.ivy.navigation.PlannerRoutineScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.data.RoutineRepository
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RoutineProgress
import com.ivy.planner.domain.RoutineStep
import com.ivy.planner.domain.Routines
import com.ivy.planner.domain.Series
import com.ivy.planner.domain.StepState
import com.ivy.planner.domain.TimerType
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val OnRoutine = Color(0xFF241A3D)

@HiltViewModel
class RoutineViewModel @Inject constructor(
    val planner: PlannerRepository,
    val routines: RoutineRepository,
) : ViewModel()

private enum class Mode { OVERVIEW, PLAYER, DONE }

@Composable
fun PlannerRoutineScreenImpl(screen: PlannerRoutineScreen) {
    val vm: RoutineViewModel = screenScopedViewModel()
    PlannerTheme { RoutineUi(vm, screen.seriesId, LocalDate.ofEpochDay(screen.epochDay)) }
}

@Composable
private fun RoutineUi(vm: RoutineViewModel, seriesId: String, date: LocalDate) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val series by remember(seriesId) { vm.planner.observeSeries().map { list -> list.firstOrNull { it.id == seriesId } } }
        .collectAsState(initial = null)
    val allSteps by remember { vm.routines.observeSteps() }.collectAsState(initial = emptyMap())
    val allStates by remember(date) { vm.routines.observeStates(date) }.collectAsState(initial = emptyMap())
    val steps = allSteps[seriesId].orEmpty()
    val progress = RoutineProgress(steps, allStates["$seriesId@$date"].orEmpty())
    var mode by remember { mutableStateOf(Mode.OVERVIEW) }
    var index by remember { mutableIntStateOf(0) }

    val s = series
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s == null) return@Box
            when (mode) {
                Mode.OVERVIEW -> Overview(
                    series = s,
                    date = date,
                    progress = progress,
                    onBack = { nav.back() },
                    onEdit = { nav.navigateTo(PlannerRoutineEditScreen(seriesId = s.id, epochDay = date.toEpochDay())) },
                    onStart = {
                        index = progress.nextIndex ?: 0
                        mode = if (progress.isComplete) Mode.DONE else Mode.PLAYER
                    },
                    onRestart = { scope.launch { vm.routines.restart(s.id, date) } },
                    onOpenStep = {
                        index = it
                        mode = Mode.PLAYER
                    },
                )
                Mode.PLAYER -> {
                    val step = steps.getOrNull(index)
                    if (step == null) {
                        mode = Mode.OVERVIEW
                    } else {
                        Player(
                            title = s.title,
                            steps = steps,
                            index = index,
                            progress = progress,
                            onClose = { mode = Mode.OVERVIEW },
                            onPrevious = { if (index > 0) index-- },
                            onMark = { state ->
                                scope.launch {
                                    vm.routines.setStep(s.id, date, step.id, state)
                                    // move on to the next step not handled yet, or finish
                                    val states = progress.states + (step.id to state)
                                    val next = steps.indices.firstOrNull { it > index && steps[it].id !in states }
                                        ?: steps.indices.firstOrNull { steps[it].id !in states }
                                    if (next == null) mode = Mode.DONE else index = next
                                }
                            },
                        )
                    }
                }
                Mode.DONE -> Done(
                    series = s,
                    progress = progress,
                    onFinish = { note ->
                        scope.launch {
                            vm.routines.finish(s.id, s.title, date, note)
                            nav.back()
                        }
                    },
                    onBackToSteps = { mode = Mode.OVERVIEW },
                )
            }
        }
    }
}

@Composable
private fun Overview(
    series: Series,
    date: LocalDate,
    progress: RoutineProgress,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onOpenStep: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit routine") }
            }
            Text(series.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Text(
                listOfNotNull(
                    RepeatCodec.describe(series.schedule),
                    series.time?.label(),
                    series.durationMinutes?.let { "about $it min" },
                ).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (series.description.isNotBlank()) {
                Text(series.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${progress.done} / ${progress.total}", "steps ${if (date == LocalDate.now()) "today" else date.withWeek()}", Modifier.weight(1f))
            }
            SectionLabel("Steps")
            progress.steps.forEachIndexed { i, step ->
                val state = progress.states[step.id]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStep(i) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    StepState.DONE -> PlannerColors.Done
                                    StepState.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
                                    null -> if (i == progress.nextIndex) PlannerColors.Routine else MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state == StepState.DONE) {
                            Icon(Icons.Filled.Check, contentDescription = "Done", tint = PlannerColors.OnDone, modifier = Modifier.size(16.dp))
                        } else {
                            Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (i == progress.nextIndex && state == null) OnRoutine else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.heading,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (state == StepState.DONE) TextDecoration.LineThrough else null,
                        )
                        val facts = step.facts + Routines.timerFacts(step)
                        if (facts.isNotEmpty() || state == StepState.SKIPPED) {
                            Text(
                                (facts + listOfNotNull(if (state == StepState.SKIPPED) "skipped" else null)).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (progress.steps.isEmpty()) {
                Text("No steps yet. Tap ✎ to add some.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress.handled > 0) {
                TextButton(onClick = onRestart) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start this day over")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (progress.steps.isNotEmpty()) {
            BigButton(
                text = when {
                    progress.isComplete -> "Finish · how did it go?"
                    progress.handled == 0 -> "Start"
                    else -> "Continue · step ${(progress.nextIndex ?: 0) + 1} of ${progress.total}"
                },
                color = PlannerColors.Routine,
                onColor = OnRoutine,
                modifier = Modifier.padding(20.dp),
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun Player(
    title: String,
    steps: List<RoutineStep>,
    index: Int,
    progress: RoutineProgress,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onMark: (StepState) -> Unit,
) {
    val step = steps[index]
    val uri = LocalUriHandler.current
    val next = steps.getOrNull(index + 1)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onClose) { Icon(Icons.Filled.List, "All steps") }
        }
        // one segment per step
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { i, s ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                progress.states[s.id] == StepState.DONE -> PlannerColors.Done
                                i == index -> PlannerColors.Routine
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("Step ${index + 1} of ${steps.size}", PlannerColors.Routine)
            Text(step.heading, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            val facts = step.facts + Routines.timerFacts(step)
            if (facts.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    facts.forEach { f ->
                        Text(
                            f,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            if (step.description.isNotBlank()) {
                Text(step.description, fontSize = 16.sp, lineHeight = 24.sp)
            }
            if (!step.highlightText.isNullOrBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionLabel(step.highlightTitle ?: "Note", PlannerColors.Routine)
                    Text(step.highlightText.orEmpty(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                }
            }
            step.link?.let { link ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { runCatching { uri.openUri(link) } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text("Open link", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("›", color = PlannerColors.Routine, fontWeight = FontWeight.Bold)
                }
            }
            when (step.timer) {
                TimerType.COUNTDOWN -> StepTimer(key = step.id, seconds = step.timerSeconds ?: 60, label = "Timer")
                TimerType.SETS -> SetsTimer(key = step.id, sets = step.sets ?: 3, restSeconds = step.timerSeconds ?: 60)
                TimerType.NONE -> {}
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(
                text = if (next != null) "Done · next: ${next.heading}" else "Done",
                color = PlannerColors.Done,
                onColor = PlannerColors.OnDone,
                onClick = { onMark(StepState.DONE) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("‹ Previous", Modifier.weight(1f), enabled = index > 0, onClick = onPrevious)
                SmallButton("Skip step", Modifier.weight(1f), onClick = { onMark(StepState.SKIPPED) })
            }
        }
    }
}

/** A countdown with pause and restart. */
@Composable
private fun StepTimer(key: String, seconds: Int, label: String) {
    var left by remember(key) { mutableIntStateOf(seconds) }
    var running by remember(key) { mutableStateOf(false) }
    LaunchedEffect(running, left) {
        if (running && left > 0) {
            delay(1_000)
            left -= 1
            if (left == 0) running = false
        }
    }
    TimerCard(label, Routines.clock(left), running, finished = left == 0, onToggle = {
        if (left == 0) left = seconds
        running = !running
    })
}

/** Sets with rest: tick off a set, then the rest countdown runs. */
@Composable
private fun SetsTimer(key: String, sets: Int, restSeconds: Int) {
    var set by remember(key) { mutableIntStateOf(1) }
    var resting by remember(key) { mutableStateOf(false) }
    var left by remember(key) { mutableIntStateOf(restSeconds) }
    LaunchedEffect(resting, left) {
        if (resting && left > 0) {
            delay(1_000)
            left -= 1
            if (left == 0) {
                resting = false
                if (set < sets) set += 1
            }
        }
    }
    TimerCard(
        label = if (resting) "Rest · then set ${minOf(set + 1, sets)} of $sets" else "Set $set of $sets",
        value = if (resting) Routines.clock(left) else "Go",
        running = resting,
        finished = !resting && set == sets && left == 0,
        onToggle = {
            if (!resting && set <= sets) {
                if (set == sets) {
                    left = 0
                } else {
                    left = restSeconds
                    resting = true
                }
            } else {
                resting = false
                if (set < sets) set += 1
            }
        },
        actionLabel = if (resting) "Skip rest" else if (set == sets) "Last set done" else "Set done · rest",
    )
}

@Composable
private fun TimerCard(label: String, value: String, running: Boolean, finished: Boolean, onToggle: () -> Unit, actionLabel: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            Text(if (finished) "Done" else value, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (actionLabel != null) {
            TextButton(onClick = onToggle) { Text(actionLabel, color = PlannerColors.Routine, fontWeight = FontWeight.Bold) }
        } else {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (running) "Pause" else "Start", tint = PlannerColors.Routine)
            }
        }
    }
}

@Composable
private fun Done(
    series: Series,
    progress: RoutineProgress,
    onFinish: (String?) -> Unit,
    onBackToSteps: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(PlannerColors.Done), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = PlannerColors.OnDone, modifier = Modifier.size(32.dp))
            }
            Text("Routine complete", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "${series.title} · ${progress.done} of ${progress.total} steps" + if (progress.skipped > 0) " · ${progress.skipped} skipped" else "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progress.steps.forEach { s ->
                val state = progress.states[s.id]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        if (state == StepState.DONE) "✓" else "–",
                        modifier = Modifier.width(24.dp),
                        color = if (state == StepState.DONE) PlannerColors.Done else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.heading, fontSize = 15.sp, color = if (state == StepState.DONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel("How did it go? · optional note")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("It lands on your Day log as a note") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            TextButton(onClick = onBackToSteps) { Text("Back to the steps") }
        }
        BigButton("Finish", PlannerColors.Routine, OnRoutine, Modifier.padding(20.dp)) { onFinish(note.ifBlank { null }) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigButton(text: String, color: Color, onColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = onColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun SmallButton(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
