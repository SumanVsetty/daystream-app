package com.ivy.planner.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.planner.data.Library
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.ImportanceLabels
import com.ivy.planner.ui.EntryRow
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.RowTag
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.importanceColor
import com.ivy.planner.ui.label
import com.ivy.planner.ui.timelineColor
import com.ivy.planner.ui.withWeek
import java.io.File

/** Newest first, then by time. */
fun List<Entry>.newestFirst(): List<Entry> =
    sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })

/**
 * A multi-day timeline: a small date heading per day, then the day's entries in the
 * compact layout, joined by the timeline line within each day.
 * [hidePersonId] / [hideCollectionId] leave out the tag of the page you're on.
 */
fun LazyListScope.libraryTimeline(
    entries: List<Entry>,
    lib: Library,
    photoFile: (String) -> File,
    onOpen: (Entry) -> Unit,
    onToggle: (Entry) -> Unit,
    hidePersonId: String? = null,
    hideCollectionId: String? = null,
) {
    val byDay = entries.groupBy { it.date }
    // year headings once the timeline reaches beyond the current year
    val showYears = entries.any { e -> e.date?.let { it.year != java.time.LocalDate.now().year } == true }
    var lastYear: Int? = null
    byDay.forEach { (date, dayEntries) ->
        val year = date?.year
        if (showYears && year != null && year != lastYear) {
            lastYear = year
            item(key = "y:$year") {
                Text(
                    "$year",
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        item(key = "d:${date ?: "none"}") {
            Box(Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp)) {
                SectionLabel(date?.withWeek() ?: "No date", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val ordered = dayEntries.sortedWith(compareBy<Entry>({ it.time != null }, { it.time }))
        itemsIndexed(ordered, key = { _, e -> e.id }) { index, e ->
            val colors = ordered.map { timelineColor(it.kind, it.state, importance = it.importance) }
            val collections = lib.collectionsOf[e.id].orEmpty().filter { it != hideCollectionId }.mapNotNull { lib.collection(it) }
            val people = lib.peopleOf[e.id].orEmpty().filter { it != hidePersonId }.mapNotNull { lib.person(it)?.name }
            Box(Modifier.padding(start = 2.dp, end = 8.dp)) {
                EntryRow(
                    kind = e.kind,
                    title = e.title,
                    description = e.description,
                    meta = people.joinToString(" · "),
                    state = e.state,
                    repeating = false,
                    onToggle = { onToggle(e) },
                    onClick = { onOpen(e) },
                    timeLabel = e.time?.label() ?: "",
                    trailing = when {
                        e.kind == EntryKind.TASK && e.time != null -> "${e.durationMinutes ?: 15} min"
                        e.kind == EntryKind.EVENT && e.time == null -> "All day"
                        else -> ""
                    },
                    lineAbove = if (index > 0) colors[index - 1] else null,
                    lineBelow = if (index < ordered.lastIndex) colors[index] else null,
                    importance = e.importance,
                    tags = collections.map { RowTag(it.name, Color(it.color)) },
                    photos = lib.photosOf[e.id].orEmpty().map(photoFile),
                )
            }
        }
    }
}

/** Four importance chips; selecting any shows only those levels. */
@Composable
fun ImportanceFilter(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Cool", "Unusual", "Important", "Life-changing").forEachIndexed { i, name ->
            val level = i + 1
            val on = level in selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .border(1.5.dp, if (on) importanceColor(level) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onToggle(level) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(importanceColor(level)))
                Spacer(Modifier.width(6.dp))
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Importance picker for the editor: four coloured circles with the selected label below. */
@Composable
fun ImportancePicker(level: Int, onPick: (Int) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (1..4).forEach { l ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(importanceColor(l))
                        .then(if (l == level) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onPick(if (l == level) 0 else l) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (l == level) Text("✓", color = PlannerColors.OnDone, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Text(
            ImportanceLabels.label(level) ?: "No importance set",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (level > 0) importanceColor(level) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A round initial avatar for a person. */
@Composable
fun PersonAvatar(name: String, size: Int = 44, color: Color = PlannerColors.Event) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color(0xFF132238), fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.42).sp)
    }
}

/** A small "name it" dialog for new people, collections and boards. */
@Composable
fun NameDialog(title: String, initial: String = "", confirm: String = "Add", onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirm, color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
