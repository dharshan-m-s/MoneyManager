package com.moneymanager.app.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository
) : ViewModel() {
    fun observe(id: Long): Flow<TransactionEntity?> = transactionDao.observeById(id)
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeActive()

    fun saveEditable(
        id: Long,
        amount: String,
        merchant: String,
        notes: String,
        business: Boolean,
        reimbursable: Boolean,
        includeInStatistics: Boolean,
        categoryId: Long?,
        dateEpochMillis: Long?,
        onDone: () -> Unit
    ) {
        val parsed = runCatching { Money.fromRupees(BigDecimal(amount.trim())) }.getOrNull()
        if (parsed == null || parsed.minorUnits < 0L) return
        viewModelScope.launch {
            transactionRepository.updateEditableTransaction(
                transactionId = id,
                amount = parsed,
                merchantReceiverSender = merchant.ifBlank { null },
                notes = notes.ifBlank { null },
                businessPersonal = if (business) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                reimbursable = reimbursable,
                includeInStatistics = includeInStatistics,
                categoryId = categoryId,
                occurredAtEpochMillis = dateEpochMillis
            )
            onDone()
        }
    }
}
