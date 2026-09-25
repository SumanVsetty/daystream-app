package com.ivy.planner.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@Immutable
data class JournalState(val entries: ImmutableList<Entry>)

/** First version of the Journal tab: journal entries and notes, newest first. */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<JournalState, Unit>() {
    @Composable
    override fun uiState(): JournalState {
        val entries by remember { repository.observeJournal() }.collectAsState(initial = emptyList())
        return JournalState(entries.toImmutableList())
    }

    override fun onEvent(event: Unit) = Unit
}

@Composable
fun PlannerJournalTab() {
    val viewModel: JournalViewModel = screenScopedViewModel()
    PlannerTheme { JournalUi(viewModel.uiState()) }
}

@Composable
private fun JournalUi(state: JournalState) {
    val nav = navigation()
    // group by month, e.g. "SEPTEMBER 2026"
    val groups = state.entries.groupBy { e ->
        e.date?.let { "${it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${it.year}" } ?: "No date"
    }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 110.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Journal", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.entries.count { it.kind == EntryKind.JOURNAL }} entries · ${state.entries.count { it.kind == EntryKind.NOTE }} notes",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { nav.navigateTo(PlannerSearchScreen) }) { Icon(Icons.Outlined.Search, "Search") }
                }
            }
            if (state.entries.isEmpty()) {
                item {
                    Text(
                        "Your journal is empty. Tap the logo to write your first entry.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groups.forEach { (month, entries) ->
                item(key = "h:$month") {
                    Box(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) {
                        SectionLabel(month, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { it.id }) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                nav.navigateTo(PlannerEditScreen(entryId = e.id, epochDay = e.date?.toEpochDay(), kind = e.kind.name))
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            if (e.kind == EntryKind.JOURNAL) {
                                Icon(Icons.Filled.Star, contentDescription = "Journal", tint = PlannerColors.Journal, modifier = Modifier.size(20.dp))
                            } else {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(PlannerColors.Done))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title,
                                fontSize = 16.sp,
                                fontWeight = if (e.kind == EntryKind.JOURNAL) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (e.description.isNotBlank()) {
                                Text(
                                    e.description,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                listOfNotNull(e.date?.withWeek(), e.time?.label()).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
