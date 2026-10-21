package com.moneymanager.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BudgetEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.TransactionClassifier
import com.moneymanager.app.domain.accounting.TransactionSemantics
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.reporting.FinancialSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    /** Available balance: included BANK/CASH/WALLET/DEBIT_CARD/PREPAID_CARD accounts. */
    val bankBalance: Money = Money.ZERO,
    val netWorth: Money = Money.ZERO,
    val creditCardOutstanding: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val upcomingBillsTotal: Money = Money.ZERO,
    val upcomingBillsCount: Int = 0,
    val safeToSpend: Money = Money.ZERO,
    /** Which definition Safe to Spend is using, so the UI can label it honestly. */
    val safeToSpendMode: SafeToSpendMode = SafeToSpendMode.CASH_AFTER_BILLS,
    val budget: Money = Money.ZERO,
    val budgetProgress: Float = 0f,
    val cashBalance: Money = Money.ZERO,
    val cashSpend: Money = Money.ZERO,
    val cashWithdrawn: Money = Money.ZERO,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val topCategoryTotals: List<CategoryTotal> = emptyList(),
    /** Category id → display name, so breakdowns can label rows instead of showing bare amounts. */
    val categoryNames: Map<Long, String> = emptyMap(),
    val hasAnyData: Boolean = false
)

/**
 * How [DashboardUiState.safeToSpend] is defined for the current month:
 *  - [BUDGET_REMAINING]: a monthly budget is set, so it is that budget minus this month's spend.
 *  - [CASH_AFTER_BILLS]: no budget is set, so it is available liquid cash minus the remaining
 *    amount still owed on scheduled (upcoming/overdue) bills.
 */
enum class SafeToSpendMode { BUDGET_REMAINING, CASH_AFTER_BILLS }

/**
 * Dashboard totals are derived exclusively from [FinancialSummary]; the drill-down screens use
 * the same functions on the same data, so the tiles and their explanations always reconcile.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    billDao: BillDao,
    budgetDao: com.moneymanager.app.data.local.dao.BudgetDao,
    categoryDao: com.moneymanager.app.data.local.dao.CategoryDao
) : ViewModel() {
    private val period = FinancialSummary.currentMonthPeriod()
    private val monthStart = period.startInclusive
    private val monthEndExclusive = period.endExclusive
    private val month = com.moneymanager.app.domain.billing.BillSchedule.dateOf(monthStart)

    val uiState: StateFlow<DashboardUiState?> = combine(
        accountDao.observeAll(),
        transactionDao.observeInRange(monthStart, monthEndExclusive),
        transactionDao.observeRecent(10),
        billDao.observeUpcomingUnpaid(),
        budgetDao.observeForMonth(month.year, month.monthValue),
        categoryDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST") val accounts = values[0] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST") val monthTransactions = values[1] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST") val recent = values[2] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST") val upcomingBills = values[3] as List<BillInstanceEntity>
        val budgetEntity = values[4] as BudgetEntity?
        @Suppress("UNCHECKED_CAST") val categories = values[5] as List<com.moneymanager.app.data.local.entity.CategoryEntity>
        val accountById = FinancialSummary.accountMap(accounts)

        val balance = FinancialSummary.balanceMinor(accounts)
        val netWorth = FinancialSummary.netWorthMinor(accounts)
        val liabilities = FinancialSummary.liabilityAccounts(accounts)
        val ccOutstanding = liabilities
            .filter { it.accountType == AccountType.CREDIT_CARD }
            .sumOf { it.outstandingMinorUnits ?: 0L }
        val loanBalance = liabilities
            .filter { it.accountType == AccountType.LOAN }
            .sumOf { it.currentBalanceMinorUnits }

        val spendRows = FinancialSummary.spendRows(monthTransactions, accountById, period)
        val incomeRows = FinancialSummary.incomeRows(monthTransactions, accountById, period)
        val monthSpend = spendRows.sumOf { FinancialSummary.spendAmountMinor(it) }
        val monthIncome = incomeRows.sumOf { FinancialSummary.incomeAmountMinor(it) }
        // Remaining still owed on each upcoming occurrence after any partial payments -
        // fully paid occurrences are already excluded by observeUpcomingUnpaid.
        val upcomingTotal = upcomingBills.sumOf { it.amountDueMinorUnits - it.paidAmountMinorUnits }

        val budget = budgetEntity?.budgetAmountMinorUnits ?: 0L
        val progress = if (budget > 0L) (monthSpend.toDouble() / budget.toDouble()).toFloat().coerceAtLeast(0f) else 0f
        // Safe to Spend has exactly two documented definitions, never mixed:
        //  - With a budget: money left in the budget this month. Spending has already reduced
        //    account balances, so subtracting monthSpend again from the balance would
        //    double-count it - hence remaining budget, not balance-minus-spend.
        //  - Without a budget: available liquid cash minus the remaining amount still owed on
        //    scheduled bills (card payments and transfers are not spending, so they never
        //    reduce this twice).
        val safeToSpendMode = if (budget > 0L) SafeToSpendMode.BUDGET_REMAINING else SafeToSpendMode.CASH_AFTER_BILLS
        val safeToSpend = when (safeToSpendMode) {
            SafeToSpendMode.BUDGET_REMAINING -> budget - monthSpend
            SafeToSpendMode.CASH_AFTER_BILLS -> balance - upcomingTotal
        }

        val categoryTotals = spendRows
            .groupBy { it.categoryId }
            .map { (categoryId, rows) -> CategoryTotal(categoryId, rows.sumOf { FinancialSummary.spendAmountMinor(it) }) }
            .filter { it.total != 0L }
            .sortedByDescending { it.total }

        val cashIds = accounts
            .filter { FinancialSummary.isIncluded(it) && it.accountType == AccountType.CASH }
            .map { it.id }
            .toSet()
        val cashBalance = accounts
            .filter { FinancialSummary.isIncluded(it) && it.accountType == AccountType.CASH }
            .sumOf { it.currentBalanceMinorUnits }
        val cashSpend = spendRows.filter { it.accountId in cashIds }
            .sumOf { FinancialSummary.spendAmountMinor(it) }
        val cashIn = monthTransactions
            .filter { it.accountId in cashIds && it.includeInStatistics }
            .filter {
                val type = accountById[it.accountId]?.accountType ?: AccountType.CASH
                when (TransactionClassifier.classify(it.txnSubType, it.txnKind, type)) {
                    TransactionSemantics.INCOME,
                    TransactionSemantics.TRANSFER_IN -> true
                    else -> false
                }
            }
            .sumOf { it.creditMinorUnits - it.debitMinorUnits }

        DashboardUiState(
            bankBalance = Money(balance),
            netWorth = Money(netWorth),
            creditCardOutstanding = Money(ccOutstanding),
            monthSpend = Money(monthSpend),
            monthIncome = Money(monthIncome),
            upcomingBillsTotal = Money(upcomingTotal),
            upcomingBillsCount = upcomingBills.size,
            safeToSpend = Money(safeToSpend),
            safeToSpendMode = safeToSpendMode,
            budget = Money(budget),
            budgetProgress = progress,
            cashBalance = Money(cashBalance),
            cashSpend = Money(cashSpend),
            cashWithdrawn = Money(cashIn),
            recentTransactions = recent,
            topCategoryTotals = categoryTotals.take(5),
            categoryNames = categories.associate { it.id to it.name },
            hasAnyData = recent.isNotEmpty() || accounts.any { FinancialSummary.isIncluded(it) } ||
                loanBalance != 0L
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
