package com.ivy.planner.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.withWeek
import java.time.LocalDate

@Composable
fun PlannerReviewScreenImpl() {
    val viewModel: ReviewViewModel = screenScopedViewModel()
    PlannerTheme { ReviewUi(viewModel.uiState(), viewModel::onEvent) }
}

@Composable
private fun ReviewUi(state: ReviewState, onEvent: (ReviewEvent) -> Unit) {
    val nav = navigation()
    var scheduling by remember { mutableStateOf<Entry?>(null) }
    val total = state.reviewed + state.remaining.size

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row {
                IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.Close, "Close review") }
                Spacer(Modifier.weight(1f))
                if (total > 0) {
                    Text(
                        "${minOf(state.reviewed + 1, total)} of $total",
                        Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionLabel("Weekly review")
            Text("Closing week ${state.week.week}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { state.reviewed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = PlannerColors.Accent,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${state.doneCount}", "done", Modifier.weight(1f))
                Stat("${state.remaining.size}", "still open", Modifier.weight(1f))
                Stat(state.habitPercent?.let { "$it%" } ?: "–", "repeating tasks", Modifier.weight(1f))
            }

            val current = state.remaining.firstOrNull()
            when {
                state.loading -> {}
                current == null -> Done(state) { nav.back() }
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            listOfNotNull(
                                current.date?.let { "Planned for ${it.withWeek()}" } ?: "Week ${state.week.week} task",
                                if (current.migrationCount > 0) "migrated ${current.migrationCount}×" else null,
                            ).joinToString(" · "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("• ${current.title}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        if (current.description.isNotBlank()) {
                            Text(current.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Is this still worth doing?", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Choice("× Done", "It's finished", PlannerColors.Done, PlannerColors.OnDone, Modifier.weight(1f)) {
                                onEvent(ReviewEvent.Done(current.id))
                            }
                            Choice("> Migrate", "Into week ${state.thisWeek.week}", PlannerColors.Accent, PlannerColors.OnAccent, Modifier.weight(1f)) {
                                onEvent(ReviewEvent.Migrate(current.id))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Choice("< Schedule", "Pick a date", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) {
                                scheduling = current
                            }
                            Choice("— Drop", "No longer matters", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) {
                                onEvent(ReviewEvent.Drop(current.id))
                            }
                        }
                    }
                    if (state.remaining.size > 1) {
                        SectionLabel("Up next", MaterialTheme.colorScheme.onSurfaceVariant)
                        state.remaining.drop(1).take(5).forEach {
                            Text("• ${it.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    scheduling?.let { entry ->
        PlannerDatePicker(
            initial = LocalDate.now().plusDays(1),
            onPick = { onEvent(ReviewEvent.Schedule(entry.id, it)) },
            onDismiss = { scheduling = null },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(title: String, sub: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(sub, color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun Done(state: ReviewState, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (state.reviewed > 0) "Week ${state.week.week} is closed." else "Nothing left open in week ${state.week.week}.",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text("Every open task has a decision. Have a good week ${state.thisWeek.week}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Back to today",
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PlannerColors.Accent)
                .clickable(onClick = onClose)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            color = PlannerColors.OnAccent,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
