package com.moneymanager.app.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionAttachmentEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailUiState(
    val txn: TransactionEntity? = null,
    val account: AccountEntity? = null,
    val category: CategoryEntity? = null,
    /** Human-readable name of the account whose outstanding a credit-card bill payment settles. */
    val paidOffAccountName: String? = null,
    val billPaymentMarker: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val billDao: BillDao,
    private val transactionRepository: TransactionRepository,
    val attachmentRepository: AttachmentRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFor(id: Long): Flow<TransactionDetailUiState> =
        transactionDao.observeById(id).flatMapLatest { txn ->
            flow {
                if (txn == null) {
                    emit(TransactionDetailUiState(loading = false, error = "This transaction no longer exists."))
                } else {
                    val paidOffName = txn.paysOffAccountId?.let { accountId ->
                        accountDao.findById(accountId)?.let { it.nickname.ifBlank { it.institutionName } }
                    }
                    val billMarker = runCatching { billDao.findByPaymentTransactionId(txn.id) }.getOrNull()?.let {
                        "Settles a bill payment"
                    }
                    emit(
                        TransactionDetailUiState(
                            txn = txn,
                            account = accountDao.findById(txn.accountId),
                            category = txn.categoryId?.let { categoryDao.findById(it) },
                            paidOffAccountName = paidOffName,
                            billPaymentMarker = billMarker,
                            loading = false
                        )
                    )
                }
            }
        }

    fun attachmentsFor(transactionId: Long): Flow<List<TransactionAttachmentEntity>> =
        attachmentRepository.observeForTransaction(transactionId)

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { transactionRepository.deleteTransaction(id) }
        }
    }
}
