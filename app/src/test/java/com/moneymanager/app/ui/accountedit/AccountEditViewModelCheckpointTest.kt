package com.moneymanager.app.ui.accountedit

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import com.moneymanager.app.data.accountingService
import com.moneymanager.app.data.billRecurrenceService

/**
 * Regression tests for the durable manual-balance checkpoint as driven through the REAL
 * [AccountEditViewModel]. Mirrors the credit-card guarantees at the account-edit layer:
 *  - reopening the editor and saving without touching the balance never resets the checkpoint;
 *  - a deliberate balance edit rebases the checkpoint exactly once;
 *  - backdated movements still apply on top of the checkpoint;
 *  - turning manual mode off returns the account to the engine-derived balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountEditViewModelCheckpointTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var service: AccountingService
    private lateinit var viewModel: AccountEditViewModel

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        service = db.accountingService()
        viewModel = AccountEditViewModel(
            db.accountDao(),
            service,
            BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao()),
            db
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private fun at(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description; state=${viewModel.state.value}")
            }
            Thread.sleep(5)
        }
    }

    /** A bank account with ₹10 of ledger history, checkpointed to show ₹5,000. */
    private suspend fun insertCheckpointedBankAccount(): Long {
        val accountId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "ae-${System.nanoTime()}",
                institutionName = "Test Bank",
                nickname = "Test Bank",
                accountType = AccountType.BANK,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 500_000L,
                manualBalanceOverrideMinorUnits = 500_000L,
                manualBalanceOverrideAtEpochMillis = at(1),
                // derived = 1_000 paise (the credit below); entered = 500_000 paise.
                manualBalanceAdjustmentMinorUnits = 499_000L,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(2),
                rawDateString = "",
                txnType = TxnType.CREDIT_TRANSACTION,
                txnSubType = TxnSubType.INCOME,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.NETBANKING,
                rawPaymentType = PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Salary",
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 1_000L,
                debitMinorUnits = 0L,
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
        )
        service.recalculateAccounts(setOf(accountId))
        assertEquals(500_000L, db.accountDao().findById(accountId)!!.currentBalanceMinorUnits)
        return accountId
    }

    @Test
    fun reopeningAndSavingWithoutTouchingBalancePreservesCheckpoint() = runBlocking {
        val accountId = insertCheckpointedBankAccount()

        viewModel.load(accountId)
        pumpUntil("form loaded") { viewModel.state.value.accountId == accountId }
        assertFalse(viewModel.state.value.balanceDirty)
        assertTrue(viewModel.state.value.manualBalanceEnabled)

        viewModel.nickname("Renamed Bank")
        viewModel.save()
        pumpUntil("save completes") { viewModel.state.value.saved }

        val persisted = db.accountDao().findById(accountId)!!
        assertEquals("Renamed Bank", persisted.nickname)
        assertEquals(500_000L, persisted.manualBalanceOverrideMinorUnits)
        assertEquals(499_000L, persisted.manualBalanceAdjustmentMinorUnits)
        assertEquals(500_000L, persisted.currentBalanceMinorUnits)
    }

    @Test
    fun deliberateBalanceEditRebasesCheckpointAndBackdatedMovementsApply() = runBlocking {
        val accountId = insertCheckpointedBankAccount()

        viewModel.load(accountId)
        pumpUntil("form loaded") { viewModel.state.value.accountId == accountId }
        viewModel.currentBalance("6000") // ₹6,000 - a deliberate reconciliation
        assertTrue(viewModel.state.value.balanceDirty)
        viewModel.save()
        pumpUntil("save completes") { viewModel.state.value.saved }

        val afterEdit = db.accountDao().findById(accountId)!!
        assertEquals(600_000L, afterEdit.manualBalanceOverrideMinorUnits)
        assertEquals(599_000L, afterEdit.manualBalanceAdjustmentMinorUnits)
        assertEquals(600_000L, afterEdit.currentBalanceMinorUnits)

        // A backdated expense must still move the displayed balance (no timestamp filter).
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(1), // BEFORE the other ledger row
                rawDateString = "",
                txnType = TxnType.DEBIT_TRANSACTION,
                txnSubType = TxnSubType.EXPENSE,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.NETBANKING,
                rawPaymentType = PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Merchant",
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 0L,
                debitMinorUnits = 200L,
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
        )
        service.recalculateAccounts(setOf(accountId))
        assertEquals(599_800L, db.accountDao().findById(accountId)!!.currentBalanceMinorUnits)

        // Restart-equivalent: a fresh service instance recalculates to the same value.
        db.accountingService()
            .recalculateAccounts(setOf(accountId))
        val persisted = db.accountDao().findById(accountId)!!
        assertEquals(599_800L, persisted.currentBalanceMinorUnits)
        assertEquals(600_000L, persisted.manualBalanceOverrideMinorUnits)
    }

    @Test
    fun turningManualModeOffReturnsToEngineDerivedBalance() = runBlocking {
        val accountId = insertCheckpointedBankAccount()

        viewModel.load(accountId)
        pumpUntil("form loaded") { viewModel.state.value.accountId == accountId }
        viewModel.manualBalanceEnabled(false)
        viewModel.save()
        pumpUntil("save completes") { viewModel.state.value.saved }

        val persisted = db.accountDao().findById(accountId)!!
        assertNull(persisted.manualBalanceOverrideMinorUnits)
        assertNull(persisted.manualBalanceAdjustmentMinorUnits)
        // Without a checkpoint the effective balance is the engine-derived ledger value.
        assertEquals(1_000L, persisted.currentBalanceMinorUnits)
    }

    @Test
    fun visibilityOnlyEditsNeverResetTheCheckpoint() = runBlocking {
        val accountId = insertCheckpointedBankAccount()

        viewModel.load(accountId)
        pumpUntil("form loaded") { viewModel.state.value.accountId == accountId }
        viewModel.active(true)
        viewModel.hide(false)
        viewModel.hideTransactions(false)
        viewModel.save()
        pumpUntil("save completes") { viewModel.state.value.saved }

        val persisted = db.accountDao().findById(accountId)!!
        assertEquals(500_000L, persisted.manualBalanceOverrideMinorUnits)
        assertEquals(499_000L, persisted.manualBalanceAdjustmentMinorUnits)
        assertEquals(500_000L, persisted.currentBalanceMinorUnits)
        assertTrue(persisted.active)
        assertFalse(persisted.hide)
        assertFalse(persisted.hideAccountTxns)
    }

    /**
     * Changing the account type is a metadata edit. It must never wipe a durable
     * reconciliation checkpoint, even though the type change re-runs the balance path.
     */
    @Test
    fun changingAccountTypePreservesTheCheckpoint() = runBlocking {
        val accountId = insertCheckpointedBankAccount()

        viewModel.load(accountId)
        pumpUntil("form loaded") { viewModel.state.value.accountId == accountId }
        viewModel.accountType(AccountType.WALLET)
        viewModel.save()
        pumpUntil("save completes") { viewModel.state.value.saved }

        val persisted = db.accountDao().findById(accountId)!!
        assertEquals(AccountType.WALLET, persisted.accountType)
        assertEquals(500_000L, persisted.manualBalanceOverrideMinorUnits)
        assertEquals(499_000L, persisted.manualBalanceAdjustmentMinorUnits)
        assertEquals(500_000L, persisted.currentBalanceMinorUnits)
        assertNull(viewModel.state.value.error)
    }
}
