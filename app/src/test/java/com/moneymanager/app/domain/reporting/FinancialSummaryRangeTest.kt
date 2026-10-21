package com.moneymanager.app.domain.reporting

import android.os.Looper
import androidx.room.Room
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth

/**
 * The SQL analytics queries and [FinancialSummary] must agree on the period boundary. Every
 * range query is start-inclusive/end-exclusive (`>= start AND < end`); a row stamped exactly at
 * the next month's 00:00 IST must belong to exactly one month. These tests pin that contract,
 * plus parity between the SQL spend total and the in-memory FinancialSummary total.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FinancialSummaryRangeTest {

    private lateinit var db: MoneyManagerDatabase
    private var bankId: Long = -1L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MoneyManagerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { bankId = insertAccount() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "range-bank",
            institutionName = "Bank",
            nickname = "Bank",
            accountType = AccountType.BANK,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private suspend fun insertExpense(occurredAt: Long, amount: Long) = db.transactionDao().insert(
        TransactionEntity(
            occurredAtEpochMillis = occurredAt,
            rawDateString = "",
            txnType = TxnType.DEBIT_TRANSACTION,
            txnSubType = TxnSubType.EXPENSE,
            txnKind = TxnKind.REGULAR,
            paymentType = PaymentType.CASH,
            rawPaymentType = PaymentType.CASH.raw,
            businessPersonal = BusinessPersonal.PERSONAL,
            merchantReceiverSender = "Shop",
            categoryId = null,
            rawCategoryName = null,
            accountId = bankId,
            creditMinorUnits = 0,
            debitMinorUnits = amount,
            balanceSnapshotMinorUnits = null,
            outstandingSnapshotMinorUnits = null,
            availableLimitSnapshotMinorUnits = null,
            notes = null,
            reimbursable = false,
            reimbursed = false,
            isHistoricalImport = false,
            dedupeFingerprint = "fp-range-${System.nanoTime()}",
            includeInStatistics = true,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    /** Epoch millis of the first instant of a given month in Asia/Kolkata. */
    private fun monthStart(year: Int, month: Int): Long =
        YearMonth.of(year, month).atDay(1).atStartOfDay(FinancialSummary.zone).toInstant().toEpochMilli()

    @Test
    fun boundaryInstantBelongsToExactlyOneMonth() = runBlocking {
        val juneStart = monthStart(2025, 6)
        val julyStart = monthStart(2025, 7)
        val augustStart = monthStart(2025, 8)

        // Exactly one minute before July starts: June.
        insertExpense(julyStart - 60_000L, 1_000L)
        // Exactly at July 00:00:00.000 IST: July, not June.
        insertExpense(julyStart, 2_000L)
        // A normal July row.
        insertExpense(julyStart + 3_600_000L, 4_000L)

        val juneTotal = db.transactionDao().observeTotalSpendInRange(juneStart, julyStart).first()
        val julyTotal = db.transactionDao().observeTotalSpendInRange(julyStart, augustStart).first()

        assertEquals("June must include only the pre-boundary row", 1_000L, juneTotal)
        assertEquals("July must include the boundary instant and the mid-month row", 6_000L, julyTotal)
    }

    @Test
    fun sqlSpendTotalMatchesFinancialSummaryForTheSameWindow() = runBlocking {
        val monthStart = monthStart(2025, 6)
        val monthEnd = monthStart(2025, 7)
        insertExpense(monthStart + 1_000L, 1_500L)
        insertExpense(monthStart + 2_000L, 2_500L)
        // Outside the window - must be excluded by both.
        insertExpense(monthEnd, 9_999L)

        val sqlTotal = db.transactionDao().observeTotalSpendInRange(monthStart, monthEnd).first()

        val accounts = db.accountDao().observeAll().first()
        val txns = db.transactionDao().findInRange(monthStart, monthEnd)
        val period = FinancialSummary.Period(monthStart, monthEnd)
        val memoryTotal = FinancialSummary.spendTotalMinor(txns, FinancialSummary.accountMap(accounts), period)

        assertEquals(memoryTotal, sqlTotal)
        assertEquals(4_000L, sqlTotal)
    }

    @Test
    fun categoryTotalsSumEqualsTotalSpendForTheWindow() = runBlocking {
        val monthStart = monthStart(2025, 6)
        val monthEnd = monthStart(2025, 7)
        insertExpense(monthStart + 1_000L, 1_500L)
        insertExpense(monthStart + 2_000L, 2_500L)

        val total = db.transactionDao().observeTotalSpendInRange(monthStart, monthEnd).first()
        val categorySum = db.transactionDao().observeCategoryTotalsInRange(monthStart, monthEnd).first()
            .sumOf { it.total }

        assertEquals("Category breakdown must reconcile with the month total", total, categorySum)
    }
}
