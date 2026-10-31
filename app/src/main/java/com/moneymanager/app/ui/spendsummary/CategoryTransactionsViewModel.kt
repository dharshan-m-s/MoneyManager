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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    val availableMonths: List<MonthWindow> = run {
        val now = java.time.ZonedDateTime.now(zone)
        (0..5).map { index ->
            val start = now.toLocalDate().withDayOfMonth(1).minusMonths(index.toLong()).atStartOfDay(zone)
            val startInclusive = start.toInstant().toEpochMilli()
            val endExclusive = start.plusMonths(1).toInstant().toEpochMilli()
            val label = java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(start)
            MonthWindow(startInclusive, endExclusive, label)
        }
    }

    private val _selectedMonthIndex = kotlinx.coroutines.flow.MutableStateFlow(
        spendAreasSelection.window.value?.let { w ->
            availableMonths.indexOfFirst { it.startInclusive == w.first && it.endExclusive == w.second }.takeIf { it >= 0 }
        } ?: 0
    )
    val selectedMonthIndex: kotlinx.coroutines.flow.StateFlow<Int> = _selectedMonthIndex

    init {
        viewModelScope.launch {
            spendAreasSelection.window.collect { w ->
                if (w != null) {
                    val idx = availableMonths.indexOfFirst { it.startInclusive == w.first && it.endExclusive == w.second }
                    if (idx >= 0 && idx != _selectedMonthIndex.value) {
                        _selectedMonthIndex.value = idx
                    }
                }
            }
        }
    }

    fun selectMonth(index: Int) {
        if (index in availableMonths.indices) {
            _selectedMonthIndex.value = index
            val w = availableMonths[index]
            spendAreasSelection.set(w.startInclusive, w.endExclusive)
        }
    }

    fun selectMonth(startInclusive: Long, endExclusive: Long) {
        spendAreasSelection.set(startInclusive, endExclusive)
    }
}
