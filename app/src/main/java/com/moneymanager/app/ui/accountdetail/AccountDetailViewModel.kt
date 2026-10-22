package com.moneymanager.app.ui.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.accounting.AccountingEngine.BillingCycleWindow
import com.moneymanager.app.domain.billing.CreditCardStatement
import com.moneymanager.app.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class MonthGroup(val label: String, val income: Long, val expense: Long, val transactions: List<TransactionEntity>)

data class CycleGroup(
    val label: String,
    val startInclusive: Long,
    val endExclusive: Long,
    val income: Long,
    val expense: Long,
    val transactions: List<TransactionEntity>
) {
    val isCurrent: Boolean get() = BillingCycleWindow(startInclusive, endExclusive, label).contains(System.currentTimeMillis())
}

data class AccountDetailUiState(
    val account: AccountEntity? = null,
    val isCreditCard: Boolean = false,
    val maskedCardNumber: String = "",
    val monthGroups: List<MonthGroup> = emptyList(),
    val cycleGroups: List<CycleGroup> = emptyList(),
    val currentCycle: CycleGroup? = null,
    val loadedCount: Int = 0,
    val canLoadMore: Boolean = false,
    /** The card's current statement - what this cycle's bill will be for. */
    val currentStatementMinorUnits: Long = 0L,
    /** When the statement of the running cycle falls due. */
    val statementDueDateEpochMillis: Long? = null
)

private const val PAGE_SIZE = 300

/**
 * Loads an account's transaction history in bounded pages rather than one unbounded query
 * (spec Phase 21), and derives the credit-card presentation data (masked card number plus the
 * Monthly and Billing Cycle groupings using the canonical billing-cycle window math) for the
 * reference-style credit-card account screen. Non-card accounts keep the month-only view.
 */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val billDao: BillDao
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val limitFlow = MutableStateFlow(PAGE_SIZE)
    private var currentAccountId: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFor(accountId: Long) = combine(
        accountDao.observeById(accountId),
        limitFlow.flatMapLatest { limit -> transactionDao.observeByAccountLimited(accountId, limit) },
        transactionDao.observeFullAccountHistory(accountId)
    ) { account, txns, fullHistory ->
        val isCard = account?.accountType == AccountType.CREDIT_CARD
        // Month/cycle income & expense are computed from the FULL history, not the bounded page
        // being displayed, so a subtotal never changes just because fewer rows were paged in.
        //
        // Performance: index each side ONCE instead of re-scanning the page for every group
        // (the previous shape was O(groups x page = O(groups x history)). Aggregates come from a
        // single pass over the full history; displayed rows come from a single pass over the page.
        val fullByMonth = HashMap<YearMonth, MutableList<TransactionEntity>>()
        for (t in fullHistory) {
            val ym = YearMonth.from(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(zone))
            fullByMonth.getOrPut(ym) { ArrayList() }.add(t)
        }
        val pageByMonth = HashMap<YearMonth, MutableList<TransactionEntity>>()
        for (t in txns) {
            val ym = YearMonth.from(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(zone))
            pageByMonth.getOrPut(ym) { ArrayList() }.add(t)
        }
        val monthGroups = fullByMonth.entries
            .sortedByDescending { it.key }
            .map { (ym, list) ->
                MonthGroup(
                    label = "${ym.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} ${ym.year}",
                    income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                    expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                    transactions = (pageByMonth[ym] ?: emptyList())
                        .sortedByDescending { it.occurredAtEpochMillis }
                )
            }
        val cycleStartDay = account?.billingCycleStartDay ?: 1
        val cycleGroups = if (isCard) {
            val fullByCycle = HashMap<Long, MutableList<TransactionEntity>>()
            for (t in fullHistory) {
                val start = AccountingEngine.billingCycleWindowFor(cycleStartDay, t.occurredAtEpochMillis, zone).startInclusive
                fullByCycle.getOrPut(start) { ArrayList() }.add(t)
            }
            val pageByCycle = HashMap<Long, MutableList<TransactionEntity>>()
            for (t in txns) {
                val start = AccountingEngine.billingCycleWindowFor(cycleStartDay, t.occurredAtEpochMillis, zone).startInclusive
                pageByCycle.getOrPut(start) { ArrayList() }.add(t)
            }
            fullByCycle.entries
                .sortedByDescending { it.key }
                .map { (startEpoch, list) ->
                    val window = AccountingEngine.billingCycleWindowFor(cycleStartDay, startEpoch, zone)
                    CycleGroup(
                        label = window.label,
                        startInclusive = window.startInclusive,
                        endExclusive = window.endExclusive,
                        income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                        expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                        transactions = (pageByCycle[startEpoch] ?: emptyList())
                            .sortedByDescending { it.occurredAtEpochMillis }
                    )
                }
        } else {
            emptyList()
        }
        val nowWindow = AccountingEngine.billingCycleWindowFor(cycleStartDay, System.currentTimeMillis(), zone)
        val currentCycle = if (isCard) {
            cycleGroups.firstOrNull { it.startInclusive <= nowWindow.startInclusive } ?: CycleGroup(
                label = nowWindow.label,
                startInclusive = nowWindow.startInclusive,
                endExclusive = nowWindow.endExclusive,
                income = 0,
                expense = 0,
                transactions = emptyList()
            )
        } else {
            null
        }
        // The card's statement for the cycle that is running right now, and the day that
        // statement falls due. The amount is kept in step with the card's ledger by the
        // billing service, so the number here is the same one the linked bill carries. The
        // due date is the day the bill occurrence that SETTLES this very cycle falls due -
        // not simply the next due date on the bill's schedule.
        val currentStatement = if (isCard) {
            account?.billAmountMinorUnits ?: account?.outstandingMinorUnits ?: 0L
        } else {
            0L
        }
        val statementDueDate = if (isCard && account != null) {
            val bill = billDao.findByLinkedAccountId(account.id)
            val settling = bill?.let { linked ->
                billDao.findInstancesForBill(linked.id).firstOrNull { instance ->
                    CreditCardStatement.cycleSettledByDueDate(cycleStartDay, instance.dueDateEpochMillis)
                        .startInclusive == nowWindow.startInclusive
                }?.dueDateEpochMillis
            }
            settling
                ?: account.dueDate
                ?: CreditCardStatement.defaultDueDate(cycleStartDay, System.currentTimeMillis())
        } else {
            null
        }
        AccountDetailUiState(
            account = account,
            isCreditCard = isCard,
            maskedCardNumber = maskedNumber(account?.sourceAccountId),
            monthGroups = monthGroups,
            cycleGroups = cycleGroups,
            currentCycle = currentCycle,
            loadedCount = txns.size,
            canLoadMore = txns.size >= limitFlow.value,
            currentStatementMinorUnits = currentStatement,
            statementDueDateEpochMillis = statementDueDate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState())

    private fun maskedNumber(sourceId: String?): String {
        val digits = sourceId?.filter { it.isDigit() }.orEmpty()
        return if (digits.length >= 4) "xxxx${digits.takeLast(4)}" else ""
    }

    fun loadMore() {
        limitFlow.value += PAGE_SIZE
    }
}