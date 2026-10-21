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
import com.moneymanager.app.ui.components.MoneyFormat
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
                val accountsById = accountDao.findByIds(source.map { it.accountId }.toSet())
                    .associateBy { it.id }
                // One transaction can settle exactly one bill, ever - exclude everything the
                // ledger has already counted as a payment.
                val allocated = billDao.findAllocatedTransactionIds().filterNotNull().toSet()
                val linkedCardId = row.bill.linkedAccountId
                val available = source.filter {
                    BillPaymentEligibility.isEligible(
                        txn = it,
                        account = accountsById[it.accountId],
                        linkedCardAccountId = linkedCardId,
                        allocatedTransactionIds = allocated
                    )
                }
                val manualSource = available
                    .sortedByDescending { it.occurredAtEpochMillis }
                    .take(800)
                val recentCandidates = manualSource.map { BillPaymentCandidate(it, accountsById[it.accountId]) }
                val matches = BillPaymentMatcher.findMatches(
                    billName = row.bill.billerName,
                    billAmountMinorUnits = instance.amountDueMinorUnits,
                    dueDateEpochMillis = instance.dueDateEpochMillis,
                    transactions = available
                )
                val matchCandidates = matches.map { BillPaymentCandidate(it.transaction, accountsById[it.transaction.accountId]) }
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

    /** Option B: record a genuine card payment (source account -> card outstanding). The
     *  amount may be partial; the occurrence only settles once allocations cover the due. */
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
                val updated = billDao.findInstanceById(instance.id)
                _paymentSearch.value = BillPaymentSearchState(
                    message = updated?.let { state -> paymentMessage(state) } ?: "Payment recorded"
                )
            }.onFailure {
                _paymentSearch.value = _paymentSearch.value.copy(
                    saving = false,
                    error = it.message ?: "Could not record the payment"
                )
            }
        }
    }

    /** Links an existing transaction ([paymentTransactionId]) or, with a null id, records an
     *  explicit cash payment. [amountMinorUnits] only applies to cash (null = settle the
     *  remaining due); a linked transaction is always allocated at its own amount so the
     *  ledger never counts more (or less) than the money that actually moved. */
    fun markAsPaid(
        instance: BillInstanceEntity,
        paymentTransactionId: Long?,
        amountMinorUnits: Long? = null
    ) {
        val s = _paymentSearch.value
        if (s.saving) return
        _paymentSearch.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching { billRepository.markAsPaid(instance, paymentTransactionId, amountMinorUnits) }
                .onSuccess { result ->
                    _paymentSearch.value = BillPaymentSearchState(message = paymentMessage(result))
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

    private fun paymentMessage(result: BillInstanceEntity): String {
        val remaining = (result.amountDueMinorUnits - result.paidAmountMinorUnits).coerceAtLeast(0L)
        val rupees = { minor: Long -> MoneyFormat.rupeesNoDecimals(Money(minor)) }
        return when {
            result.paid && result.paidAmountMinorUnits > result.amountDueMinorUnits ->
                "Bill settled - ${rupees(result.paidAmountMinorUnits - result.amountDueMinorUnits)} extra recorded"
            result.paid -> "Bill marked as paid"
            else -> "Part payment recorded - ${rupees(remaining)} still due"
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
