package com.moneymanager.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Searches via a real SQL query (TransactionDao.searchTransactions) rather than loading a
 * capped recent-transactions window and filtering client-side. The dataset can hold 13,000+
 * historical transactions - a client-side cap would silently miss older matches, which is a
 * correctness bug, not just a performance one. SQL LIKE and the parsed amount predicate filter
 * the complete ledger; Compose only receives matching rows.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<TransactionEntity>> = _query.flatMapLatest { q ->
        if (q.isBlank()) {
            flowOf(emptyList())
        } else {
            val amountMinorUnits = parseAmountMinorUnits(q)
            // Match the Activity page policy: ±5%, clamped to ₹25–₹500.
            val tolerance = amountMinorUnits?.let {
                (it / 20L).coerceAtLeast(2_500L).coerceAtMost(50_000L)
            } ?: 0L
            transactionDao.searchTransactions(
                query = q,
                amountMinorUnits = amountMinorUnits,
                amountToleranceMinorUnits = tolerance
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _query.value = q }

    /** Plain rupees, comma grouping and ₹ are accepted; mixed text is not treated as a number. */
    private fun parseAmountMinorUnits(raw: String): Long? {
        val cleaned = raw.trim().replace("₹", "").replace(",", "").replace(" ", "")
        if (!cleaned.matches(Regex("\\d+(\\.\\d{1,2})?"))) return null
        return runCatching {
            (cleaned.toBigDecimal() * java.math.BigDecimal(100)).longValueExact()
        }.getOrNull()
    }
}
