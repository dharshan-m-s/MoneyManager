package com.moneymanager.app.ui.dashboard

import androidx.core.content.edit
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardSnapshotStoreTest {

    private lateinit var store: DashboardSnapshotStore

    @Before
    fun setUp() {
        store = DashboardSnapshotStore(RuntimeEnvironment.getApplication()).also { it.clear() }
    }

    @Test
    fun `saved snapshot restores every dashboard field`() {
        val state = sampleState()
        store.save("period-1", state)

        assertEquals(state, store.load("period-1"))
    }

    @Test
    fun `snapshot from another period is ignored`() {
        store.save("period-1", sampleState())

        assertNull(store.load("period-2"))
    }

    @Test
    fun `clear removes the snapshot`() {
        store.save("period-1", sampleState())
        store.clear()

        assertNull(store.load("period-1"))
    }

    @Test
    fun `corrupt snapshot is ignored instead of crashing`() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("moneymanager_dashboard_snapshot", 0)
            .edit { putString("snapshot", "{not-json") }

        assertNull(store.load("period-1"))
    }

    private fun sampleState() = DashboardUiState(
        bankBalance = Money(123_456L),
        netWorth = Money(654_321L),
        creditCardOutstanding = Money(9_000L),
        monthSpend = Money(12_345L),
        monthIncome = Money(50_000L),
        upcomingBillsTotal = Money(2_500L),
        upcomingBillsCount = 3,
        safeToSpend = Money(7_700L),
        safeToSpendMode = SafeToSpendMode.CASH_AFTER_BILLS,
        budget = Money(20_000L),
        budgetProgress = 0.61f,
        cashBalance = Money(10_000L),
        cashSpend = Money(1_200L),
        cashWithdrawn = Money(3_400L),
        recentTransactions = listOf(sampleTransaction()),
        topCategoryTotals = listOf(CategoryTotal(7L, 12_345L, "Groceries")),
        categoryNames = mapOf(7L to "Groceries"),
        hasAnyData = true
    )

    private fun sampleTransaction() = TransactionEntity(
        occurredAtEpochMillis = 1_700_000_000_000L,
        rawDateString = "2026/Nov/15 10:00:00",
        txnType = TxnType.DEBIT_TRANSACTION,
        txnSubType = TxnSubType.EXPENSE,
        txnKind = TxnKind.REGULAR,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = "Shop",
        categoryId = 7L,
        rawCategoryName = "Groceries",
        accountId = 1L,
        creditMinorUnits = 0L,
        debitMinorUnits = 12_345L,
        balanceSnapshotMinorUnits = null,
        outstandingSnapshotMinorUnits = null,
        availableLimitSnapshotMinorUnits = null,
        notes = null,
        reimbursable = false,
        reimbursed = false,
        isHistoricalImport = false,
        dedupeFingerprint = "fp-test",
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )
}
