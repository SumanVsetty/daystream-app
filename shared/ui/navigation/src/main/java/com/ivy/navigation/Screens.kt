package com.ivy.navigation

import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

data object MainScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object OnboardingScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class CSVScreen(
    val launchedFromOnboarding: Boolean
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class EditTransactionScreen(
    val initialTransactionId: UUID?,
    val type: TransactionType,
    // extras
    val accountId: UUID? = null,
    val categoryId: UUID? = null
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class TransactionsScreen(
    val accountId: UUID? = null,
    val categoryId: UUID? = null,
    val unspecifiedCategory: Boolean? = false,
    val transactionType: TransactionType? = null,
    val accountIdFilterList: List<UUID> = persistentListOf(),
    val transactions: List<Transaction> = persistentListOf()
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class PieChartStatisticScreen(
    val type: TransactionType,
    val filterExcluded: Boolean = true,
    val accountList: ImmutableList<UUID> = persistentListOf(),
    val transactions: ImmutableList<Transaction> = persistentListOf(),
    val treatTransfersAsIncomeExpense: Boolean = false
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class EditPlannedScreen(
    val plannedPaymentRuleId: UUID?,
    val type: TransactionType,
    val amount: Double? = null,
    val accountId: UUID? = null,
    val categoryId: UUID? = null,
    val title: String? = null,
    val description: String? = null,
) : Screen {
    override val isLegacy: Boolean
        get() = true

    fun mandatoryFilled(): Boolean {
        return amount != null && amount > 0.0 &&
                accountId != null
    }
}

data object BalanceScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object PlannedPaymentsScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object CategoriesScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object SettingsScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class ImportScreen(
    val launchedFromOnboarding: Boolean
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object ReportScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object BudgetScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object LoansScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object SearchScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data class LoanDetailsScreen(
    val loanId: UUID
) : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object ExchangeRatesScreen : Screen {
    override val isLegacy: Boolean
        get() = true
}

data object FeaturesScreen : Screen

data object AttributionsScreen : Screen

data object ContributorsScreen : Screen

data object ReleasesScreen : Screen

data object DisclaimerScreen : Screen

data object PollScreen : Screen
// ---------------------------------------------------------------- Apeiro planner

/** Day log. [epochDay] null = today. */
data class PlannerDayScreen(val epochDay: Long? = null) : Screen

/** Week log for an ISO week. Nulls = the current week. */
data class PlannerWeekScreen(val year: Int? = null, val week: Int? = null) : Screen

/** Weekly review of last week's open tasks. */
data object PlannerReviewScreen : Screen

/** Create or edit an entry, or a day of a repeating series. */
data class PlannerEditScreen(
    val entryId: String? = null,
    val seriesId: String? = null,
    val epochDay: Long? = null,
    val kind: String = "TASK",
) : Screen

data object PlannerSearchScreen : Screen

/** Boards (Taskito-style task collections). [boardId] null = the first board. */
data class PlannerBoardsScreen(val boardId: String? = null) : Screen

/**
 * The timeline of one person or one collection (Car, House, TEDLinx…), or with neither,
 * all journal memories. [importance] pre-selects an importance level.
 */
data class PlannerTimelineScreen(
    val personId: String? = null,
    val collectionId: String? = null,
    val importance: Int? = null,
) : Screen

/** A routine on one day: overview, step player and completion. */
data class PlannerRoutineScreen(val seriesId: String, val epochDay: Long) : Screen

/** Create ([seriesId] null) or edit a routine and its steps. */
data class PlannerRoutineEditScreen(val seriesId: String? = null, val epochDay: Long? = null) : Screen

/** All routines. */
data object PlannerRoutinesScreen : Screen

/** Month log for a month, as its key (e.g. 202609). Null = this month. */
data class PlannerMonthScreen(val monthKey: Int? = null) : Screen

/** Closing a month: decide on its open tasks and goals. */
data class PlannerMonthReviewScreen(val monthKey: Int) : Screen

/** Year in review. Null = this year. */
data class PlannerYearScreen(val year: Int? = null) : Screen

/** Automatic backups: folder and frequency. */
data object PlannerAutoBackupScreen : Screen
