package com.ivy.planner.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.Entry
import com.ivy.planner.ui.withWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchState(val query: String, val results: ImmutableList<Entry>)

@HiltViewModel
class PlannerSearchViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<SearchState, String>() {
    private var query by mutableStateOf("")
    private var results by mutableStateOf<ImmutableList<Entry>>(persistentListOf())
    private var job: Job? = null

    @Composable
    override fun uiState() = SearchState(query, results)

    /** The event is the new query text. */
    override fun onEvent(event: String) {
        query = event
        job?.cancel()
        job = viewModelScope.launch {
            delay(250)
            results = repository.search(event).toImmutableList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerSearchScreenImpl() {
    val viewModel: PlannerSearchViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    val nav = navigation()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onEvent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search tasks, events and notes") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            if (state.query.isNotBlank()) {
                Text(
                    "${state.results.size} results",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
                items(state.results, key = { it.id }) { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                nav.navigateTo(PlannerEditScreen(entryId = e.id, epochDay = e.date?.toEpochDay()))
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            listOfNotNull(e.date?.withWeek() ?: e.week?.label(), e.kind.name.lowercase(), e.state.name.lowercase())
                                .joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (e.description.isNotBlank()) {
                            Text(e.description, maxLines = 2, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
