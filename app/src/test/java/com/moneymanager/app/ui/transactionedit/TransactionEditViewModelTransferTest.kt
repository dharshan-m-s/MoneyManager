package com.moneymanager.app.ui.transactionedit

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
import com.moneymanager.app.data.repository.AttachmentRepository
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.cancel
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
import java.time.LocalDate

/**
 * Section 4 driven through the REAL [TransactionEditViewModel]: what the edit screen sends is
 * what the linked-transfer contract executes - a plain transfer's amount and date mirror to
 * both legs, and a credit-card bill payment loads its amount read-only (never sent), so the
 * bill-payment model keeps sole ownership of what was paid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionEditViewModelTransferTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var accounting: AccountingService
    private lateinit var backupManager: BackupManager
    private lateinit var billRepository: BillRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var viewModel: TransactionEditViewModel
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        appContext = context
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        accounting = AccountingService(db.accountDao(), db.transactionDao())
        backupManager = BackupManager(
            context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        billRepository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = BillRecurrenceService(db.billDao()),
            backupManager = backupManager
        )
        transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            accounting, backupManager,
            AttachmentRepository(context, db.transactionAttachmentDao()),
            billRepository
        )
        viewModel = TransactionEditViewModel(
            db.transactionDao(),
            db.accountDao(),
            db.categoryDao(),
            transactionRepository,
            AttachmentRepository(context, db.transactionAttachmentDao())
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private fun at(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private suspend fun insertBankAccount(): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "bank-${System.nanoTime()}",
            institutionName = "Test",
            nickname = "Bank",
            accountType = AccountType.BANK,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private fun pumpUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description; state=${viewModel.ui.value}")
            }
            Thread.sleep(5)
        }
    }

    private suspend fun balance(accountId: Long): Long =
        db.accountDao().findById(accountId)!!.currentBalanceMinorUnits

    @Test
    fun savingANewAmountAndDateOnATransferMirrorsBothLegsEndToEnd() = runBlocking {
        val a = insertBankAccount()
        val b = insertBankAccount()
        val (outId, inId) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), null)

        viewModel.load(outId)
        pumpUntil("edit form loaded") { viewModel.ui.value.loaded }
        assertTrue(viewModel.ui.value.isTransfer)
        assertFalse(viewModel.ui.value.isBillPaymentPair)

        viewModel.onField { copy(amount = "70") }
        viewModel.onField { copy(epochMillis = at(6)) }
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        val out = db.transactionDao().findById(outId)!!
        val inn = db.transactionDao().findById(inId)!!
        // One amount, both legs, both balances - moved together by one save.
        assertEquals(7_000L, out.debitMinorUnits)
        assertEquals(0L, out.creditMinorUnits)
        assertEquals(7_000L, inn.creditMinorUnits)
        assertEquals(0L, inn.debitMinorUnits)
        assertEquals(at(6), out.occurredAtEpochMillis)
        assertEquals(at(6), inn.occurredAtEpochMillis)
        assertEquals(-7_000L, balance(a))
        assertEquals(7_000L, balance(b))
    }

    @Test
    fun savingANotesOnlyTransferEditKeepsEveryStoredSign() = runBlocking {
        val a = insertBankAccount()
        val b = insertBankAccount()
        val (outId, inId) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), null)

        viewModel.load(outId)
        pumpUntil("edit form loaded") { viewModel.ui.value.loaded }
        viewModel.onField { copy(notes = "monthly shift") }
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        assertEquals("monthly shift", db.transactionDao().findById(outId)!!.notes)
        assertEquals(5_000L, db.transactionDao().findById(outId)!!.debitMinorUnits)
        assertEquals(5_000L, db.transactionDao().findById(inId)!!.creditMinorUnits)
        assertEquals(-5_000L, balance(a))
        assertEquals(5_000L, balance(b))
    }

    @Test
    fun aCardBillPaymentPairLoadsItsAmountReadOnlyAndSavingChangesNothing() = runBlocking {
        val bankId = insertBankAccount()
        val cardId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "card-${System.nanoTime()}",
                institutionName = "Test",
                nickname = "Card",
                accountType = AccountType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                creditLimitMinorUnits = 100_000L,
                outstandingMinorUnits = 5_000,
                outstandingCheckpointMinorUnits = 5_000,
                outstandingCheckpointAtEpochMillis = at(1),
                outstandingAdjustmentMinorUnits = 5_000,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "HDFC Card",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "1234",
                nickname = "Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = at(20),
                scheduleAnchorDay = 20,
                estimatedAmountMinorUnits = 5_000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val instanceId = db.billDao().insertInstance(
            BillInstanceEntity(billId = billId, dueDateEpochMillis = at(20), amountDueMinorUnits = 5_000)
        )
        val cardLegId = billRepository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(18)
        )
        val bankLegId = db.transactionDao().findById(cardLegId)!!.linkedTransferTransactionId!!

        viewModel.load(cardLegId)
        pumpUntil("edit form loaded") { viewModel.ui.value.loaded }
        assertTrue(viewModel.ui.value.isTransfer)
        assertTrue(viewModel.ui.value.isBillPaymentPair)

        // Saving (amount field untouched, and never sent for a bill payment) is a no-op on
        // the pair's money: the bill-payment model owns it.
        viewModel.onField { copy(notes = "payment note") }
        viewModel.save()
        pumpUntil("save completes") { viewModel.ui.value.saved }

        val bankLeg = db.transactionDao().findById(bankLegId)!!
        val cardLeg = db.transactionDao().findById(cardLegId)!!
        assertEquals(5_000L, bankLeg.debitMinorUnits)
        assertEquals(5_000L, cardLeg.creditMinorUnits)
        assertEquals("payment note", cardLeg.notes)
        val instance = db.billDao().findInstanceById(instanceId)!!
        assertTrue(instance.paid)
        assertEquals(5_000L, instance.paidAmountMinorUnits)
        assertEquals(1, db.billDao().findAllocationsForInstance(instanceId).size)
        assertEquals(5_000L, db.billDao().findAllocationsForInstance(instanceId).single().amountMinorUnits)
    }

    /**
     * If the row disappears between load and save, the save must surface an error and must
     * NOT report success - the screen would otherwise navigate away claiming it was saved.
     */
    @Test
    fun savingAnEditForAVanishedTransactionSurfacesAnErrorInsteadOfFalselySucceeding() = runBlocking {
        val accountId = insertBankAccount()
        val txnId = db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(2),
                rawDateString = "",
                txnType = TxnType.DEBIT_TRANSACTION,
                txnSubType = TxnSubType.EXPENSE,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.NETBANKING,
                rawPaymentType = PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Coffee",
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 0L,
                debitMinorUnits = 100L,
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

        viewModel.load(txnId)
        pumpUntil("edit form loaded") { viewModel.ui.value.loaded }

        // The row vanishes behind the form's back (another surface deleted it).
        db.transactionDao().deleteById(txnId)

        viewModel.save()
        pumpUntil("save fails") { viewModel.ui.value.error != null }

        assertFalse(viewModel.ui.value.saved)
        assertTrue(viewModel.ui.value.error!!.contains("no longer exists"))
    }
}
