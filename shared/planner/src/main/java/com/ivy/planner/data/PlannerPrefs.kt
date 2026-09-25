package com.ivy.planner.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small planner preferences, e.g. the Day view filter. */
@Singleton
class PlannerPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("apeiro_planner", Context.MODE_PRIVATE)

    /** true = "To do" view, false = "Everything". */
    var todoOnly: Boolean
        get() = prefs.getBoolean(KEY_TODO_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TODO_ONLY, value).apply()
        }

    private companion object {
        const val KEY_TODO_ONLY = "day_filter_todo_only"
    }
}
