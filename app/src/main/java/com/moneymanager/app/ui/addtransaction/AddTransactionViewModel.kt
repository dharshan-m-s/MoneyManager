package com.moneymanager.app.ui.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.repository.NewTransactionInput
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AddTransactionUiState())
    val ui: StateFlow<AddTransactionUiState> = _ui

    fun initialize(entryPoint: AddTransactionEntryPoint, preselectedAccountId: Long?) {
        val isIncome = entryPoint == AddTransactionEntryPoint.ACCOUNT_INCOME || entryPoint == AddTransactionEntryPoint.CASH_INCOME
        val isCash = entryPoint == AddTransactionEntryPoint.CASH_INCOME || entryPoint == AddTransactionEntryPoint.CASH_SPEND
        _ui.value = _ui.value.copy(
            isIncome = isIncome,
            selectedAccountId = preselectedAccountId,
            error = null,
            saved = false
        )

        viewModelScope.launch {
            combine(
                categoryDao.observeAll(),
                if (isCash) accountDao.observeByType(AccountType.CASH) else accountDao.observeActive()
            ) { cats, accts -> cats to accts }.collect { (cats, accts) ->
                val relevantCats = cats.filter {
                    it.kind == CategoryKind.BOTH || it.kind == (if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE)
                }
                _ui.value = _ui.value.copy(
                    categories = relevantCats,
                    accounts = accts,
                    selectedAccountId = _ui.value.selectedAccountId?.takeIf { id -> accts.any { it.id == id } }
                        ?: if (isCash) accts.firstOrNull()?.id else null
                )
            }
        }
    }

    fun onAmountChange(text: String) { _ui.value = _ui.value.copy(amountText = text) }
    fun onMerchantChange(text: String) { _ui.value = _ui.value.copy(merchant = text) }
    fun onCategorySelected(id: Long) { _ui.value = _ui.value.copy(selectedCategoryId = id) }
    fun onIncludeInStatisticsToggle(v: Boolean) { _ui.value = _ui.value.copy(includeInStatistics = v) }
    fun onBusinessPersonalToggle(bp: BusinessPersonal) { _ui.value = _ui.value.copy(businessPersonal = bp) }
    fun onReimbursableToggle(v: Boolean) { _ui.value = _ui.value.copy(reimbursable = v) }
    fun onNotesChange(text: String) { _ui.value = _ui.value.copy(notes = text) }

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
                    paymentType = if (isCash) PaymentType.CASH else PaymentType.NETBANKING,
                    businessPersonal = s.businessPersonal,
                    notes = s.notes.ifBlank { null },
                    reimbursable = s.reimbursable,
                    includeInStatistics = s.includeInStatistics
                )
            )
            _ui.value = _ui.value.copy(saved = true)
        }
    }
}
