package com.moneymanager.app.ui.dashboard

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import com.moneymanager.app.domain.reporting.FinancialSummary
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every dashboard tile must reconcile EXACTLY with its drill-down screen:
 * same FinancialSummary helper, same period, same rows. Seeded fixtures also pin the
 * spec's expected values so tile and drill-down cannot drift together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardReconciliationTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var breakdownViewModel: DashboardBreakdownViewModel

    private var bankId: Long = -1L
    private var cashId: Long = -1L
    private var investmentId: Long = -1L
    private var cardId: Long = -1L
    private var loanId: Long = -1L
    private var hiddenBankId: Long = -1L
    private var childCashId: Long = -1L
    private var deletedCashId: Long = -1L
    private var allAccounts: List<AccountEntity> = emptyList()

    private val period = FinancialSummary.currentMonthPeriod()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking { seed() }
        dashboardViewModel = DashboardViewModel(
            db.accountDao(), db.transactionDao(), db.billDao(), db.budgetDao(), db.categoryDao()
        )
        breakdownViewModel = DashboardBreakdownViewModel(
            db.accountDao(), db.transactionDao(), db.billDao()
        )
        startCollectors()
    }

    /** The ViewModels expose WhileSubscribed state flows; upstream only runs while somebody
     *  collects. Tests subscribe exactly like Compose does in production. */
    private fun startCollectors() {
        dashboardViewModel.viewModelScope.launch { dashboardViewModel.uiState.collect {} }
        for (kind in DashboardBreakdownKind.entries) {
            breakdownViewModel.viewModelScope.launch { breakdownViewModel.stateFor(kind).collect {} }
        }
    }

    @After
    fun tearDown() {
        dashboardViewModel.viewModelScope.cancel()
        breakdownViewModel.viewModelScope.cancel()
        db.close()
    }

    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description")
            }
            Thread.sleep(5)
        }
    }

    private suspend fun account(
        nickname: String,
        type: AccountType,
        balance: Long,
        active: Boolean = true,
        deleted: Boolean = false,
        hide: Boolean = false,
        hideAccountTxns: Boolean = false,
        parentAccountId: Long? = null,
        outstanding: Long? = null
    ): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "rec-${System.nanoTime()}-$nickname",
            institutionName = nickname,
            nickname = nickname,
            accountType = type,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = balance,
            outstandingMinorUnits = outstanding,
            active = active,
            deleted = deleted,
            hide = hide,
            hideAccountTxns = hideAccountTxns,
            parentAccountId = parentAccountId,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private fun txn(
        accountId: Long,
        occurredAt: Long,
        subType: TxnSubType,
        kind: TxnKind = TxnKind.REGULAR,
        credit: Long = 0L,
        debit: Long = 0L,
        includeInStatistics: Boolean = true
    ) = TransactionEntity(
        occurredAtEpochMillis = occurredAt,
        rawDateString = "",
        txnType = if (credit != 0L) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
        txnSubType = subType,
        txnKind = kind,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = "Merchant",
        categoryId = null,
        rawCategoryName = null,
        accountId = accountId,
        creditMinorUnits = credit,
        debitMinorUnits = debit,
        balanceSnapshotMinorUnits = null,
        outstandingSnapshotMinorUnits = null,
        availableLimitSnapshotMinorUnits = null,
        notes = null,
        reimbursable = false,
        reimbursed = false,
        isHistoricalImport = false,
        dedupeFingerprint = "fp-${System.nanoTime()}",
        includeInStatistics = includeInStatistics,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0
    )

    private suspend fun seed() {
        bankId = account("Bank A", AccountType.BANK, 500_000L)
        cashId = account("Cash", AccountType.CASH, 100_000L)
        investmentId = account("Mutual Funds", AccountType.INVESTMENT, 300_000L)
        cardId = account("Credit Card", AccountType.CREDIT_CARD, 123_456L, outstanding = 400_000L)
        loanId = account("Home Loan", AccountType.LOAN, 150_000L)
        hiddenBankId = account("Hidden Bank", AccountType.BANK, 999_999L, hide = true)
        childCashId = account("Linked Cash", AccountType.CASH, 888_888L, parentAccountId = bankId)
        deletedCashId = account("Deleted Cash", AccountType.CASH, 777_777L, deleted = true)
        account("Inactive Wallet", AccountType.WALLET, 666_666L, active = false)
        account("Stats Off Cash", AccountType.CASH, 555_555L, hideAccountTxns = true)

        val t = period.startInclusive + 3 * 3_600_000L // 03:00 on the 1st, in-period
        db.transactionDao().insert(txn(bankId, t, TxnSubType.EXPENSE, debit = 20_000L))
        db.transactionDao().insert(txn(bankId, t + 1_000L, TxnSubType.INCOME, credit = 500_000L))
        db.transactionDao().insert(txn(cardId, t + 2_000L, TxnSubType.EXPENSE, debit = 15_000L))
        db.transactionDao().insert(txn(bankId, t + 3_000L, TxnSubType.TRANSFER_OUT, debit = 10_000L))
        db.transactionDao().insert(txn(bankId, t + 4_000L, TxnSubType.EXPENSE, debit = 5_000L, includeInStatistics = false))
        db.transactionDao().insert(txn(hiddenBankId, t + 5_000L, TxnSubType.EXPENSE, debit = 7_000L))
        db.transactionDao().insert(txn(bankId, t + 6_000L, TxnSubType.EXPENSE, kind = TxnKind.REFUND_REVERSAL, credit = 3_000L))

        val activeBillId = db.billDao().insert(
            BillEntity(
                billerName = "Electricity",
                billerType = BillerType.ELECTRICITY,
                accountReferenceId = null,
                nickname = "Electricity",
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = period.startInclusive + 2 * 86_400_000L,
                estimatedAmountMinorUnits = 12_000L,
                active = true,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val inactiveBillId = db.billDao().insert(
            BillEntity(
                billerName = "Broadband",
                billerType = BillerType.BROADBAND_WIFI,
                accountReferenceId = null,
                nickname = "Broadband",
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = period.startInclusive + 4 * 86_400_000L,
                estimatedAmountMinorUnits = 8_000L,
                active = false,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        db.billDao().insertInstance(
            BillInstanceEntity(
                billId = activeBillId,
                dueDateEpochMillis = period.startInclusive + 2 * 86_400_000L,
                amountDueMinorUnits = 12_000L,
                paid = false
            )
        )
        db.billDao().insertInstance(
            BillInstanceEntity(
                billId = activeBillId,
                dueDateEpochMillis = period.startInclusive - 30 * 86_400_000L,
                amountDueMinorUnits = 9_000L,
                paid = true,
                paidAtEpochMillis = period.startInclusive
            )
        )
        db.billDao().insertInstance(
            BillInstanceEntity(
                billId = inactiveBillId,
                dueDateEpochMillis = period.startInclusive + 4 * 86_400_000L,
                amountDueMinorUnits = 8_000L,
                paid = false
            )
        )
        allAccounts = db.accountDao().findAll()
    }

    @Test
    fun everyTileReconcilesExactlyWithItsDrillDown() {
        pumpUntil("dashboard ui") { dashboardViewModel.uiState.value != null }
        val ui = dashboardViewModel.uiState.value!!

        pumpUntil("balance breakdown") {
            breakdownViewModel.stateFor(DashboardBreakdownKind.BALANCE).value.loading.not() &&
                breakdownViewModel.stateFor(DashboardBreakdownKind.BALANCE).value.accountLines.isNotEmpty()
        }
        pumpUntil("net worth breakdown") {
            !breakdownViewModel.stateFor(DashboardBreakdownKind.NET_WORTH).value.loading
        }
        pumpUntil("spent breakdown") {
            !breakdownViewModel.stateFor(DashboardBreakdownKind.SPENT).value.loading
        }
        pumpUntil("income breakdown") {
            !breakdownViewModel.stateFor(DashboardBreakdownKind.INCOME).value.loading
        }
        pumpUntil("bills breakdown") {
            !breakdownViewModel.stateFor(DashboardBreakdownKind.BILLS).value.loading
        }

        // --- Available tile vs BALANCE drill-down (spec:600_000 = bank 500k + cash 100k).
        val balanceBd = breakdownViewModel.stateFor(DashboardBreakdownKind.BALANCE).value
        assertEquals(Money(600_000L), ui.bankBalance)
        assertEquals(ui.bankBalance, balanceBd.total)
        assertEquals(600_000L, balanceBd.accountLines.sumOf { it.amountMinorUnits })
        assertEquals(2, balanceBd.accountLines.size)
        assertTrue(balanceBd.accountLines.none {
            it.account.id in setOf(hiddenBankId, childCashId, deletedCashId, investmentId)
        })
        assertTrue(balanceBd.formula.isNotBlank())
        assertEquals("As of now", balanceBd.periodLabel)

        // --- Net worth tile vs NET_WORTH drill-down (assets 900k − liabilities 550k).
        val nwBd = breakdownViewModel.stateFor(DashboardBreakdownKind.NET_WORTH).value
        assertEquals(Money(350_000L), ui.netWorth)
        assertEquals(ui.netWorth, nwBd.total)
        assertEquals(900_000L, nwBd.assetTotal.minorUnits)
        assertEquals(550_000L, nwBd.liabilityTotal.minorUnits)
        assertEquals(nwBd.assetTotal.minorUnits - nwBd.liabilityTotal.minorUnits, nwBd.total.minorUnits)
        assertEquals(Money(400_000L), ui.creditCardOutstanding)
        assertTrue(nwBd.assetLines.none { it.account.id in setOf(hiddenBankId, childCashId, deletedCashId) })
        assertTrue(nwBd.liabilityLines.none { it.account.id in setOf(hiddenBankId, childCashId, deletedCashId) })

        // --- Spent tile vs SPENT drill-down (20k expense + 15k card purchase − 3k refund).
        val spentBd = breakdownViewModel.stateFor(DashboardBreakdownKind.SPENT).value
        assertEquals(Money(32_000L), ui.monthSpend)
        assertEquals(ui.monthSpend, spentBd.total)
        assertEquals(32_000L, spentBd.transactions.sumOf { FinancialSummary.spendAmountMinor(it) })
        assertEquals(3, spentBd.transactions.size)
        assertTrue(spentBd.transactions.all { FinancialSummary.isSpendRow(it, FinancialSummary.accountMap(allAccounts)) })
        assertTrue(spentBd.formula.isNotBlank())
        assertTrue(spentBd.periodLabel.isNotBlank())

        // --- Income tile vs INCOME drill-down (500k only; transfers/refunds not income).
        val incomeBd = breakdownViewModel.stateFor(DashboardBreakdownKind.INCOME).value
        assertEquals(Money(500_000L), ui.monthIncome)
        assertEquals(ui.monthIncome, incomeBd.total)
        assertEquals(500_000L, incomeBd.transactions.sumOf { FinancialSummary.incomeAmountMinor(it) })
        assertEquals(1, incomeBd.transactions.size)

        // --- Upcoming bills tile vs BILLS drill-down (unpaid active-biller only).
        val billsBd = breakdownViewModel.stateFor(DashboardBreakdownKind.BILLS).value
        assertEquals(Money(12_000L), ui.upcomingBillsTotal)
        assertEquals(1, ui.upcomingBillsCount)
        assertEquals(ui.upcomingBillsTotal, billsBd.total)
        assertEquals(12_000L, billsBd.billLines.sumOf { it.instance.amountDueMinorUnits })
        assertTrue(billsBd.billLines.all { !it.instance.paid && it.bill?.active == true })

        // --- Category tiles on the dashboard derive from the same spend rows as SPENT.
        val categorySum = ui.topCategoryTotals.sumOf { it.total }
        assertEquals(ui.monthSpend.minorUnits, categorySum)
    }

    @Test
    fun emptyStateReconcilesToZeroEverywhere() {
        // A fresh account set with no transactions: every tile and drill-down must be zero.
        pumpUntil("dashboard ui") { dashboardViewModel.uiState.value != null }
        // Drain any leftover state from the seeded fixture by using fresh VMs on a fresh DB.
        val context = RuntimeEnvironment.getApplication()
        val freshDb = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dash = DashboardViewModel(
                freshDb.accountDao(), freshDb.transactionDao(), freshDb.billDao(),
                freshDb.budgetDao(), freshDb.categoryDao()
            )
            val bd = DashboardBreakdownViewModel(freshDb.accountDao(), freshDb.transactionDao(), freshDb.billDao())
            dash.viewModelScope.launch { dash.uiState.collect {} }
            for (kind in DashboardBreakdownKind.entries) {
                bd.viewModelScope.launch { bd.stateFor(kind).collect {} }
            }
            pumpUntil("fresh dashboard") { dash.uiState.value != null }
            pumpUntil("fresh balance breakdown") { !bd.stateFor(DashboardBreakdownKind.BALANCE).value.loading }
            pumpUntil("fresh spent breakdown") { !bd.stateFor(DashboardBreakdownKind.SPENT).value.loading }
            pumpUntil("fresh bills breakdown") { !bd.stateFor(DashboardBreakdownKind.BILLS).value.loading }
            pumpUntil("fresh net worth") { !bd.stateFor(DashboardBreakdownKind.NET_WORTH).value.loading }
            pumpUntil("fresh income") { !bd.stateFor(DashboardBreakdownKind.INCOME).value.loading }

            val ui = dash.uiState.value!!
            assertEquals(Money.ZERO, ui.bankBalance)
            assertEquals(Money.ZERO, ui.netWorth)
            assertEquals(Money.ZERO, ui.monthSpend)
            assertEquals(Money.ZERO, ui.monthIncome)
            assertEquals(Money.ZERO, ui.upcomingBillsTotal)
            assertEquals(Money.ZERO, bd.stateFor(DashboardBreakdownKind.BALANCE).value.total)
            assertEquals(Money.ZERO, bd.stateFor(DashboardBreakdownKind.SPENT).value.total)
            assertEquals(Money.ZERO, bd.stateFor(DashboardBreakdownKind.INCOME).value.total)
            assertEquals(Money.ZERO, bd.stateFor(DashboardBreakdownKind.NET_WORTH).value.total)
            assertEquals(Money.ZERO, bd.stateFor(DashboardBreakdownKind.BILLS).value.total)
            assertFalse(ui.hasAnyData)
            dash.viewModelScope.cancel()
            bd.viewModelScope.cancel()
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun safeToSpendWithoutBudgetIsCashAfterScheduledBills() {
        pumpUntil("dashboard ui") { dashboardViewModel.uiState.value != null }
        val ui = dashboardViewModel.uiState.value!!
        // No budget is seeded: Safe to Spend must be available cash minus the remaining amount
        // owed on scheduled bills, and it must say so.
        assertEquals(SafeToSpendMode.CASH_AFTER_BILLS, ui.safeToSpendMode)
        assertEquals(Money(0L), ui.budget)
        assertEquals(ui.bankBalance.minorUnits - ui.upcomingBillsTotal.minorUnits, ui.safeToSpend.minorUnits)
        assertEquals(600_000L - 12_000L, ui.safeToSpend.minorUnits)
    }

    @Test
    fun safeToSpendWithBudgetIsRemainingBudgetNotCashMinusSpendTwice() {
        // Give the current month a budget, then verify Safe to Spend is budget − spend and the
        // mode flips accordingly (spending must not be subtracted from cash a second time).
        val month = com.moneymanager.app.domain.billing.BillSchedule.dateOf(period.startInclusive)
        runBlocking {
            db.budgetDao().upsertForMonth(
                year = month.year,
                month = month.monthValue,
                amount = 100_000L,
                now = System.currentTimeMillis()
            )
        }
        pumpUntil("dashboard ui with budget") { dashboardViewModel.uiState.value?.budget?.minorUnits == 100_000L }
        val ui = dashboardViewModel.uiState.value!!
        assertEquals(SafeToSpendMode.BUDGET_REMAINING, ui.safeToSpendMode)
        assertEquals(Money(100_000L), ui.budget)
        // Spent so far this month is 32,000 from the seeded fixture.
        assertEquals(100_000L - 32_000L, ui.safeToSpend.minorUnits)
    }
}
