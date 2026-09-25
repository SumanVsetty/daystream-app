package com.ivy.planner.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TagAssociationDao
import com.ivy.data.db.dao.read.TagDao
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
    val category: String? = null,
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
    private val tagDao: TagDao,
    private val tagAssociationDao: TagAssociationDao,
) {
    suspend fun between(from: LocalDate, to: LocalDate): List<MoneyItem> {
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
        return toItems(runCatching { transactionDao.findAllBetween(start, end) }.getOrDefault(emptyList()))
    }

    /** The wallet's tags: id → name. */
    suspend fun walletTags(): List<Pair<UUID, String>> =
        runCatching { tagDao.findAll() }.getOrDefault(emptyList()).filter { !it.isDeleted }.map { it.id to it.name }

    /** Expenses and income carrying any of [tagIds], newest first. */
    suspend fun tagged(tagIds: Set<UUID>): List<MoneyItem> {
        if (tagIds.isEmpty()) return emptyList()
        val ids = runCatching { tagAssociationDao.findByAllAssociatedIdForTagId(tagIds.toList()) }.getOrDefault(emptyMap())
            .values.flatten().filter { !it.isDeleted }.map { it.associatedId }.distinct()
        if (ids.isEmpty()) return emptyList()
        return toItems(ids.chunked(500).flatMap { runCatching { transactionDao.findByIds(it) }.getOrDefault(emptyList()) })
            .sortedByDescending { it.date }
    }

    private suspend fun toItems(all: List<com.ivy.data.db.entity.TransactionEntity>): List<MoneyItem> {
        val zone = ZoneId.systemDefault()
        @Suppress("DEPRECATION")
        val transactions = all.filter { !it.isDeleted && (it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME) }
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
                category = category,
            )
        }
    }
}

/** The day the planner is showing, shared with the app's add menu so new entries land on it. */
object PlannerSelection {
    var date: LocalDate by mutableStateOf(LocalDate.now())
}

/** Spending with a person or on a collection, from its linked wallet tags. */
data class MoneySummary(
    val thisYear: String?,
    val allTime: String?,
    /** e.g. "School ₹38k" */
    val topCategories: List<String>,
    val count: Int,
)

fun summarize(items: List<MoneyItem>, year: Int): MoneySummary {
    val spent = items.filter { !it.isIncome }
    fun total(list: List<MoneyItem>): String? = list.groupBy { it.currency }
        .map { (c, l) -> formatMoney(l.sumOf { it.amount }, c) }
        .takeIf { it.isNotEmpty() }?.joinToString(" + ")
    val top = spent.groupBy { it.category ?: "Other" }
        .map { (cat, l) -> cat to l.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(3)
        .map { (cat, amount) -> "$cat " + formatShort(amount, spent.firstOrNull()?.currency.orEmpty()) }
    return MoneySummary(total(spent.filter { it.date.year == year }), total(spent), top, spent.size)
}

/** "₹38k", "₹1.2L" style, for compact summaries (Indian lakhs for INR). */
fun formatShort(amount: Double, currencyCode: String): String {
    val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(Locale("en", "IN")) }.getOrDefault(currencyCode)
    val a = abs(amount)
    val text = when {
        currencyCode == "INR" && a >= 1_00_00_000 -> String.format(Locale.ENGLISH, "%.1fCr", a / 1_00_00_000)
        currencyCode == "INR" && a >= 1_00_000 -> String.format(Locale.ENGLISH, "%.1fL", a / 1_00_000)
        a >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fM", a / 1_000_000)
        a >= 10_000 -> String.format(Locale.ENGLISH, "%.0fk", a / 1_000)
        a >= 1_000 -> String.format(Locale.ENGLISH, "%.1fk", a / 1_000)
        else -> String.format(Locale.ENGLISH, "%.0f", a)
    }
    return symbol + text.replace(".0L", "L").replace(".0M", "M").replace(".0Cr", "Cr").replace(".0k", "k")
}
