package com.moneymanager.app.ui.transactionedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionAttachmentEntity
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnSubType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject

data class TransactionEditUiState(
    val transactionId: Long = -1,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Transfers keep their paired legs intact; amount and date apply to BOTH legs (the
     *  repository mirrors them), while category, direction and account stay managed by the
     *  transfer itself. Per-leg facts - merchant, payment method, notes - edit in place. */
    val isTransfer: Boolean = false,
    /** Either leg is a credit-card bill payment: its amount belongs to the bill-payment model
     *  (section 3), so the amount field is read-only here and the repository rejects changes. */
    val isBillPaymentPair: Boolean = false,
    val isIncome: Boolean = false,
    val originalIsIncome: Boolean = false,
    val amount: String = "",
    val epochMillis: Long? = null,
    val merchant: String = "",
    val notes: String = "",
    val categoryId: Long? = null,
    val accountId: Long = -1,
    val paymentType: PaymentType = PaymentType.NONE,
    val reimbursable: Boolean = false,
    val includeInStatistics: Boolean = true,
    val amountError: String? = null,
    val dateError: String? = null,
    val accountError: String? = null,
    val error: String? = null
)

/** Payment methods offered when editing. "Unspecified" replaces the raw NULL/NONE sentinels. */
val editablePaymentTypes: List<PaymentType> = listOf(
    PaymentType.CASH,
    PaymentType.UPI,
    PaymentType.CREDIT_CARD,
    PaymentType.DEBIT_CARD,
    PaymentType.NETBANKING,
    PaymentType.ONLINE_TRANSFER,
    PaymentType.IMPS,
    PaymentType.CHEQUE,
    PaymentType.PREPAID_CARD,
    PaymentType.NONE
)

private val editZone = ZoneId.of("Asia/Kolkata")

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository,
    val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(TransactionEditUiState())
    val ui: StateFlow<TransactionEditUiState> = _ui

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The account a transaction already belongs to, even when inactive. Historical edits must
     * show the real account (clearly marked) instead of a blank field that invites recreating it.
     */
    private val originalAccount = MutableStateFlow<AccountEntity?>(null)
    val originalAccountName: StateFlow<String?> = originalAccount
        .map { account ->
            account?.takeIf { !it.active }?.let { acc ->
                val name = acc.nickname.ifBlank { acc.institutionName }
                "$name (inactive account)"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun attachmentsFor(id: Long): Flow<List<TransactionAttachmentEntity>> =
        attachmentRepository.observeForTransaction(id)

    fun load(id: Long) {
        viewModelScope.launch {
            val txn = transactionDao.findById(id) ?: return@launch
            val isTransfer = txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
            val peer = if (isTransfer) txn.linkedTransferTransactionId?.let { transactionDao.findById(it) } else null
            val isBillPaymentPair = txn.isCreditCardBillPayment || txn.paysOffAccountId != null ||
                peer?.isCreditCardBillPayment == true || (peer != null && peer.paysOffAccountId != null)
            val isIncome = txn.creditMinorUnits != 0L && txn.debitMinorUnits == 0L
            originalAccount.value = accountDao.findById(txn.accountId)
            _ui.value = TransactionEditUiState(
                transactionId = txn.id,
                amount = Money(if (isIncome) txn.creditMinorUnits else txn.debitMinorUnits)
                    .abs()
                    .toBigDecimal()
                    .stripTrailingZeros()
                    .toPlainString(),
                epochMillis = txn.occurredAtEpochMillis,
                merchant = txn.merchantReceiverSender.orEmpty(),
                notes = txn.notes.orEmpty(),
                categoryId = txn.categoryId,
                accountId = txn.accountId,
                paymentType = txn.paymentType.takeIf { it in editablePaymentTypes } ?: PaymentType.NONE,
                reimbursable = txn.reimbursable,
                includeInStatistics = txn.includeInStatistics,
                isTransfer = isTransfer,
                isBillPaymentPair = isBillPaymentPair,
                isIncome = isIncome,
                originalIsIncome = isIncome,
                loaded = true
            )
        }
    }

    fun onField(update: TransactionEditUiState.() -> TransactionEditUiState) {
        val previous = _ui.value
        val next = previous.update()
        // Clear a field-level error as soon as the user changes that field.
        _ui.value = next.copy(
            amountError = if (next.amount != previous.amount) null else next.amountError,
            dateError = if (next.epochMillis != previous.epochMillis) null else next.dateError,
            accountError = if (next.accountId != previous.accountId) null else next.accountError,
            error = null
        )
    }

    fun save() {
        val s = _ui.value
        val amountValue = runCatching { Money.fromRupees(BigDecimal(s.amount.trim())) }.getOrNull()
        val amountError = when {
            s.amount.isBlank() -> "Enter an amount"
            amountValue == null -> "Enter a valid number"
            amountValue.minorUnits <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val dateError = if (s.epochMillis == null) "Choose a date" else null
        val accountError = if (!s.isTransfer && s.accountId < 0) "Choose an account" else null
        val notLoadedError = if (!s.loaded || s.transactionId <= 0L) "Transaction is not ready to save yet" else null
        if (amountError != null || dateError != null || accountError != null || notLoadedError != null) {
            _ui.value = s.copy(
                amountError = amountError,
                dateError = dateError,
                accountError = accountError,
                error = notLoadedError
            )
            return
        }
        _ui.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                transactionRepository.updateEditableTransaction(
                    transactionId = s.transactionId,
                    // Pair-level fact: the repository mirrors the amount to BOTH legs of a
                    // plain transfer. A credit-card bill payment never sends one - the field is
                    // read-only there and the amount stays owned by the bill-payment model.
                    amount = if (s.isBillPaymentPair) null else amountValue,
                    merchantReceiverSender = s.merchant.trim(),
                    notes = s.notes.trim(),
                    reimbursable = s.reimbursable,
                    includeInStatistics = s.includeInStatistics,
                    categoryId = if (s.isTransfer) null else s.categoryId,
                    occurredAtEpochMillis = s.epochMillis,
                    accountId = if (s.isTransfer) null else s.accountId,
                    paymentType = s.paymentType,
                    isIncome = if (s.isTransfer || s.isIncome == s.originalIsIncome) null else s.isIncome
                )
                _ui.value = _ui.value.copy(saving = false, saved = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    saving = false,
                    error = "Could not save: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

}
