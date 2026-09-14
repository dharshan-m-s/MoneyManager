package com.moneymanager.app.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val transactionRepository: TransactionRepository,
    private val receiptStorage: com.moneymanager.app.data.repository.ReceiptStorage
) : ViewModel() {
    fun observe(id: Long): Flow<TransactionEntity?> = transactionDao.observeById(id)
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeActive()
    fun observeAccount(id: Long): Flow<com.moneymanager.app.data.local.entity.AccountEntity?> = accountDao.observeById(id)

    fun saveEditable(id: Long, amount: String, isIncome: Boolean, merchant: String, notes: String, business: Boolean, reimbursable: Boolean, includeInStatistics: Boolean, categoryId: Long?, receiptTempPath: String? = null, removeReceipt: Boolean = false, onDone: () -> Unit) {
        val parsed = runCatching { Money.fromRupees(java.math.BigDecimal(amount.trim())) }.getOrNull()
        if (parsed == null || parsed.minorUnits < 0L) return
        viewModelScope.launch {
            transactionRepository.updateEditableTransaction(id, parsed, isIncome, merchant.ifBlank { null }, notes.ifBlank { null }, if (business) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL, reimbursable, includeInStatistics, categoryId, receiptTempPath, removeReceipt)
            onDone()
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            val existing = transactionDao.findById(id)
            transactionRepository.deleteTransaction(id)
            existing?.receiptPath?.let { receiptStorage.delete(it) }
            onDone()
        }
    }

    suspend fun importReceipt(uri: android.net.Uri): String? = receiptStorage.importFromUri(uri)

    fun prepareCameraCapture(): Pair<java.io.File, android.net.Uri> = receiptStorage.createCaptureTempFile()

    fun attachReceipt(id: Long, stagedPath: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (transactionRepository.attachReceipt(id, stagedPath)) onDone()
        }
    }

    fun receiptFileName(path: String?): String? = receiptStorage.fileName(path)
}
