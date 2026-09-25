package com.ivy.planner.ui.reminders

import com.ivy.planner.data.PlannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Started with the app: whenever tasks, repeating tasks or reminders change,
 * the alarms are re-planned (after a short pause, so a burst of edits plans once).
 */
@Singleton
class ReminderSync @Inject constructor(
    private val repository: PlannerRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        notifier.ensureChannel()
        scope.launch {
            repository.reminderChanges().debounce(1_500).collect {
                runCatching { scheduler.schedule() }
            }
        }
    }
}
