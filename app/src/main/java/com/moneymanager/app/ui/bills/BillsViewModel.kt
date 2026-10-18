package com.moneymanager.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal
import javax.inject.Inject

data class BillRow(
    val bill: BillEntity,
    val nextInstance: BillInstanceEntity?,
    val latestPaid: BillInstanceEntity?
)

data class BillPaymentCandidate(
    val transaction: TransactionEntity,
    val account: AccountEntity?
)

data class BillPaymentSearchState(
    val billRow: BillRow? = null,
    val matches: List<BillPaymentCandidate> = emptyList(),
    val recentTransactions: List<BillPaymentCandidate> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedTransactionId: Long? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val recordSourceAccountId: Long? = null,
    val recordAmount: String = "",
    val recordDateEpochMillis: Long = System.currentTimeMillis(),
    val recordNotes: String = "",
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billDao: BillDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val billRepository: BillRepository
) : ViewModel() {

    val bills = combine(
        billDao.observeActive(),
        billDao.observeUpcomingUnpaid(),
        billDao.observePaid()
    ) { bills, unpaid, paid ->
        bills.map { bill ->
            BillRow(
                bill = bill,
                nextInstance = unpaid.filter { it.billId == bill.id }.minByOrNull { it.dueDateEpochMillis },
                latestPaid = paid.filter { it.billId == bill.id }.maxByOrNull { it.paidAtEpochMillis ?: 0L }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _paymentSearch = MutableStateFlow(BillPaymentSearchState())
    val paymentSearch = _paymentSearch

    fun findPaymentTransactions(row: BillRow) {
        val instance = row.nextInstance ?: return
        _paymentSearch.value = BillPaymentSearchState(
            billRow = row,
            loading = true,
            recordAmount = Money(instance.amountDueMinorUnits).toBigDecimal()
                .stripTrailingZeros().toPlainString(),
            recordDateEpochMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            runCatching {
                // Search a useful window around the due date first. If that produces no good
                // match, fall back to the latest 500 transactions so an early payment can still
                // be found without loading the whole 13k+ ledger into memory.
                val start = instance.dueDateEpochMillis - 60L * 86_400_000L
                val end = instance.dueDateEpochMillis + 30L * 86_400_000L
                val inWindow = transactionDao.findInRange(start, end)
                val recent = transactionDao.findRecent(500)
                val source = (inWindow + recent).distinctBy { it.id }
                val linkedIds: Set<Long> = billDao.findLinkedPaymentTransactionIds(source.map { it.id }.toSet()).toSet()
                val available = source.filterNot { it.id in linkedIds }
                val allAccounts = accountDao.findByIds(available.map { it.accountId }.toSet()).associateBy { it.id }
                val manualSource = (recent + inWindow)
                    .distinctBy { it.id }
                    .filter { it.debitMinorUnits > 0 && it.creditMinorUnits <= 0 && it.id !in linkedIds }
                    .sortedByDescending { it.occurredAtEpochMillis }
                    .take(800)
                val recentCandidates = manualSource.map { BillPaymentCandidate(it, allAccounts[it.accountId]) }
                val matches = BillPaymentMatcher.findMatches(
                    billName = row.bill.billerName,
                    billAmountMinorUnits = instance.amountDueMinorUnits,
                    dueDateEpochMillis = instance.dueDateEpochMillis,
                    transactions = available
                )
                val matchAccounts = accountDao.findByIds(matches.map { it.transaction.accountId }.toSet())
                    .associateBy { it.id }
                val matchCandidates = matches.map { BillPaymentCandidate(it.transaction, matchAccounts[it.transaction.accountId]) }
                val paymentAccounts = accountDao.findAll()
                    .filter {
                        it.active && !it.deleted && it.accountType != AccountType.CREDIT_CARD &&
                            !it.isSyntheticUnlinkedAccount
                    }
                    .sortedBy { it.nickname.lowercase() }
                Triple(matchCandidates, recentCandidates, paymentAccounts)
            }.onSuccess { (matches, recentCandidates, paymentAccounts) ->
                if (_paymentSearch.value.billRow?.bill?.id != row.bill.id) return@onSuccess
                _paymentSearch.value = _paymentSearch.value.copy(
                    billRow = row,
                    matches = matches,
                    recentTransactions = recentCandidates,
                    accounts = paymentAccounts,
                    recordSourceAccountId = _paymentSearch.value.recordSourceAccountId
                        ?: paymentAccounts.firstOrNull { it.accountType == AccountType.BANK }?.id
                        ?: paymentAccounts.firstOrNull()?.id,
                    loading = false
                )
            }.onFailure { error ->
                if (_paymentSearch.value.billRow?.bill?.id != row.bill.id) return@onFailure
                _paymentSearch.value = _paymentSearch.value.copy(
                    billRow = row,
                    loading = false,
                    error = error.message ?: "Could not search transactions"
                )
            }
        }
    }

    fun selectPaymentTransaction(transactionId: Long?) {
        _paymentSearch.value = _paymentSearch.value.copy(selectedTransactionId = transactionId)
    }

    fun setRecordSourceAccount(id: Long) {
        _paymentSearch.value = _paymentSearch.value.copy(recordSourceAccountId = id, error = null)
    }

    fun setRecordAmount(value: String) {
        _paymentSearch.value = _paymentSearch.value.copy(recordAmount = value, error = null)
    }

    fun setRecordDate(epochMillis: Long) {
        _paymentSearch.value = _paymentSearch.value.copy(recordDateEpochMillis = epochMillis)
    }

    fun setRecordNotes(value: String) {
        _paymentSearch.value = _paymentSearch.value.copy(recordNotes = value)
    }

    /** Option B: record a genuine card payment (source account -> card outstanding). */
    fun recordPayment() {
        val s = _paymentSearch.value
        val instance = s.billRow?.nextInstance ?: return
        val sourceId = s.recordSourceAccountId
        if (sourceId == null) {
            _paymentSearch.value = s.copy(error = "Choose the account the payment came from")
            return
        }
        val amount = runCatching {
            Money.fromRupees(BigDecimal(s.recordAmount.trim().ifBlank { "0" })).minorUnits
        }.getOrNull()
        if (amount == null || amount <= 0L) {
            _paymentSearch.value = s.copy(error = "Enter a valid payment amount")
            return
        }
        if (s.saving) return
        _paymentSearch.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                billRepository.recordCreditCardBillPayment(
                    instanceId = instance.id,
                    sourceAccountId = sourceId,
                    amountMinorUnits = amount,
                    occurredAtEpochMillis = s.recordDateEpochMillis,
                    notes = s.recordNotes.ifBlank { null }
                )
            }.onSuccess {
                _paymentSearch.value = BillPaymentSearchState(message = "Payment recorded")
            }.onFailure {
                _paymentSearch.value = _paymentSearch.value.copy(
                    saving = false,
                    error = it.message ?: "Could not record the payment"
                )
            }
        }
    }

    fun markAsPaid(instance: BillInstanceEntity, paymentTransactionId: Long?) {
        val s = _paymentSearch.value
        if (s.saving) return
        _paymentSearch.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching { billRepository.markAsPaid(instance, paymentTransactionId) }
                .onSuccess {
                    _paymentSearch.value = BillPaymentSearchState(message = "Bill marked as paid")
                }
                .onFailure {
                    _paymentSearch.value = _paymentSearch.value.copy(
                        error = it.message ?: "Could not mark bill as paid",
                        loading = false,
                        saving = false
                    )
                }
        }
    }

    fun clearPaymentSearch() {
        _paymentSearch.value = BillPaymentSearchState()
    }

    fun consumeMessage() {
        if (_paymentSearch.value.message != null) {
            _paymentSearch.value = _paymentSearch.value.copy(message = null)
        }
    }
}
