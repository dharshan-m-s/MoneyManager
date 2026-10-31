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

    /**
     * Bills in the order the user has to act on them: the occurrence that must be paid soonest
     * comes first (a bill due in 3 days before one due in 10 days), and already-settled bills
     * follow, sorted by the due date of the occurrence that was paid.
     */
    val bills = combine(
        billDao.observeActive(),
        billDao.observeUpcomingUnpaid(),
        billDao.observePaid()
    ) { bills, unpaid, paid ->
        bills.map { bill ->
            val next = unpaid.filter { it.billId == bill.id }.minByOrNull { it.dueDateEpochMillis }
            val settled = paid.filter { it.billId == bill.id }.maxByOrNull { it.paidAtEpochMillis ?: 0L }
            BillRow(
                bill = bill,
                nextInstance = next,
                latestPaid = settled
            )
        }.sortedWith(
            // Open occurrences first (soonest due date at the top), settled bills afterwards.
            compareBy<BillRow> { if (it.nextInstance != null) 0 else 1 }
                .thenBy {
                    it.nextInstance?.dueDateEpochMillis
                        ?: it.latestPaid?.dueDateEpochMillis
                        ?: it.bill.nextDueDateEpochMillis
                }
                .thenBy { it.bill.billerName.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Automatic card-payment detection removed per requirements - only explicit selection+confirmation

    private val _paymentSearch = MutableStateFlow(BillPaymentSearchState())
    val paymentSearch = _paymentSearch

    companion object {
        // Explicit candidate window around due date to avoid pulling unrelated history
        // Support early payments (up to 90 days before) and short post-due window (up to 60 days after)
        private const val DEFAULT_WINDOW_BEFORE_DAYS = 90L
        private const val DEFAULT_WINDOW_AFTER_DAYS = 60L
        private const val DAY_MS = 86_400_000L
    }

    fun findPaymentTransactions(row: BillRow, searchOlder: Boolean = false) {
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
                val allocated = billDao.findAllocatedTransactionIds().filterNotNull().toSet()
                // Also exclude linked peers of allocated transactions
                val allocatedPeers = mutableSetOf<Long>()
                allocated.forEach { allocId ->
                    transactionDao.findById(allocId)?.linkedTransferTransactionId?.let { allocatedPeers.add(it) }
                }
                val excluded = (allocated + allocatedPeers).toSet()
                val linkedCardId = row.bill.linkedAccountId
                val accountsByIdAll = mutableMapOf<Long, AccountEntity>()

                val availableList = if (searchOlder) {
                    // Deliberate search of older transactions - still apply all eligibility rules
                    val recent = transactionDao.findRecent(2000)
                    val accountsById = accountDao.findByIds(recent.map { it.accountId }.toSet())
                        .associateBy { it.id }
                    accountsByIdAll.putAll(accountsById)
                    recent.filter {
                        if (it.id in excluded) return@filter false
                        BillPaymentEligibility.isEligible(
                            txn = it,
                            account = accountsById[it.accountId],
                            linkedCardAccountId = linkedCardId,
                            allocatedTransactionIds = excluded
                        )
                    }
                } else {
                    // Use explicit window only - no blanket fallback to entire history
                    val start = instance.dueDateEpochMillis - DEFAULT_WINDOW_BEFORE_DAYS * DAY_MS
                    val end = instance.dueDateEpochMillis + DEFAULT_WINDOW_AFTER_DAYS * DAY_MS
                    val inWindow = transactionDao.findInRange(start, end)
                    val accountsById = accountDao.findByIds(inWindow.map { it.accountId }.toSet())
                        .associateBy { it.id }
                    accountsByIdAll.putAll(accountsById)
                    inWindow.filter {
                        if (it.id in excluded) return@filter false
                        BillPaymentEligibility.isEligible(
                            txn = it,
                            account = accountsById[it.accountId],
                            linkedCardAccountId = linkedCardId,
                            allocatedTransactionIds = excluded
                        )
                    }
                }

                val accountsById = accountsByIdAll
                val manualSource = availableList
                    .sortedByDescending { it.occurredAtEpochMillis }
                    .take(800)
                val recentCandidates = manualSource.map { BillPaymentCandidate(it, accountsById[it.accountId]) }
                val matches = BillPaymentMatcher.findMatches(
                    billName = row.bill.billerName,
                    billAmountMinorUnits = instance.amountDueMinorUnits,
                    dueDateEpochMillis = instance.dueDateEpochMillis,
                    transactions = availableList
                )
                val matchCandidates = matches.map { BillPaymentCandidate(it.transaction, accountsById[it.transaction.accountId]) }
                // Ensure no transaction appears in both suggestions and recent list
                val matchIds = matchCandidates.map { it.transaction.id }.toSet()
                val dedupedRecent = recentCandidates.filter { it.transaction.id !in matchIds }
                val paymentAccounts = accountDao.findAll()
                    .filter {
                        it.active && !it.deleted && it.accountType != AccountType.CREDIT_CARD &&
                            !it.isSyntheticUnlinkedAccount
                    }
                    .sortedBy { it.nickname.lowercase() }
                Triple(matchCandidates, dedupedRecent, paymentAccounts)
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

    fun searchOlderTransactions() {
        val row = _paymentSearch.value.billRow ?: return
        findPaymentTransactions(row, searchOlder = true)
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

    fun markAsPaidDirect(instance: BillInstanceEntity, amountMinorUnits: Long = 0L) {
        markAsPaid(instance, null, amountMinorUnits)
    }

    /** Links a payment that has already been recorded against the card (the "payment detected"
     *  path). The money already moved and the card's outstanding already fell, so this only
     *  records that the bill occurrence is settled. */
    fun markDetectedPaymentPaid(row: BillRow) {
        // Automatic detected payment shortcut removed
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
