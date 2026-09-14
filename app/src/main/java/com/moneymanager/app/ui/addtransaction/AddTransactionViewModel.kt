package com.moneymanager.app.ui.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.repository.CategoryImageStorage
import com.moneymanager.app.data.repository.NewTransactionInput
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.data.repository.ReceiptStorage
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** Identifies the transaction-entry route and its fixed money direction. Account entries always
 * arrive with an account context; cash entries resolve their dedicated cash account. */
enum class AddTransactionEntryPoint { ACCOUNT_INCOME, ACCOUNT_SPEND, CASH_INCOME, CASH_SPEND }

data class AddTransactionUiState(
    val isIncome: Boolean = true,
    /** "Spend ON/OFF" / "Income ON/OFF" - whether this entry counts toward the app's spend
     *  (or income) totals. Defaults to ON. This is NOT an income/expense type switch: the
     *  income/spend type is fixed by which FAB opened the screen. */
    val includeInStatistics: Boolean = true,
    val amountText: String = "",
    val merchant: String = "",
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val businessPersonal: BusinessPersonal = BusinessPersonal.PERSONAL,
    val reimbursable: Boolean = false,
    val notes: String = "",
    /** Payment method for non-cash entries. Cash entries always save as PaymentType.CASH;
     *  the money direction FAB that opened the screen determines which one applies. */
    val paymentType: PaymentType = PaymentType.UPI,
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val saved: Boolean = false,
    val receiptTempPath: String? = null,
    val receiptLabel: String? = null,
    val error: String? = null
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository,
    val imageStorage: CategoryImageStorage,
    private val receiptStorage: ReceiptStorage
) : ViewModel() {

    private val _ui = MutableStateFlow(AddTransactionUiState())
    val ui: StateFlow<AddTransactionUiState> = _ui
    private var initializeJob: Job? = null

    fun initialize(entryPoint: AddTransactionEntryPoint, preselectedAccountId: Long?) {
        val isCash = entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND
        _ui.value = _ui.value.copy(
            isIncome = typeDefaultFor(entryPoint),
            selectedAccountId = preselectedAccountId,
            error = null,
            saved = false
        )

        initializeJob?.cancel()
        initializeJob = viewModelScope.launch {
            // The selected income/spend type is the single source of truth for the visible
            // category set, so category filtering reacts to in-screen type changes too.
            combine(
                categoryDao.observeAll(),
                _ui.map { it.isIncome },
                if (isCash) accountDao.observeByType(AccountType.CASH) else accountDao.observeActive()
            ) { cats, isIncome, accts -> Triple(cats, isIncome, accts) }.collect { (cats, isIncome, accts) ->
                val relevantCats = cats.filter {
                    it.kind == CategoryKind.BOTH || it.kind == (if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE)
                }
                val currentSelection = _ui.value.selectedAccountId
                val stableSelection = when {
                    currentSelection != null && accts.any { it.id == currentSelection } -> currentSelection
                    preselectedAccountId != null && accts.any { it.id == preselectedAccountId } -> preselectedAccountId
                    isCash -> accts.firstOrNull()?.id
                    else -> null
                }
                _ui.value = _ui.value.copy(
                    categories = relevantCats,
                    accounts = accts,
                    selectedAccountId = stableSelection
                )
            }
        }
    }

    private fun typeDefaultFor(entryPoint: AddTransactionEntryPoint): Boolean =
        entryPoint == AddTransactionEntryPoint.ACCOUNT_INCOME || entryPoint == AddTransactionEntryPoint.CASH_INCOME

    fun onAmountChange(text: String) { _ui.value = _ui.value.copy(amountText = text) }
    fun onMerchantChange(text: String) { _ui.value = _ui.value.copy(merchant = text) }
    fun onCategorySelected(id: Long) { _ui.value = _ui.value.copy(selectedCategoryId = id) }
    fun onIncludeInStatisticsToggle(v: Boolean) { _ui.value = _ui.value.copy(includeInStatistics = v) }
    fun onBusinessPersonalToggle(bp: BusinessPersonal) { _ui.value = _ui.value.copy(businessPersonal = bp) }
    fun onReimbursableToggle(v: Boolean) { _ui.value = _ui.value.copy(reimbursable = v) }
    fun onNotesChange(text: String) { _ui.value = _ui.value.copy(notes = text) }
    fun onPaymentTypeSelected(type: PaymentType) { _ui.value = _ui.value.copy(paymentType = type) }

    fun onReceiptStaged(path: String?, label: String?) {
        val previous = _ui.value.receiptTempPath
        _ui.value = _ui.value.copy(receiptTempPath = path, receiptLabel = label, error = null)
        if (!previous.isNullOrBlank() && previous != path) {
            viewModelScope.launch { receiptStorage.delete(previous) }
        }
    }

    fun clearReceipt() {
        val old = _ui.value.receiptTempPath
        _ui.value = _ui.value.copy(receiptTempPath = null, receiptLabel = null)
        old?.let { viewModelScope.launch { receiptStorage.delete(it) } }
    }

    fun prepareCameraCapture(): Pair<java.io.File, android.net.Uri> = receiptStorage.createCaptureTempFile()

    fun stagePickedReceipt(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = receiptStorage.importFromUri(uri)
            if (path != null) onReceiptStaged(path, "Receipt image attached")
            else _ui.value = _ui.value.copy(error = "Couldn't read that image. Please choose another file.")
        }
    }

    fun stageCapturedReceipt(path: String) {
        onReceiptStaged(path, "Camera photo attached")
    }

    /** The income/spend type is domain state, not presentation-only: changing it here changes
     *  the category set offered below and, at save time, the persisted transaction direction. */
    fun onIncomeSpendChange(isIncome: Boolean) {
        val current = _ui.value
        _ui.value = current.copy(
            isIncome = isIncome,
            // A transaction type that no longer matches the currently selected category resets
            // the selection so a Spend can never silently persist under an Income-only category.
            selectedCategoryId = current.selectedCategoryId?.takeIf { id ->
                current.categories.any {
                    it.id == id && (it.kind == CategoryKind.BOTH || it.kind == (if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE))
                }
            }
        )
    }

    fun save(entryPoint: AddTransactionEntryPoint) {
        val s = _ui.value
        val amount = runCatching { BigDecimal(s.amountText.trim()) }.getOrNull()
        if (amount == null || amount.signum() <= 0) {
            _ui.value = s.copy(error = "Enter a valid amount")
            return
        }
        val accountId = s.selectedAccountId
        if (accountId == null || s.accounts.none { it.id == accountId }) {
            _ui.value = s.copy(error = "This transaction needs an account. Go back and choose an account first.")
            return
        }
        val isCash = entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND
        val selectedCategory = s.categories.firstOrNull { it.id == s.selectedCategoryId }

        viewModelScope.launch {
            transactionRepository.addTransaction(
                NewTransactionInput(
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    accountId = accountId,
                    categoryId = s.selectedCategoryId,
                    rawCategoryName = selectedCategory?.name,
                    merchantReceiverSender = s.merchant.ifBlank { null },
                    amount = Money.fromRupees(amount),
                    isIncome = s.isIncome,
                    paymentType = if (isCash) PaymentType.CASH else s.paymentType,
                    businessPersonal = s.businessPersonal,
                    notes = s.notes.ifBlank { null },
                    reimbursable = s.reimbursable,
                    includeInStatistics = s.includeInStatistics,
                    receiptTempPath = s.receiptTempPath
                )
            )
            _ui.value = _ui.value.copy(saved = true)
        }
    }
}
