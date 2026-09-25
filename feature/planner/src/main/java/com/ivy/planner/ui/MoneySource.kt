package com.ivy.planner.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToLong

/** An expense or income, as shown on the planner timeline. */
@Immutable
data class MoneyItem(
    val id: UUID,
    val date: LocalDate,
    val time: LocalTime,
    val title: String,
    val account: String,
    val amount: Double,
    val currency: String,
    val isIncome: Boolean,
) {
    /** "−₹320" / "+₹25,000" */
    val label: String get() = (if (isIncome) "+" else "−") + formatMoney(amount, currency)
}

fun formatMoney(amount: Double, currencyCode: String): String {
    val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(Locale("en", "IN")) }.getOrDefault(currencyCode)
    val whole = abs(amount) >= 100 || abs(amount - amount.roundToLong()) < 0.005
    val number = if (whole) String.format(Locale.ENGLISH, "%,d", abs(amount).roundToLong())
    else String.format(Locale.ENGLISH, "%,.2f", abs(amount))
    return "$symbol$number"
}

/** Reads expenses and income from Ivy's money database for the planner (read-only). */
@Singleton
class MoneySource @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
) {
    suspend fun between(from: LocalDate, to: LocalDate): List<MoneyItem> {
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
        val transactions = runCatching { transactionDao.findAllBetween(start, end) }.getOrDefault(emptyList())
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
        if (transactions.isEmpty()) return emptyList()
        val categories = categoryDao.findAll().associateBy { it.id }
        val accounts = accountDao.findAll().associateBy { it.id }
        val baseCurrency = runCatching { settingsDao.findFirstOrNull()?.currency }.getOrNull() ?: ""
        return transactions.mapNotNull { t ->
            val at = t.dateTime?.atZone(zone) ?: return@mapNotNull null
            val category = t.categoryId?.let { categories[it]?.name }
            val account = accounts[t.accountId]
            MoneyItem(
                id = t.id,
                date = at.toLocalDate(),
                time = at.toLocalTime().withSecond(0).withNano(0),
                title = listOfNotNull(t.title?.takeIf { it.isNotBlank() }, category).distinct().joinToString(" · ")
                    .ifBlank { if (t.type == TransactionType.INCOME) "Income" else "Expense" },
                account = account?.name ?: "",
                amount = t.amount,
                currency = account?.currency?.takeIf { it.isNotBlank() } ?: baseCurrency,
                isIncome = t.type == TransactionType.INCOME,
            )
        }
    }
}

/** The day the planner is showing, shared with the app's add menu so new entries land on it. */
object PlannerSelection {
    var date: LocalDate by mutableStateOf(LocalDate.now())
}
