package com.moneymanager.app.ui.income

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
class IncomeViewModel @Inject constructor(
    transactionDao: TransactionDao
) : ViewModel() {
    val transactions: StateFlow<List<TransactionEntity>> = transactionDao.observeRecentIncome(20000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
