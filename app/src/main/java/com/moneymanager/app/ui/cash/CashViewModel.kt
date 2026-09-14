package com.moneymanager.app.ui.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.time.ZoneId
import java.time.ZonedDateTime

data class CatSpend(val categoryId: Long?, val name: String, val total: Long)

data class CashUiState(
    val cashInHand: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val primaryCashAccountId: Long? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val topCategoryTotals: List<CatSpend> = emptyList()
)

@HiltViewModel
class CashViewModel @Inject constructor(
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val accountingService: AccountingService
) : ViewModel() {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val monthStart: Long
    private val monthEndExclusive: Long

    init {
        val start = ZonedDateTime.now(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        monthStart = start.toInstant().toEpochMilli()
        monthEndExclusive = start.plusMonths(1).toInstant().toEpochMilli()
    }

    val uiState = combine(
        accountDao.observeByType(AccountType.CASH),
        transactionDao.observeAllCashTransactions(),
        categoryDao.observeAll()
    ) { cashAccounts, allImportedCashTxns, categories ->
        // IMPORTANT: the Cash screen is calculated from the transaction ledger, not from a
        // persisted/imported Balance column. The CSV contains Balance=0 for cash rows and uses
        // cash-forward restore rows as the real opening checkpoint. AccountingService/
        // AccountingEngine is therefore the single source of truth for the displayed balance.
        // Historical cash transactions must remain visible even if a legacy import created the
        // cash account with an incorrect visibility flag. Balance display still respects the
        // user's active/hidden account settings, but history/analytics are sourced from every
        // non-deleted CASH account so imported rows cannot disappear from the Cash page.
        val usableCashAccounts = cashAccounts.filter { it.active && !it.deleted && !it.hide }
        val historyCashAccounts = cashAccounts.filter { !it.deleted }
        val cashAccountIds = historyCashAccounts.map { it.id }.toSet()
        val allCashTxns = allImportedCashTxns
            .filter { it.accountId in cashAccountIds }
            .sortedWith(compareByDescending<com.moneymanager.app.data.local.entity.TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
        val cashTxns = allCashTxns.filter {
            it.occurredAtEpochMillis >= monthStart && it.occurredAtEpochMillis < monthEndExclusive
        }

        var totalBalance = 0L
        for (account in usableCashAccounts) {
            val accountTxns = allCashTxns.filter { it.accountId == account.id }
            val state = accountingService.ledgerRowsFor(accountTxns, AccountType.CASH)
                .let(com.moneymanager.app.domain.accounting.AccountingEngine::balanceState)
            val calculated = if (account.manualBalanceOverrideMinorUnits != null &&
                account.manualBalanceOverrideAtEpochMillis != null
            ) {
                val postOverride = accountingService.ledgerRowsFor(accountTxns, AccountType.CASH)
                    .asSequence()
                    .filter { it.occurredAtEpochMillis > account.manualBalanceOverrideAtEpochMillis }
                    .sumOf { it.movement.ledgerDeltaMinorUnits }
                account.manualBalanceOverrideMinorUnits + postOverride
            } else {
                state.calculatedBalanceMinorUnits
            }
            totalBalance += calculated
        }

        val spend = cashTxns.filter { it.includeInStatistics && it.debitMinorUnits > it.creditMinorUnits }
            .sumOf { it.debitMinorUnits - it.creditMinorUnits }
        val income = cashTxns.filter { it.includeInStatistics && it.creditMinorUnits > it.debitMinorUnits }
            .sumOf { it.creditMinorUnits - it.debitMinorUnits }

        // Calculate Top Spend Areas from the same imported ledger used for the balance. This
        // avoids a second SQL accounting path getting out of sync with the canonical engine.
        val names = categories.associate { it.id to it.name }
        val categoryTotals = cashTxns
            .asSequence()
            .filter { it.includeInStatistics && it.debitMinorUnits > it.creditMinorUnits }
            .groupBy { it.categoryId }
            .map { (categoryId, txns) ->
                CatSpend(
                    categoryId = categoryId,
                    name = categoryId?.let(names::get) ?: "Uncategorized",
                    total = txns.sumOf { it.debitMinorUnits - it.creditMinorUnits }
                )
            }
            .filter { it.total > 0 }
            .sortedByDescending { it.total }
            .take(6)

        CashUiState(
            cashInHand = Money(totalBalance),
            monthSpend = Money(spend),
            monthIncome = Money(income),
            primaryCashAccountId = usableCashAccounts.firstOrNull()?.id,
            transactions = allCashTxns,
            topCategoryTotals = categoryTotals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashUiState())
}
