package com.moneymanager.app.ui.spendsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.TxnSubType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoryTransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : ViewModel() {
    private val filter = MutableStateFlow(TxnSubType.EXPENSE.name to true)

    fun configure(subType: TxnSubType, included: Boolean) {
        filter.value = subType.name to included
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun transactions(categoryId: Long): StateFlow<List<TransactionEntity>> =
        filter.flatMapLatest { (subType, included) ->
            transactionDao.observeByCategoryAndIncluded(categoryId.takeIf { it >= 0L }, subType, included)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun categoryName(categoryId: Long): String = categoryId.takeIf { it >= 0 }?.let { categoryDao.findById(it)?.name } ?: "Uncategorized"
}
