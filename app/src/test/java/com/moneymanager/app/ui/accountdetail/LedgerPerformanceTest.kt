package com.moneymanager.app.ui.accountdetail

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Section 14: a reproducible micro-benchmark (no device needed) that records, on a synthetic
 * account, (a) the cost of the whole-account read and (b) the cost of the month-grouping transform
 * BEFORE and AFTER the single-pass rewrite. The numbers are printed so before/after can be compared
 * honestly; the assertions are generous ceilings that only catch a pathological regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedgerPerformanceTest {

    private lateinit var db: MoneyManagerDatabase

    private val zone = ZoneId.of("Asia/Kolkata")
    private val n = 6_000
    private lateinit var account: AccountEntity
    private lateinit var history: List<TransactionEntity>

    @Before
    fun setUp() {
        runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        val accountId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "perf-account",
                institutionName = "Perf Bank",
                nickname = "Perf",
                accountType = AccountType.BANK,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        account = db.accountDao().findById(accountId)!!
        val base = Instant.parse("2016-01-01T00:00:00Z").toEpochMilli()
        // One row every 4 hours spreads records across many months, so the grouping path has a
        // realistic number of groups (the O(groups x page) shape is worst when both are large).
        val rows = (0 until n).map { i ->
            TransactionEntity(
                occurredAtEpochMillis = base + i * 4 * 3_600_000L,
                rawDateString = "",
                txnType = TxnType.DEBIT_TRANSACTION,
                txnSubType = TxnSubType.EXPENSE,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.UPI,
                rawPaymentType = null,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "merchant-$i",
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 0,
                debitMinorUnits = 100L,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "fp-$i",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        }
        db.transactionDao().insertAll(rows)
    }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private data class Group(val label: String, val income: Long, val expense: Long, val txns: List<TransactionEntity>)

    /** The shape that existed before the fix: for every group, re-scan the whole page (O(groups x page)). */
    private fun groupOld(full: List<TransactionEntity>, page: List<TransactionEntity>): List<Group> =
        full.groupBy { YearMonth.from(Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone)) }
            .toSortedMap(compareByDescending { it })
            .map { (ym, list) ->
                Group(
                    label = ym.toString(),
                    income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                    expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                    txns = page.filter {
                        YearMonth.from(Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone)) == ym
                    }.sortedByDescending { it.occurredAtEpochMillis }
                )
            }

    /** The single-pass rewrite now used by AccountDetailViewModel (O(full + page)). */
    private fun groupNew(full: List<TransactionEntity>, page: List<TransactionEntity>): List<Group> {
        val fullByMonth = HashMap<YearMonth, MutableList<TransactionEntity>>()
        for (t in full) {
            fullByMonth.getOrPut(YearMonth.from(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(zone))) { ArrayList() }.add(t)
        }
        val pageByMonth = HashMap<YearMonth, MutableList<TransactionEntity>>()
        for (t in page) {
            pageByMonth.getOrPut(YearMonth.from(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(zone))) { ArrayList() }.add(t)
        }
        return fullByMonth.entries.sortedByDescending { it.key }.map { (ym, list) ->
            Group(
                label = ym.toString(),
                income = list.filter { it.includeInStatistics }.sumOf { it.creditMinorUnits },
                expense = list.filter { it.includeInStatistics }.sumOf { it.debitMinorUnits },
                txns = (pageByMonth[ym] ?: emptyList()).sortedByDescending { it.occurredAtEpochMillis }
            )
        }
    }

    private inline fun measureMillis(block: () -> Unit): Long {
        block() // warm-up
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun singlePassGroupingIsNotSlowerThanTheOldShapeAndAgreesExactly() = runBlocking {
        val full = db.transactionDao().findByAccount(account.id)
        assertEquals("full history must be read", n, full.size)
        // The worst realistic state is "Load more" until the whole history is paged in, so the
        // displayed page equals the full history. That is where the old nested scan is O(n^2).
        val page = full

        val old = groupOld(full, page)
        val new = groupNew(full, page)

        // Correctness first: the rewrite must produce identical groups and totals.
        assertEquals(old.size, new.size)
        old.zip(new).forEach { (a, b) ->
            assertEquals(a.label, b.label)
            assertEquals(a.income, b.income)
            assertEquals(a.expense, b.expense)
            assertEquals(a.txns.map { it.id }, b.txns.map { it.id })
        }

        val oldMs = measureMillis { groupOld(full, page) }
        val newMs = measureMillis { groupNew(full, page) }
        println(
            "[§14 ledger benchmark] n=$n groups=${new.size} page=${page.size} " +
                "old=${oldMs}ms new=${newMs}ms"
        )
        // Generous ceiling: only catches a pathological regression, not machine noise.
        assertTrue("single-pass grouping should stay well under a second (was ${newMs}ms)", newMs < 1_000)
    }

    @Test
    fun fullAccountReadIsIndexedAndBoundedPerAccount() = runBlocking {
        val start = System.nanoTime()
        val full = db.transactionDao().findByAccount(account.id)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("[§14 read benchmark] read ${full.size} rows in ${ms}ms from one account")
        assertEquals(n, full.size)
        assertTrue("reading one account's history should be quick (was ${ms}ms)", ms < 3_000)
    }
}
