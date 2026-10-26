package com.moneymanager.app.ui.creditcardform

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.AccountRepository
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
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
import com.moneymanager.app.data.creditCardBillingService
import com.moneymanager.app.data.statementService

/**
 * Regression tests for the durable credit-card outstanding checkpoint as driven through the
 * REAL [CreditCardFormViewModel] (real Room DAOs, real AccountingService, real BackupManager).
 *
 * The core invariants:
 *  - Reopening a card and saving without touching the outstanding field must NOT reset or
 *    rebase the durable checkpoint ([CreditCardFormState.outstandingEdited] guard).
 *  - A deliberate outstanding edit rebases the checkpoint exactly once.
 *  - Purchases and payments keep moving the effective outstanding on top of the checkpoint,
 *    and a full recalculation never erases it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreditCardFormCheckpointTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var service: AccountingService
    private lateinit var viewModel: CreditCardFormViewModel
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(
            context,
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        service = db.accountingService()
        val backupManager = BackupManager(
            context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        val accountRepository = AccountRepository(
            db.accountDao(), backupManager, db.transactionDao(), db.billDao(), db
        )
        val attachmentRepository = AttachmentRepository(context, db.transactionAttachmentDao())
        val billRecurrenceService = db.billRecurrenceService()
        transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            service, backupManager, attachmentRepository,
            BillRepository(
                database = db,
                billDao = db.billDao(),
                transactionDao = db.transactionDao(),
                accountDao = db.accountDao(),
                accountingService = service,
                billRecurrenceService = billRecurrenceService,
                backupManager = backupManager
            )
        )
        viewModel = CreditCardFormViewModel(
            db.accountDao(), db.billDao(), transactionRepository, service,
            billRecurrenceService, db.statementService(), db.creditCardBillingService(),
            backupManager, accountRepository, db
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private fun at(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    /** Drains Robolectric's main looper until [condition] holds (ViewModel coroutines resume
     *  on the main dispatcher after Room's background query executor completes). */
    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description; ui=${viewModel.ui.value}")
            }
            Thread.sleep(5)
        }
    }

    private suspend fun insertCard(outstandingMinorUnits: Long): Long =
        db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "cc-checkpoint-${System.nanoTime()}",
                institutionName = "Test Card",
                nickname = "Test Card",
                accountType = AccountType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                creditLimitMinorUnits = 1_000_000L,
                outstandingMinorUnits = outstandingMinorUnits,
                outstandingCheckpointMinorUnits = outstandingMinorUnits,
                outstandingCheckpointAtEpochMillis = at(1),
                outstandingAdjustmentMinorUnits = outstandingMinorUnits,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

    private fun purchaseTxn(accountId: Long, debitMinorUnits: Long, occurredAt: Long) =
        TransactionEntity(
            occurredAtEpochMillis = occurredAt,
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
            debitMinorUnits = debitMinorUnits,
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

    @Test
    fun reopenAndSaveWithoutTouchingOutstandingPreservesDurableCheckpoint() = runBlocking {
        val cardId = insertCard(outstandingMinorUnits = 500_000L) // ₹5,000 checkpoint

        // Reopen the card for an unrelated edit (nickname) and save.
        viewModel.loadForEdit(cardId)
        pumpUntil("form loaded") { viewModel.ui.value.editingAccountId == cardId }
        assertFalse(viewModel.ui.value.outstandingEdited)
        viewModel.onField { copy(nickname = "Renamed Card") }
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        val persisted = db.accountDao().findById(cardId)!!
        assertEquals("checkpoint must survive reopen+save", 500_000L, persisted.outstandingCheckpointMinorUnits)
        assertEquals("adjustment must survive reopen+save", 500_000L, persisted.outstandingAdjustmentMinorUnits)
        assertEquals("effective outstanding unchanged", 500_000L, persisted.outstandingMinorUnits)
        assertEquals("Renamed Card", persisted.nickname)

        // Purchases still move the position on top of the preserved checkpoint.
        db.transactionDao().insert(purchaseTxn(cardId, debitMinorUnits = 100_000L, occurredAt = at(2)))
        service.recalculateAccounts(setOf(cardId))
        assertEquals(600_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Full recalculation (startup repair) keeps the same result.
        service.recalculateAll()
        val afterFull = db.accountDao().findById(cardId)!!
        assertEquals(600_000L, afterFull.outstandingMinorUnits)
        assertEquals(500_000L, afterFull.outstandingCheckpointMinorUnits)
    }

    @Test
    fun deliberateOutstandingEditRebasesCheckpointExactlyOnce() = runBlocking {
        val cardId = insertCard(outstandingMinorUnits = 500_000L)

        viewModel.loadForEdit(cardId)
        pumpUntil("form loaded") { viewModel.ui.value.editingAccountId == cardId }
        viewModel.onOutstandingChange("7500") // ₹7,500 - a deliberate user edit
        assertTrue(viewModel.ui.value.outstandingEdited)
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        val persisted = db.accountDao().findById(cardId)!!
        assertEquals(750_000L, persisted.outstandingCheckpointMinorUnits)
        assertEquals(750_000L, persisted.outstandingAdjustmentMinorUnits)
        assertEquals(750_000L, persisted.outstandingMinorUnits)

        // A ₹1,000 purchase applies on top of the rebased checkpoint.
        db.transactionDao().insert(purchaseTxn(cardId, debitMinorUnits = 100_000L, occurredAt = at(3)))
        service.recalculateAccounts(setOf(cardId))
        assertEquals(850_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        // Re-saving without another outstanding edit must not rebase again.
        viewModel.loadForEdit(cardId)
        pumpUntil("form reloaded") { viewModel.ui.value.editingAccountId == cardId }
        viewModel.save()
        pumpUntil("second save completes") { viewModel.ui.value.saved }
        service.recalculateAll()
        val finalState = db.accountDao().findById(cardId)!!
        assertEquals(850_000L, finalState.outstandingMinorUnits)
        assertEquals(750_000L, finalState.outstandingCheckpointMinorUnits)
    }

    @Test
    fun paymentAfterCheckpointReducesOutstandingAndStaysStable() = runBlocking {
        val cardId = insertCard(outstandingMinorUnits = 500_000L)

        viewModel.loadForEdit(cardId)
        pumpUntil("form loaded") { viewModel.ui.value.editingAccountId == cardId }
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        // ₹1,000 purchase then a ₹300 bill payment.
        db.transactionDao().insert(purchaseTxn(cardId, debitMinorUnits = 100_000L, occurredAt = at(4)))
        service.recalculateAccounts(setOf(cardId))
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(5),
                rawDateString = "",
                txnType = TxnType.CREDIT_TRANSACTION,
                txnSubType = TxnSubType.TRANSFER_IN,
                txnKind = TxnKind.CC_BILL_PAYMENT,
                paymentType = PaymentType.NETBANKING,
                rawPaymentType = PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Card Payment",
                categoryId = null,
                rawCategoryName = null,
                accountId = cardId,
                creditMinorUnits = 30_000L,
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
        service.recalculateAccounts(setOf(cardId))
        assertEquals(570_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)

        service.recalculateAll()
        val persisted = db.accountDao().findById(cardId)!!
        assertEquals(570_000L, persisted.outstandingMinorUnits)
        assertEquals(500_000L, persisted.outstandingCheckpointMinorUnits)
        assertEquals(430_000L, persisted.availableLimitMinorUnits)
    }

    @Test
    fun deletingCardRemovesLinkedBillAndSoftDeletesAccountRow() = runBlocking {
        val cardId = insertCard(outstandingMinorUnits = 500_000L)
        db.transactionDao().insert(purchaseTxn(cardId, debitMinorUnits = 100_000L, occurredAt = at(2)))
        service.recalculateAccounts(setOf(cardId))

        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Test Card",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "cc-delete-$cardId",
                nickname = "Test Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = at(20),
                scheduleAnchorDay = 20,
                estimatedAmountMinorUnits = 500_000L,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val instanceId = db.billDao().insertInstance(
            BillInstanceEntity(
                billId = billId,
                dueDateEpochMillis = at(20),
                amountDueMinorUnits = 500_000L
            )
        )
        assertTrue(db.billDao().findInstancesForBill(billId).isNotEmpty())

        transactionRepository.deleteCreditCardAccount(cardId)

        val account = db.accountDao().findById(cardId)!!
        assertTrue("account row is kept for audit but marked deleted", account.deleted)
        assertNull("linked bill must be removed", db.billDao().findById(billId))
        assertTrue(
            "bill instances cascade with the bill",
            db.billDao().findInstancesForBill(billId).isEmpty()
        )
        assertEquals(
            "historical transactions stay in place",
            1,
            db.transactionDao().findByAccount(cardId).size
        )
        assertNull("the bill-instance row cascades away", db.billDao().findInstanceById(instanceId))
    }
}
