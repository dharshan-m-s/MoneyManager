package com.moneymanager.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionDao: TransactionDao
) : ViewModel() {
    val transactions: StateFlow<List<TransactionEntity>> = transactionDao.observeRecent(20000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
