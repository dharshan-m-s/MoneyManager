package com.moneymanager.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tokenized relevance-ranked search.
 *
 * "bike petrol" must match a transaction saved as "petrol bike":
 *  - exact phrase hit ranks first
 *  - all-words-in-any-order ranks next
 *  - partial word hits rank below, ordered by matched-token count then recency
 *
 * SQL LIKE does the broad candidate fetch per token (so 13k+ history is covered
 * without loading the whole ledger); Kotlin does the ranking.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: StateFlow<List<TransactionEntity>> = _query
        .debounce(180)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else flow { emit(rankedSearch(q)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _query.value = q }

    fun clearQuery() { _query.value = "" }

    private suspend fun rankedSearch(raw: String): List<TransactionEntity> {
        val query = raw.trim()
        if (query.isEmpty()) return emptyList()
        val qLower = query.lowercase()
        val tokens = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }.take(6)
        if (tokens.isEmpty()) return emptyList()

        // Broad candidate fetch: full phrase + each token, merged distinct.
        val keys = (listOf(query) + tokens).distinct()
        val seen = LinkedHashMap<Long, TransactionEntity>()
        for (k in keys) {
            if (k.isBlank()) continue
            runCatching { transactionDao.searchTransactionsOnce(k, 5000) }
                .getOrDefault(emptyList())
                .forEach { seen[it.id] = it }
            if (seen.size >= 8000) break
        }
        if (seen.isEmpty()) return emptyList()

        return seen.values
            .map { it to relevanceScore(it, qLower, tokens) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<TransactionEntity, Int>> { it.second }
                    .thenByDescending { it.first.occurredAtEpochMillis }
                    .thenByDescending { it.first.id }
            )
            .map { it.first }
            .take(2000)
    }

    private fun relevanceScore(
        txn: TransactionEntity,
        qLower: String,
        tokens: List<String>
    ): Int {
        val merchant = txn.merchantReceiverSender?.lowercase().orEmpty()
        val category = txn.rawCategoryName?.lowercase().orEmpty()
        val notes = txn.notes?.lowercase().orEmpty()
        val payment = txn.rawPaymentType?.lowercase().orEmpty()
        val hay = "$merchant $category $notes $payment"

        // Tier 1: exact phrase present. Bonus when it hits the merchant (primary field)
        // or starts the field — those are the most "exact" feeling results.
        if (hay.contains(qLower)) {
            var score = 1000
            if (merchant.contains(qLower)) score += 300
            if (category.contains(qLower)) score += 120
            if (merchant.startsWith(qLower) || category.startsWith(qLower)) score += 150
            // Shorter haystacks are closer to a pure exact match.
            score += (200 - hay.length.coerceAtMost(200))
            return score
        }

        val matched = tokens.count { hay.contains(it) }
        if (matched == 0) return 0

        // Tier 2: every word present in any order ("petrol bike" vs "bike petrol").
        if (matched == tokens.size) {
            var score = 500 + tokens.size * 20
            if (tokens.all { merchant.contains(it) }) score += 120
            return score
        }

        // Tier 3: partial — rank by how many words hit. Single-token queries that
        // reach here already failed the exact check, so keep them low but visible.
        return matched * 80
    }
}
