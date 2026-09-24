package com.ivy.planner.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatEnd
import com.ivy.planner.domain.RepeatRule
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.withWeek
import java.time.DayOfWeek
import java.time.LocalDate

/** The Repeat menu: Never, quick choices adapted to [date], and Custom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatMenu(
    date: LocalDate,
    current: RepeatSchedule?,
    onPick: (RepeatSchedule?) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            Text("Repeat", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(12.dp))
            MenuRow("Never", "One-off", selected = current == null) { onPick(null) }
            RepeatCodec.quickChoices(date).forEach { rule ->
                MenuRow(RepeatCodec.describe(rule), null, selected = current != null && current.rule == rule && current.end == RepeatEnd.Never) {
                    onPick(RepeatSchedule(rule, date))
                }
            }
            MenuRow("Custom…", "Interval, days, after completion, start and end", selected = false, onClick = onCustom)
        }
    }
}

@Composable
private fun MenuRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
            sub?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PlannerColors.Accent)
    }
}

private enum class RepeatUnit { DAYS, WEEKS, MONTHS, AFTER_COMPLETION }
private enum class MonthMode { DAY, WEEKDAY, LAST }
private enum class EndMode { NEVER, DATE, COUNT }

/** Custom repeat: every N days/weeks/months or N days after completion, with start, end and a preview. */
@Composable
fun CustomRepeatDialog(
    start: LocalDate,
    initial: RepeatSchedule?,
    onDone: (RepeatSchedule) -> Unit,
    onDismiss: () -> Unit,
) {
    val rule = initial?.rule
    var intervalText by remember { mutableStateOf((rule?.interval ?: 1).toString()) }
    var unit by remember {
        mutableStateOf(
            when (rule) {
                is RepeatRule.Weekly -> RepeatUnit.WEEKS
                is RepeatRule.MonthlyOnDay, is RepeatRule.MonthlyOnWeekday, is RepeatRule.MonthlyLastDay -> RepeatUnit.MONTHS
                is RepeatRule.AfterCompletion -> RepeatUnit.AFTER_COMPLETION
                else -> RepeatUnit.DAYS
            },
        )
    }
    var days by remember { mutableStateOf((rule as? RepeatRule.Weekly)?.days ?: setOf(start.dayOfWeek)) }
    var monthMode by remember {
        mutableStateOf(
            when (rule) {
                is RepeatRule.MonthlyOnWeekday -> MonthMode.WEEKDAY
                is RepeatRule.MonthlyLastDay -> MonthMode.LAST
                else -> MonthMode.DAY
            },
        )
    }
    var endMode by remember {
        mutableStateOf(
            when (initial?.end) {
                is RepeatEnd.OnDate -> EndMode.DATE
                is RepeatEnd.AfterCount -> EndMode.COUNT
                else -> EndMode.NEVER
            },
        )
    }
    var endDate by remember { mutableStateOf((initial?.end as? RepeatEnd.OnDate)?.lastDate ?: start.plusMonths(3)) }
    var countText by remember { mutableStateOf(((initial?.end as? RepeatEnd.AfterCount)?.count ?: 10).toString()) }
    var pickingEnd by remember { mutableStateOf(false) }

    val interval = intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1
    val nth = (start.dayOfMonth - 1) / 7 + 1
    val built: RepeatSchedule? = runCatching {
        val r: RepeatRule = when (unit) {
            RepeatUnit.DAYS -> RepeatRule.Daily(interval)
            RepeatUnit.WEEKS -> RepeatRule.Weekly(interval, days)
            RepeatUnit.MONTHS -> when (monthMode) {
                MonthMode.DAY -> RepeatRule.MonthlyOnDay(interval, start.dayOfMonth)
                MonthMode.WEEKDAY -> RepeatRule.MonthlyOnWeekday(
                    interval,
                    if (nth <= 4) nth else RepeatRule.MonthlyOnWeekday.LAST,
                    start.dayOfWeek,
                )
                MonthMode.LAST -> RepeatRule.MonthlyLastDay(interval)
            }
            RepeatUnit.AFTER_COMPLETION -> RepeatRule.AfterCompletion(interval)
        }
        val end = when (endMode) {
            EndMode.NEVER -> RepeatEnd.Never
            EndMode.DATE -> RepeatEnd.OnDate(endDate)
            EndMode.COUNT -> RepeatEnd.AfterCount(countText.toIntOrNull()?.coerceAtLeast(1) ?: 1)
        }
        RepeatSchedule(r, start, end)
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { built?.let(onDone) }, enabled = built != null) {
                Text("Done", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Custom repeat") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Every")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { v -> intervalText = v.filter { it.isDigit() }.take(3) },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Days", unit == RepeatUnit.DAYS, { unit = RepeatUnit.DAYS })
                    Pill("Weeks", unit == RepeatUnit.WEEKS, { unit = RepeatUnit.WEEKS })
                    Pill("Months", unit == RepeatUnit.MONTHS, { unit = RepeatUnit.MONTHS })
                }
                Pill("Days after completion", unit == RepeatUnit.AFTER_COMPLETION, { unit = RepeatUnit.AFTER_COMPLETION })

                if (unit == RepeatUnit.WEEKS) {
                    SectionLabel("On these days")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { d ->
                            Pill(
                                RepeatCodec.dayName(d).take(2),
                                d in days,
                                { days = if (d in days && days.size > 1) days - d else days + d },
                            )
                        }
                    }
                }
                if (unit == RepeatUnit.MONTHS) {
                    SectionLabel("On")
                    RadioLine("Day ${start.dayOfMonth} of the month", monthMode == MonthMode.DAY) { monthMode = MonthMode.DAY }
                    val nthLabel = if (nth <= 4) listOf("first", "second", "third", "fourth")[nth - 1] else "last"
                    RadioLine("The $nthLabel ${RepeatCodec.dayName(start.dayOfWeek)}", monthMode == MonthMode.WEEKDAY) {
                        monthMode = MonthMode.WEEKDAY
                    }
                    RadioLine("The last day of the month", monthMode == MonthMode.LAST) { monthMode = MonthMode.LAST }
                }
                if (unit == RepeatUnit.AFTER_COMPLETION) {
                    Text(
                        "The next one is due $interval day${if (interval == 1) "" else "s"} after you complete it. " +
                            "It's never marked missed: it waits until you do it.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionLabel("Starting")
                Text(start.withWeek(), fontWeight = FontWeight.SemiBold)

                SectionLabel("Ends")
                RadioLine("Never", endMode == EndMode.NEVER) { endMode = EndMode.NEVER }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == EndMode.DATE, onClick = { endMode = EndMode.DATE })
                    Text("On ", Modifier.clickable { endMode = EndMode.DATE })
                    TextButton(onClick = {
                        endMode = EndMode.DATE
                        pickingEnd = true
                    }) { Text(endDate.withWeek()) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == EndMode.COUNT, onClick = { endMode = EndMode.COUNT })
                    Text("After ")
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { v ->
                            countText = v.filter { it.isDigit() }.take(3)
                            endMode = EndMode.COUNT
                        },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text(" times")
                }

                built?.let { s ->
                    Spacer(Modifier.height(4.dp))
                    Text(RepeatCodec.describe(s), fontWeight = FontWeight.Bold)
                    if (s.isCalendarBased) {
                        SectionLabel("Next dates", MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            s.preview(start, 5).joinToString(" · ") { it.withWeek() },
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
    )

    if (pickingEnd) {
        PlannerDatePicker(initial = endDate, onPick = { endDate = it }, onDismiss = { pickingEnd = false })
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
