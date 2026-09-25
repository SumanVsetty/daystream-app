package com.ivy.planner.ui.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.EntryState
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.PlannerTimePicker
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * The Snooze button's sheet: snooze (remind again later without changing the task) or
 * reschedule (move the task). Repeating tasks can be snoozed or skipped for today.
 */
@AndroidEntryPoint
class ReminderSheetActivity : ComponentActivity() {
    @Inject lateinit var repository: PlannerRepository
    @Inject lateinit var prefs: PlannerPrefs
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: ReminderNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = ReminderInfo.from(intent) ?: return finish()
        notifier.cancel(info)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                PlannerTheme { Sheet(info) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet(info: ReminderInfo) {
        val scope = rememberCoroutineScope()
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf<LocalDate?>(null) }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        fun done(block: suspend () -> Unit) {
            scope.launch {
                block()
                scheduler.schedule()
                finish()
            }
        }
        fun snooze(minutes: Long) = done { prefs.snooze(info.key, LocalDateTime.now().plusMinutes(minutes)) }
        fun moveTo(date: LocalDate, time: LocalTime) = done {
            prefs.clearSnooze(info.key)
            repository.reschedule(info.ownerId, date, time)
        }

        ModalBottomSheet(onDismissRequest = { finish() }, sheetState = state) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(info.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SectionLabel("Snooze")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10L to "10 min", 30L to "30 min", 60L to "1 hour", 240L to "4 hours").forEach { (m, label) ->
                        Option(label, modifier = Modifier.weight(1f)) { snooze(m) }
                    }
                }
                if (info.isSeries) {
                    SectionLabel("Today only")
                    Option("Skip today", sub = "Doesn't count as missed") {
                        done {
                            prefs.clearSnooze(info.key)
                            repository.setOccurrenceState(info.ownerId, LocalDate.ofEpochDay(info.epochDay), EntryState.SKIPPED)
                        }
                    }
                } else {
                    SectionLabel("Reschedule")
                    val tonight = LocalTime.of(20, 0)
                    val tomorrow = today.plusDays(1)
                    val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (now.toLocalTime().isBefore(tonight)) {
                            Option("Tonight", sub = tonight.label(), modifier = Modifier.weight(1f)) { moveTo(today, tonight) }
                        }
                        Option("Tomorrow morning", sub = "${tomorrow.withWeek()} · 08:00", modifier = Modifier.weight(1f)) { moveTo(tomorrow, LocalTime.of(8, 0)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Option("Tomorrow afternoon", sub = "14:00", modifier = Modifier.weight(1f)) { moveTo(tomorrow, LocalTime.of(14, 0)) }
                        Option("Next week", sub = "${nextMonday.withWeek()} · 08:00", modifier = Modifier.weight(1f)) { moveTo(nextMonday, LocalTime.of(8, 0)) }
                    }
                    Option("Pick a date and time") { pickDate = true }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        if (pickDate) {
            PlannerDatePicker(
                initial = today.plusDays(1),
                onPick = { pickTime = it },
                onDismiss = { pickDate = false },
            )
        }
        pickTime?.let { date ->
            PlannerTimePicker(
                initial = LocalTime.of(info.minuteOfDay / 60, info.minuteOfDay % 60),
                onPick = { moveTo(date, it) },
                onDismiss = { pickTime = null },
            )
        }
    }

    @Composable
    private fun Option(title: String, modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit) {
        Column(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
