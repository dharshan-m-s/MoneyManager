package com.moneymanager.app.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.reporting.FinancialSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CatSpend(val categoryId: Long?, val name: String, val total: Long)

/** Canonical per-month cash totals (spend/income), keyed by the first day of the month. */
data class CashMonthTotals(val spendMinor: Long, val incomeMinor: Long)

data class CashUiState(
    val cashInHand: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val primaryCashAccountId: Long? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val topCategoryTotals: List<CatSpend> = emptyList(),
    val monthTotals: Map<java.time.LocalDate, CashMonthTotals> = emptyMap()
)

@HiltViewModel
class CashViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao
) : ViewModel() {

    // Canonical reporting period - same month window and timezone as the dashboard and every
    // other summary. Computed once per VM instance, consistent with the rest of the app.
    private val period = FinancialSummary.currentMonthPeriod()

    val uiState = combine(
        accountDao.observeByType(AccountType.CASH),
        transactionDao.observeAllCashTransactions(),
        categoryDao.observeAll()
    ) { cashAccounts, allImportedCashTxns, categories ->
        // The Cash screen is calculated from the transaction ledger, not a persisted/imported
        // Balance column - the CSV contains Balance=0 for cash rows and uses cash-forward restore
        // rows as the real opening checkpoint. Historical cash transactions must remain visible
        // even if a legacy import created the cash account with a wrong visibility flag, so
        // history is sourced from every non-deleted CASH account. Balance, however, only counts
        // accounts that participate in combined totals (FinancialSummary.isIncluded), matching
        // the dashboard, and reads the value AccountingService persisted - the single source of
        // truth - instead of re-deriving it with an ad-hoc timestamp filter.
        val includedCashAccounts = cashAccounts.filter { FinancialSummary.isIncluded(it) }
        val historyCashAccounts = cashAccounts.filter { !it.deleted }
        val historyIds = historyCashAccounts.map { it.id }.toSet()
        val allCashTxns = allImportedCashTxns
            .filter { it.accountId in historyIds }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }
                    .thenByDescending { it.id }
            )

        // Balance: persisted authoritative value of included cash accounts.
        val totalBalance = includedCashAccounts.sumOf { it.currentBalanceMinorUnits }

        // Month figures use the canonical spend/income predicates (transaction semantics, not a
        // raw debit/credit sign test), so transfers, cash withdrawals, refunds/reversals and
        // excluded-statistics rows are treated exactly as they are everywhere else in the app.
        val accounts = cashAccounts.associateBy { it.id }
        val monthRows = allCashTxns.filter { period.contains(it.occurredAtEpochMillis) }
        val spendRows = financialSpendRows(monthRows, accounts)
        val incomeRows = financialIncomeRows(monthRows, accounts)
        val spend = spendRows.sumOf { FinancialSummary.spendAmountMinor(it) }
        val income = incomeRows.sumOf { FinancialSummary.incomeAmountMinor(it) }

        // Top Spend Areas from the same canonical spend rows, so the totals reconcile with the
        // month spend above.
        val names = categories.associate { it.id to it.name }
        val categoryTotals = spendRows
            .groupBy { it.categoryId }
            .map { (categoryId, txns) ->
                CatSpend(
                    categoryId = categoryId,
                    name = categoryId?.let(names::get) ?: "Uncategorized",
                    total = txns.sumOf { FinancialSummary.spendAmountMinor(it) }
                )
            }
            .filter { it.total != 0L }
            .sortedByDescending { it.total }
            .take(6)

        val monthTotals = monthRows
            .groupBy {
                java.time.Instant.ofEpochMilli(it.occurredAtEpochMillis)
                    .atZone(FinancialSummary.zone).toLocalDate().withDayOfMonth(1)
            }
            .mapValues { (_, rowsInMonth) ->
                val spend = rowsInMonth.filter { FinancialSummary.isSpendRow(it, accounts) }
                    .sumOf { FinancialSummary.spendAmountMinor(it) }
                val income = rowsInMonth.filter { FinancialSummary.isIncomeRow(it, accounts) }
                    .sumOf { FinancialSummary.incomeAmountMinor(it) }
                CashMonthTotals(spendMinor = spend, incomeMinor = income)
            }

        CashUiState(
            cashInHand = Money(totalBalance),
            monthSpend = Money(spend),
            monthIncome = Money(income),
            primaryCashAccountId = includedCashAccounts.firstOrNull()?.id,
            transactions = allCashTxns,
            topCategoryTotals = categoryTotals,
            monthTotals = monthTotals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashUiState())

    private fun financialSpendRows(
        rows: List<TransactionEntity>,
        accounts: Map<Long, com.moneymanager.app.data.local.entity.AccountEntity>
    ): List<TransactionEntity> = rows.filter { FinancialSummary.isSpendRow(it, accounts) }

    private fun financialIncomeRows(
        rows: List<TransactionEntity>,
        accounts: Map<Long, com.moneymanager.app.data.local.entity.AccountEntity>
    ): List<TransactionEntity> = rows.filter { FinancialSummary.isIncomeRow(it, accounts) }
}
