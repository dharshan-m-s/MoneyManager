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
 * correctness bug, not just a performance one. SQL LIKE does the filtering; only the (capped)
 * result set reaches the UI.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<TransactionEntity>> = _query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else transactionDao.searchTransactions(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _query.value = q }
}
