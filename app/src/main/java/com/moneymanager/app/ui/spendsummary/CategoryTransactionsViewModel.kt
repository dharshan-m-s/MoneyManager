package com.moneymanager.app.ui.spendsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Backs the Spend Areas category drill-down. The month window comes from [SpendAreasSelection],
 * written by [SpendSummaryViewModel], so the drill-down shows the SAME month the user had selected
 * on the summary — never all-time data. When opened without a selection it uses the calendar month.
 */
@HiltViewModel
class CategoryTransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    val spendAreasSelection: SpendAreasSelection
) : ViewModel() {

    fun transactions(categoryId: Long): StateFlow<List<TransactionEntity>> {
        val flow = spendAreasSelection.window.flatMapLatest { window ->
            if (window != null) {
                transactionDao.observeByCategoryInRange(
                    categoryId.takeIf { it >= 0L }, window.first, window.second
                )
            } else {
                val now = java.time.ZonedDateTime.now(zone)
                val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
                transactionDao.observeByCategoryInRange(
                    categoryId.takeIf { it >= 0L },
                    start.toInstant().toEpochMilli(),
                    start.plusMonths(1).toInstant().toEpochMilli()
                )
            }
        }
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /**
     * Complete category history, zero-filled from the oldest recorded month through the current
     * month. This intentionally avoids the former fixed 18-month category-only window.
     */
    fun categoryHistory(categoryId: Long): StateFlow<DashboardSpendHistory> {
        val flow = transactionDao.observeMonthlySpendTotals().flatMapLatest { rows ->
            val oldest = rows.mapNotNull { row ->
                runCatching { java.time.YearMonth.parse(row.yearMonth) }.getOrNull()
            }.minOrNull() ?: java.time.YearMonth.now(zone)
            val current = java.time.YearMonth.now(zone)
            val windows = mutableListOf<MonthWindow>()
            var month = current
            while (!month.isBefore(oldest)) {
                val start = month.atDay(1).atStartOfDay(zone)
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zone)
                windows += MonthWindow(
                    start.toInstant().toEpochMilli(),
                    end.toInstant().toEpochMilli(),
                    java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(start)
                )
                month = month.minusMonths(1)
            }
            val streams = windows.map { window ->
                transactionDao.observeByCategoryInRange(categoryId.takeIf { it >= 0L }, window.startInclusive, window.endExclusive)
            }
            if (streams.isEmpty()) flowOf(DashboardSpendHistory(emptyList(), emptyList()))
            else combine(streams) { monthRows ->
                DashboardSpendHistory(
                    months = windows,
                    totals = monthRows.map { monthTransactions ->
                        monthTransactions.sumOf { it.debitMinorUnits - it.creditMinorUnits }
                    }
                )
            }
        }
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSpendHistory(emptyList(), emptyList()))
    }

    /** Category history clipped to an exact custom date range, grouped by calendar month. */
    fun customCategoryHistory(categoryId: Long, startInclusive: Long, endExclusive: Long): StateFlow<DashboardSpendHistory> {
        val startDate = java.time.Instant.ofEpochMilli(startInclusive).atZone(zone).toLocalDate()
        val endDate = java.time.Instant.ofEpochMilli(endExclusive - 1L).atZone(zone).toLocalDate()
        val windows = mutableListOf<MonthWindow>()
        var month = java.time.YearMonth.from(startDate)
        val lastMonth = java.time.YearMonth.from(endDate)
        while (!month.isAfter(lastMonth)) {
            val bucketStart = maxOf(month.atDay(1), startDate)
            val bucketEnd = minOf(month.plusMonths(1).atDay(1), endDate.plusDays(1))
            windows += MonthWindow(
                bucketStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                bucketEnd.atStartOfDay(zone).toInstant().toEpochMilli(),
                java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(month.atDay(1))
            )
            month = month.plusMonths(1)
        }
        val streams = windows.map { window ->
            transactionDao.observeByCategoryInRange(categoryId.takeIf { it >= 0L }, window.startInclusive, window.endExclusive)
        }
        val flow = if (streams.isEmpty()) flowOf(DashboardSpendHistory(emptyList(), emptyList()))
        else combine(streams) { monthRows ->
            DashboardSpendHistory(
                months = windows.asReversed(),
                totals = monthRows.map { monthTransactions ->
                    monthTransactions.sumOf { it.debitMinorUnits - it.creditMinorUnits }
                }.asReversed()
            )
        }
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSpendHistory(emptyList(), emptyList()))
    }

    suspend fun categoryName(categoryId: Long): String =
        categoryId.takeIf { it >= 0 }?.let { categoryDao.findById(it)?.name } ?: "Uncategorized"

    /** Human label of the window this drill-down is scoped to, e.g. "Sep '26". */
    fun monthLabel(window: Pair<Long, Long>? = spendAreasSelection.window.value): String {
        val effectiveWindow = window ?: spendAreasSelection.window.value
        return if (effectiveWindow != null) {
            val start = java.time.Instant.ofEpochMilli(effectiveWindow.first).atZone(zone)
            java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(start)
        } else {
            val now = java.time.ZonedDateTime.now(zone)
            java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(now)
        }
    }

    private val zone = java.time.ZoneId.of("Asia/Kolkata")

    fun selectMonth(startInclusive: Long, endExclusive: Long) {
        spendAreasSelection.set(startInclusive, endExclusive)
    }
}
