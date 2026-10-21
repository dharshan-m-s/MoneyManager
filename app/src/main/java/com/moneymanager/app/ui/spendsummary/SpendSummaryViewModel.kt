package com.moneymanager.app.ui.spendsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.dao.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SpendCategoryRow(val categoryId: Long?, val name: String, val total: Long, val colorHex: String)

/** One selectable month window, newest last in the list. */
data class MonthWindow(val startInclusive: Long, val endExclusive: Long, val label: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpendSummaryViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val spendAreasSelection: SpendAreasSelection
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** Current month index (0 = current month, 1 = last month, …). */
    private val _selectedMonthIndex = MutableStateFlow(0)
    val selectedMonthIndex: StateFlow<Int> = _selectedMonthIndex

    /** The six months the user can pick, newest first — matching the reference graph. Built once from a single "now". */
    val availableMonths: List<MonthWindow> = run {
        val now = ZonedDateTime.now(zone)
        (0..5).map { index ->
            val start = now.toLocalDate().withDayOfMonth(1).minusMonths(index.toLong()).atStartOfDay(zone)
            val startInclusive = start.toInstant().toEpochMilli()
            val endExclusive = start.plusMonths(1).toInstant().toEpochMilli()
            val label = DateTimeFormatter.ofPattern("MMM ''yy").format(start)
            MonthWindow(startInclusive, endExclusive, label)
        }
    }

    /** Category totals for the selected month ONLY — never cumulative all-time data. */
    val categoryRows: StateFlow<List<SpendCategoryRow>> = combine(
        _selectedMonthIndex.flatMapLatest { index ->
            val window = availableMonths[index]
            transactionDao.observeCategoryTotalsInRange(window.startInclusive, window.endExclusive)
        },
        categoryDao.observeAll()
    ) { totals: List<CategoryTotal>, categories ->
        val byId = categories.associateBy { it.id }
        // Keep every category with a non-zero net contribution - including a category that nets
        // negative because refunds exceeded spend - so the rows and the total are the same
        // calculation. Consumers render negative rows as credits rather than dropping them.
        totals.filter { it.total != 0L }.map { t ->
            val cat = t.categoryId?.let { byId[it] }
            SpendCategoryRow(t.categoryId, cat?.name ?: "Uncategorized", t.total, cat?.colorHex ?: "#9E9E9E")
        }.sortedByDescending { it.total }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Canonical total spend for the selected month (same query definition the graph and the
     *  dashboard use), in minor units. */
    val selectedMonthTotal: StateFlow<Long> = _selectedMonthIndex
        .flatMapLatest { index ->
            val window = availableMonths[index]
            transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** Total spend for the immediately previous calendar month (0 when the screen is on the oldest month). */
    val previousMonthTotal: StateFlow<Long> = _selectedMonthIndex
        .map { idx -> if (idx + 1 < availableMonths.size) idx + 1 else -1 }
        .flatMapLatest { prevIdx ->
            if (prevIdx >= 0) {
                val window = availableMonths[prevIdx]
                transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
            } else {
                flowOf(0L)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** True only when the previous month actually has spending — used to avoid misleading ₹0 comparisons. */
    val hasPreviousMonthData: StateFlow<Boolean> = combine(
        previousMonthTotal,
        _selectedMonthIndex
    ) { total, idx ->
        total > 0L && idx + 1 < availableMonths.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Total spend per month for the on-screen graph, index-aligned with [availableMonths]
     * (0 = newest). One Room stream per window keeps every bar reactive to data changes.
     */
    val monthlyTotals: StateFlow<List<Long>> = combine(
        availableMonths.map { window ->
            transactionDao.observeTotalSpendInRange(window.startInclusive, window.endExclusive)
        }
    ) { values: Array<Long> -> values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(availableMonths.size) { 0L })

    fun selectMonth(index: Int) {
        if (index in availableMonths.indices) {
            _selectedMonthIndex.value = index
            // Keep the category drill-down on the same month the user is looking at.
            val w = availableMonths[index]
            spendAreasSelection.set(w.startInclusive, w.endExclusive)
        }
    }

    /** Label of the month immediately before the given index, for comparison captions. */
    fun previousMonthLabel(index: Int): String? =
        availableMonths.getOrNull(index + 1)?.label
}
