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
    spendAreasSelection: SpendAreasSelection
) : ViewModel() {

    /** Epoch window (startInclusive, endExclusive) of the month to show; null = calendar month. */
    private val monthWindow: Pair<Long, Long>? = spendAreasSelection.window.value

    fun transactions(categoryId: Long): StateFlow<List<TransactionEntity>> {
        val window = monthWindow
        val flow = if (window != null) {
            transactionDao.observeByCategoryInRange(
                categoryId.takeIf { it >= 0L }, window.first, window.second
            )
        } else {
            val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
            transactionDao.observeByCategoryInRange(
                categoryId.takeIf { it >= 0L },
                start.toInstant().toEpochMilli(),
                start.plusMonths(1).toInstant().toEpochMilli()
            )
        }
        return flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    suspend fun categoryName(categoryId: Long): String =
        categoryId.takeIf { it >= 0 }?.let { categoryDao.findById(it)?.name } ?: "Uncategorized"

    /** Human label of the window this drill-down is scoped to, e.g. "Sep '26". */
    fun monthLabel(): String {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val window = monthWindow ?: return run {
            val now = java.time.ZonedDateTime.now(zone)
            java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(now)
        }
        val start = java.time.Instant.ofEpochMilli(window.first).atZone(zone)
        return java.time.format.DateTimeFormatter.ofPattern("MMM ''yy").format(start)
    }
}
