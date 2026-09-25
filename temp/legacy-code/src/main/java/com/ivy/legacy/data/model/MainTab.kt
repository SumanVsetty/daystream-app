package com.ivy.legacy.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class MainTab {
    /** Apeiro planner tabs come first; HOME is the money overview. */
    DAY, WEEK, JOURNAL, HOME, ACCOUNTS
}
