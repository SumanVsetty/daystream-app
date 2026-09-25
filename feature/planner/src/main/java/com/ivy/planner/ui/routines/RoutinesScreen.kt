package com.ivy.planner.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.PlannerRoutineEditScreen
import com.ivy.navigation.PlannerRoutineScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RoutineProgress
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RoutineRing
import com.ivy.planner.ui.label
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** All routines: today's progress for each, and "+ New routine". */
@Composable
fun PlannerRoutinesScreenImpl() {
    val vm: RoutineViewModel = screenScopedViewModel()
    PlannerTheme { RoutinesUi(vm) }
}

@Composable
private fun RoutinesUi(vm: RoutineViewModel) {
    val nav = navigation()
    val today = LocalDate.now()
    val routines by remember { vm.planner.observeSeries().map { list -> list.filter { it.isRoutine } } }.collectAsState(initial = emptyList())
    val steps by remember { vm.routines.observeSteps() }.collectAsState(initial = emptyMap())
    val states by remember { vm.routines.observeStates(today) }.collectAsState(initial = emptyMap())

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Text("Routines", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigateTo(PlannerRoutineEditScreen(epochDay = today.toEpochDay())) }) {
                        Text("+ New", color = PlannerColors.Routine, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (routines.isEmpty()) {
                item {
                    Text(
                        "No routines yet. Create one for your mornings, nights, workouts or the start of work.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(routines, key = { it.id }) { r ->
                val list = steps[r.id].orEmpty()
                val progress = RoutineProgress(list, states["${r.id}@$today"].orEmpty())
                val occursToday = r.schedule.occursOn(today)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            if (occursToday) nav.navigateTo(PlannerRoutineScreen(r.id, today.toEpochDay()))
                            else nav.navigateTo(PlannerRoutineEditScreen(seriesId = r.id))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoutineRing(progress.done, maxOf(progress.total, 1), size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                RepeatCodec.describe(r.schedule),
                                r.time?.label(),
                                "${list.size} steps",
                                if (occursToday) progress.label() + " today" else null,
                            ).joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { nav.navigateTo(PlannerRoutineEditScreen(seriesId = r.id)) }) {
                        Text("Edit", color = PlannerColors.Routine)
                    }
                }
            }
        }
    }
}
