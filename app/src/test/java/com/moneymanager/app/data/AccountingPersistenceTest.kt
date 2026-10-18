package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountingPersistenceTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var service: AccountingService

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        service = AccountingService(db.accountDao(), db.transactionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(day: Int): Long =
        com.moneymanager.app.domain.billing.BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private suspend fun insertAccount(type: AccountType, current: Long = 0L): Long =
        db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "test-${type.name}-${System.nanoTime()}",
                institutionName = "Test",
                nickname = "Test ${type.name}",
                accountType = type,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = current,
                creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

    private fun txn(
        accountId: Long,
        credit: Long = 0L,
        debit: Long = 0L,
        occurredAt: Long,
        type: AccountType = AccountType.BANK,
        subType: TxnSubType = TxnSubType.EXPENSE,
        kind: TxnKind = TxnKind.REGULAR
    ) = TransactionEntity(
        occurredAtEpochMillis = occurredAt,
        rawDateString = "",
        txnType = if (credit > 0) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
        txnSubType = subType,
        txnKind = kind,
        paymentType = PaymentType.NETBANKING,
        rawPaymentType = PaymentType.NETBANKING.raw,
        businessPersonal = BusinessPersonal.PERSONAL,
        merchantReceiverSender = "Txn",
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
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0
    )

    /** Simulates AccountEditViewModel saving a deliberate checkpoint. */
    private suspend fun setManualBalance(accountId: Long, entered: Long, now: Long) {
        val account = db.accountDao().findById(accountId)!!
        val derived = service.derivedBalancesFor(account).autoBalanceMinorUnits
        db.accountDao().update(
            account.copy(
                manualBalanceOverrideMinorUnits = entered,
                manualBalanceOverrideAtEpochMillis = now,
                manualBalanceAdjustmentMinorUnits = AccountingEngine.checkpointAdjustment(entered, derived)
            )
        )
        service.recalculateAccounts(setOf(accountId))
    }

    @Test
    fun manualBalanceCheckpointSurvivesBackdatedMovementAndRestart() = runBlocking {
        val accountId = insertAccount(AccountType.BANK)
        db.transactionDao().insert(
            txn(accountId, credit = 1_000, occurredAt = at(5), subType = TxnSubType.INCOME)
        )
        service.recalculateAccounts(setOf(accountId))
        assertEquals(1_000L, db.accountDao().findById(accountId)!!.currentBalanceMinorUnits)

        // User states the bank app shows ₹5,000 on 10 Oct.
        setManualBalance(accountId, entered = 5_000L, now = at(10))
        assertEquals(5_000L, db.accountDao().findById(accountId)!!.currentBalanceMinorUnits)

        // A backdated expense entered later must still move the displayed balance. A naive
        // "occurredAt > checkpoint timestamp" filter would lose it.
        db.transactionDao().insert(txn(accountId, debit = 200, occurredAt = at(2)))
        service.recalculateAccounts(setOf(accountId))
        assertEquals(4_800L, db.accountDao().findById(accountId)!!.currentBalanceMinorUnits)

        // Process restart: a brand-new service instance over the same database.
        val restarted = AccountingService(db.accountDao(), db.transactionDao())
        restarted.recalculateAccounts(setOf(accountId))
        val persisted = db.accountDao().findById(accountId)!!
        assertEquals(4_800L, persisted.currentBalanceMinorUnits)
        assertEquals(5_000L, persisted.manualBalanceOverrideMinorUnits)
        assertEquals(4_000L, persisted.manualBalanceAdjustmentMinorUnits)
    }

    @Test
    fun creditCardOutstandingCheckpointSurvivesRecalculationAndTracksMovements() = runBlocking {
        val cardId = insertAccount(AccountType.CREDIT_CARD)
        // Simulates the credit-card editor: entered outstanding 5,000 against a zero ledger.
        db.accountDao().update(
            db.accountDao().findById(cardId)!!.copy(
                outstandingMinorUnits = 5_000L,
                outstandingCheckpointMinorUnits = 5_000L,
                outstandingCheckpointAtEpochMillis = at(1),
                outstandingAdjustmentMinorUnits = 5_000L
            )
        )
        service.recalculateAccounts(setOf(cardId))
        assertEquals(5_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Purchase increases outstanding.
        db.transactionDao().insert(
            txn(cardId, debit = 1_000, occurredAt = at(2), type = AccountType.CREDIT_CARD)
        )
        service.recalculateAccounts(setOf(cardId))
        assertEquals(6_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Card payment decreases outstanding and is not an ordinary expense.
        db.transactionDao().insert(
            txn(
                cardId, credit = 500, occurredAt = at(3), type = AccountType.CREDIT_CARD,
                subType = TxnSubType.TRANSFER_IN, kind = TxnKind.CC_BILL_PAYMENT
            )
        )
        service.recalculateAccounts(setOf(cardId))
        assertEquals(5_500L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertEquals(94_500L, db.accountDao().findById(cardId)!!.availableLimitMinorUnits)

        // Full recalculation (e.g. startup repair) must not erase the checkpoint.
        service.recalculateAll()
        val afterFull = db.accountDao().findById(cardId)!!
        assertEquals(5_500L, afterFull.outstandingMinorUnits)
        assertEquals(5_000L, afterFull.outstandingCheckpointMinorUnits)

        // Editing the purchase amount adjusts the effective outstanding once.
        val purchase = db.transactionDao().findByAccount(cardId)
            .first { it.debitMinorUnits == 1_000L }
        db.transactionDao().update(purchase.copy(debitMinorUnits = 2_000L))
        service.recalculateAccounts(setOf(cardId))
        assertEquals(6_500L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Deleting the purchase removes exactly its movement. The 5,000 checkpoint and the
        // 500 payment remain: 5,000 + 0 - 500 = 4,500.
        db.transactionDao().deleteById(purchase.id)
        service.recalculateAccounts(setOf(cardId))
        assertEquals(4_500L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
    }

    @Test
    fun legacyCardWithoutCheckpointCapturesVisibleOutstandingExactlyOnce() = runBlocking {
        val cardId = insertAccount(AccountType.CREDIT_CARD)
        // Legacy builds persisted only the visible outstanding value - no checkpoint fields.
        db.accountDao().update(
            db.accountDao().findById(cardId)!!.copy(outstandingMinorUnits = 3_000L)
        )

        service.recalculateAccounts(setOf(cardId))
        val captured = db.accountDao().findById(cardId)!!
        assertEquals(3_000L, captured.outstandingMinorUnits)
        assertEquals(3_000L, captured.outstandingAdjustmentMinorUnits)
        assertEquals(3_000L, captured.outstandingCheckpointMinorUnits)

        // A second full recalculation must not re-capture: the offset is taken once from the
        // existing visible value, never compounded from it again.
        service.recalculateAll()
        assertEquals(3_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Movements still apply on top of the captured checkpoint.
        db.transactionDao().insert(
            txn(cardId, debit = 500, occurredAt = at(6), type = AccountType.CREDIT_CARD)
        )
        service.recalculateAccounts(setOf(cardId))
        assertEquals(3_500L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertEquals(3_000L, db.accountDao().findById(cardId)!!.outstandingCheckpointMinorUnits)
    }
}
