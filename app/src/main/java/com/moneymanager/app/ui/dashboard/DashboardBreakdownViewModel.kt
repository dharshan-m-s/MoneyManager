package com.moneymanager.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AccountLine(val account: AccountEntity, val amountMinorUnits: Long)
data class BillLine(val instance: BillInstanceEntity, val bill: BillEntity?)

data class DashboardBreakdownState(
    val kind: DashboardBreakdownKind,
    val periodLabel: String,
    val formula: String,
    val total: Money = Money.ZERO,
    val accountLines: List<AccountLine> = emptyList(),
    val assetLines: List<AccountLine> = emptyList(),
    val liabilityLines: List<AccountLine> = emptyList(),
    val assetTotal: Money = Money.ZERO,
    val liabilityTotal: Money = Money.ZERO,
    val transactions: List<TransactionEntity> = emptyList(),
    val billLines: List<BillLine> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class DashboardBreakdownViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val billDao: BillDao
) : ViewModel() {

    private val period = FinancialSummary.currentMonthPeriod()
    private val periodLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
        .format(FinancialSummary.currentMonthPeriod().let {
            com.moneymanager.app.domain.billing.BillSchedule.dateOf(it.startInclusive)
        })

    private val states = mutableMapOf<DashboardBreakdownKind, StateFlow<DashboardBreakdownState>>()

    fun stateFor(kind: DashboardBreakdownKind): StateFlow<DashboardBreakdownState> =
        states.getOrPut(kind) { createState(kind) }

    private fun createState(kind: DashboardBreakdownKind): StateFlow<DashboardBreakdownState> =
        combine(
            accountDao.observeAll(),
            transactionDao.observeInRange(period.startInclusive, period.endExclusive),
            billDao.observeUpcomingUnpaid(),
            billDao.observeActive()
        ) { accounts, transactions, unpaid, bills ->
            val accountById = FinancialSummary.accountMap(accounts)
            val billsById = bills.associateBy { it.id }
            when (kind) {
                DashboardBreakdownKind.BALANCE -> {
                    val lines = FinancialSummary.balanceAccounts(accounts).map {
                        AccountLine(it, it.currentBalanceMinorUnits)
                    }
                    DashboardBreakdownState(
                        kind = kind,
                        periodLabel = "As of now",
                        formula = "Sum of current balances for active BANK, CASH, WALLET, DEBIT_CARD " +
                            "and PREPAID_CARD accounts. Hidden, deleted, inactive and child accounts " +
                            "are excluded. Investments are excluded because they are not spendable cash.",
                        total = Money(lines.sumOf { it.amountMinorUnits }),
                        accountLines = lines,
                        loading = false
                    )
                }
                DashboardBreakdownKind.NET_WORTH -> {
                    val assets = FinancialSummary.netWorthAssetAccounts(accounts).map {
                        AccountLine(it, it.currentBalanceMinorUnits)
                    }
                    val liabilities = FinancialSummary.liabilityAccounts(accounts).map {
                        AccountLine(it, FinancialSummary.liabilityAmountMinor(it))
                    }
                    val assetTotal = assets.sumOf { it.amountMinorUnits }
                    val liabilityTotal = liabilities.sumOf { it.amountMinorUnits }
                    DashboardBreakdownState(
                        kind = kind,
                        periodLabel = "As of now",
                        formula = "Net worth = assets − liabilities. Assets: active BANK, CASH, " +
                            "WALLET, INVESTMENT, DEBIT_CARD and PREPAID_CARD accounts. Liabilities: " +
                            "credit-card outstanding plus loan balances. Internal transfers are not " +
                            "assets and credit limits are not counted.",
                        total = Money(assetTotal - liabilityTotal),
                        assetLines = assets,
                        liabilityLines = liabilities,
                        assetTotal = Money(assetTotal),
                        liabilityTotal = Money(liabilityTotal),
                        loading = false
                    )
                }
                DashboardBreakdownKind.SPENT -> {
                    val rows = FinancialSummary.spendRows(transactions, accountById, period)
                    DashboardBreakdownState(
                        kind = kind,
                        periodLabel = periodLabel,
                        formula = "Expenses and card purchases for $periodLabel with Count in spending " +
                            "enabled. Transfers, credit-card bill payments and reimbursements are " +
                            "excluded; refunds and reversals reduce the total.",
                        total = Money(rows.sumOf { FinancialSummary.spendAmountMinor(it) }),
                        transactions = rows,
                        loading = false
                    )
                }
                DashboardBreakdownKind.INCOME -> {
                    val rows = FinancialSummary.incomeRows(transactions, accountById, period)
                    DashboardBreakdownState(
                        kind = kind,
                        periodLabel = periodLabel,
                        formula = "Income transactions for $periodLabel with Count in income enabled. " +
                            "Transfers, card payments, refunds and reimbursements are not income.",
                        total = Money(rows.sumOf { FinancialSummary.incomeAmountMinor(it) }),
                        transactions = rows,
                        loading = false
                    )
                }
                DashboardBreakdownKind.BILLS -> {
                    val lines = unpaid
                        .sortedBy { it.dueDateEpochMillis }
                        .map { BillLine(it, billsById[it.billId]) }
                    DashboardBreakdownState(
                        kind = kind,
                        periodLabel = "Unpaid occurrences",
                        formula = "Every generated unpaid bill occurrence, including overdue ones, " +
                            "counted as the amount still remaining after recorded partial payments. " +
                            "Recurring occurrences are created only inside each biller's lead window.",
                        total = Money(lines.sumOf {
                            it.instance.amountDueMinorUnits - it.instance.paidAmountMinorUnits
                        }),
                        billLines = lines,
                        loading = false
                    )
                }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardBreakdownState(kind = kind, periodLabel = "", formula = ""))
}
