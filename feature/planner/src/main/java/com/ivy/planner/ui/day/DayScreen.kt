package com.ivy.planner.ui.day

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.PlannerDayScreen
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerMonthScreen
import com.ivy.navigation.PlannerReviewScreen
import com.ivy.navigation.PlannerRoutineScreen
import com.ivy.navigation.PlannerSearchScreen
import com.ivy.navigation.PlannerWeekScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.domain.key
import com.ivy.planner.ui.CheckCircle
import com.ivy.planner.ui.DayTitleFmt
import com.ivy.planner.ui.EntryRow
import com.ivy.planner.ui.MonthCalendar
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerDatePicker
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RapidLog
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.ShortDayFmt
import com.ivy.planner.ui.WeekStrip
import com.ivy.planner.ui.add.PlannerAddSheetHost
import com.ivy.planner.ui.swipeDays
import com.ivy.planner.ui.timelineColor
import com.ivy.planner.ui.withWeek
import java.time.LocalDate

@Composable
fun PlannerDayScreenImpl(screen: PlannerDayScreen) {
    val viewModel: DayViewModel = screenScopedViewModel()
    LaunchedEffect(screen) {
        screen.epochDay?.let { viewModel.onEvent(DayEvent.SelectDate(LocalDate.ofEpochDay(it))) }
    }
    PlannerTheme {
        DayChooser(viewModel.uiState(), viewModel::onEvent, asTab = false)
        PlannerAddSheetHost()
    }
}

/** The Day log as the app's main "Day" tab (no back button; the bottom bar's logo button adds entries). */
@Composable
fun PlannerDayTab() {
    val viewModel: DayViewModel = screenScopedViewModel()
    PlannerTheme { DayChooser(viewModel.uiState(), viewModel::onEvent, asTab = true) }
}

/** The Day tab: today's 3, still open, later today (with free time) and earlier today. */
@Composable
private fun DayChooser(state: DayState, onEvent: (DayEvent) -> Unit, asTab: Boolean) {
    run {
        // refresh expenses when coming back (e.g. after adding one in the wallet)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onEvent(DayEvent.Refresh) }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        FocusDayUi(state, onEvent, asTab) {
            NotificationBanner()
            if (state.date == state.today && state.today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
                val nav = navigation()
                SlimBanner("New week: review last week", "Review") { nav.navigateTo(PlannerReviewScreen) }
            }
        }
    }
}

/** Asks for notification permission on Android 13+, until it's allowed. */
@Composable
private fun NotificationBanner() {
    val context = LocalContext.current
    fun allowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(allowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    if (!ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SlimBanner("Allow notifications for reminders", "Allow") { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

@Composable
private fun SlimBanner(text: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp)
        Text(action, color = PlannerColors.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
