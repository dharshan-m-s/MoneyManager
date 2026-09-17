package com.moneymanager.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BudgetEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class DashboardUiState(
    val bankBalance: Money = Money.ZERO,
    val netWorth: Money = Money.ZERO,
    val creditCardOutstanding: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val upcomingBillsTotal: Money = Money.ZERO,
    val upcomingBillsCount: Int = 0,
    val safeToSpend: Money = Money.ZERO,
    val budget: Money = Money.ZERO,
    val budgetProgress: Float = 0f,
    val cashBalance: Money = Money.ZERO,
    val cashSpend: Money = Money.ZERO,
    val cashWithdrawn: Money = Money.ZERO,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val topCategoryTotals: List<CategoryTotal> = emptyList(),
    val hasAnyData: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    billDao: BillDao,
    budgetDao: com.moneymanager.app.data.local.dao.BudgetDao,
    private val accountingService: AccountingService
) : ViewModel() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monthStart: Long
    private val monthEndExclusive: Long
    private val year: Int
    private val month: Int

    init {
        val now = ZonedDateTime.now(zone)
        val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        val end = start.plusMonths(1)
        monthStart = start.toInstant().toEpochMilli()
        monthEndExclusive = end.toInstant().toEpochMilli()
        year = start.year
        month = start.monthValue
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        accountDao.observeTotalBalance(),
        accountDao.observeTotalNetAssets(),
        accountDao.observeTotalCreditCardOutstanding(),
        accountDao.observeTotalLoanBalance(),
        transactionDao.observeTotalSpendInRange(monthStart, monthEndExclusive),
        transactionDao.observeTotalIncomeInRange(monthStart, monthEndExclusive),
        billDao.observeTotalUpcomingBillAmount(),
        billDao.observeUpcomingUnpaid(),
        transactionDao.observeRecent(10),
        transactionDao.observeCategoryTotalsInRange(monthStart, monthEndExclusive),
        budgetDao.observeForMonth(year, month),
        accountDao.observeByType(AccountType.CASH),
        transactionDao.observeAllCashTransactions()
    ) { values ->
        val bankBalance = values[0] as Long
        val netAssets = values[1] as Long
        val ccOutstanding = values[2] as Long
        val loanBalance = values[3] as Long
        val monthSpend = values[4] as Long
        val monthIncome = values[5] as Long
        val upcomingBillsTotal = values[6] as Long
        @Suppress("UNCHECKED_CAST") val upcomingBills = values[7] as List<BillInstanceEntity>
        @Suppress("UNCHECKED_CAST") val recent = values[8] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST") val categoryTotals = values[9] as List<CategoryTotal>
        val budgetEntity = values[10] as BudgetEntity?
        @Suppress("UNCHECKED_CAST") val cashAccounts = values[11] as List<com.moneymanager.app.data.local.entity.AccountEntity>
        @Suppress("UNCHECKED_CAST") val cashTransactions = values[12] as List<TransactionEntity>
        val budget = budgetEntity?.budgetAmountMinorUnits ?: 0L
        val netWorth = netAssets - ccOutstanding - loanBalance
        val safeToSpend = if (budget > 0L) budget - monthSpend - upcomingBillsTotal else bankBalance - monthSpend - upcomingBillsTotal
        val progress = if (budget > 0L) (monthSpend.toDouble() / budget.toDouble()).toFloat().coerceAtLeast(0f) else 0f
        val activeCashIds = cashAccounts.filter { it.active && !it.deleted && !it.hide }.map { it.id }.toSet()
        var cashBalanceMinor = 0L
        cashAccounts.filter { it.active && !it.deleted && !it.hide }.forEach { account ->
            val rows = cashTransactions.filter { it.accountId == account.id }
            val ledger = accountingService.ledgerRowsFor(rows, AccountType.CASH)
            val calculated = if (account.manualBalanceOverrideMinorUnits != null && account.manualBalanceOverrideAtEpochMillis != null) {
                val post = ledger.asSequence().filter { it.occurredAtEpochMillis > account.manualBalanceOverrideAtEpochMillis }
                    .sumOf { it.movement.ledgerDeltaMinorUnits }
                account.manualBalanceOverrideMinorUnits + post
            } else {
                AccountingEngine.balanceState(ledger).calculatedBalanceMinorUnits
            }
            cashBalanceMinor += calculated
        }
        val cashMonthTxns = cashTransactions.filter {
            it.accountId in activeCashIds && it.occurredAtEpochMillis >= monthStart && it.occurredAtEpochMillis < monthEndExclusive
        }
        val cashSpendMinor = cashMonthTxns.filter { it.includeInStatistics && it.debitMinorUnits > it.creditMinorUnits }
            .sumOf { it.debitMinorUnits - it.creditMinorUnits }
        val cashWithdrawnMinor = cashMonthTxns.filter { it.includeInStatistics && it.creditMinorUnits > it.debitMinorUnits }
            .sumOf { it.creditMinorUnits - it.debitMinorUnits }

        DashboardUiState(
            bankBalance = Money(bankBalance),
            netWorth = Money(netWorth),
            creditCardOutstanding = Money(ccOutstanding),
            monthSpend = Money(monthSpend),
            monthIncome = Money(monthIncome),
            upcomingBillsTotal = Money(upcomingBillsTotal),
            upcomingBillsCount = upcomingBills.size,
            safeToSpend = Money(safeToSpend),
            budget = Money(budget),
            budgetProgress = progress,
            cashBalance = Money(cashBalanceMinor),
            cashSpend = Money(cashSpendMinor),
            cashWithdrawn = Money(cashWithdrawnMinor),
            recentTransactions = recent,
            topCategoryTotals = categoryTotals.take(6),
            hasAnyData = recent.isNotEmpty() || bankBalance != 0L || netWorth != 0L
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
