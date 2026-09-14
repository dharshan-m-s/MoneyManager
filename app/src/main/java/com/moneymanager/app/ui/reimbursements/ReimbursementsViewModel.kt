package com.moneymanager.app.ui.reimbursements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReimbursementsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val pending = transactionDao.observePendingReimbursements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markReimbursed(transactionId: Long) {
        viewModelScope.launch { transactionRepository.markReimbursed(transactionId) }
    }
}
