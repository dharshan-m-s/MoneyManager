package com.moneymanager.app.data

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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Section 4 - the linked-transfer editing contract.
 *
 * A pair shares exactly two facts, amount and date: editing either must move BOTH legs inside
 * one database transaction, each leg keeping its own credit/debit placement, so net worth can
 * never change because of an edit. Per-leg facts stay per leg. A leg can be re-pointed to a
 * different account but never onto the receiving account. Credit-card bill-payment pairs keep
 * their amount owned by the bill-payment model and reject amount edits here.
 *
 * These tests also pin the sign-integrity regression: saving an edit without an amount must
 * never invert a stored row (the old fallback recomputed a transfer-out's debit from
 * `-debit`, flipping it negative and corrupting both account balances).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinkedTransferEditingTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var billRepository: BillRepository
    private lateinit var accounting: AccountingService
    private lateinit var backupManager: BackupManager
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        appContext = context
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        accounting = db.accountingService()
        backupManager = BackupManager(
            context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        billRepository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = db.billRecurrenceService(),
            backupManager = backupManager
        )
        transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            accounting, backupManager,
            AttachmentRepository(context, db.transactionAttachmentDao()),
            billRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private suspend fun insertAccount(type: AccountType, outstanding: Long? = null): Long =
        db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "acc-${type.name}-${System.nanoTime()}",
                institutionName = "Test",
                nickname = "Test ${type.name}",
                accountType = type,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
                outstandingMinorUnits = outstanding,
                outstandingCheckpointMinorUnits = outstanding,
                outstandingCheckpointAtEpochMillis = outstanding?.let { at(1) },
                outstandingAdjustmentMinorUnits = outstanding,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

    private suspend fun balance(accountId: Long): Long =
        db.accountDao().findById(accountId)!!.currentBalanceMinorUnits

    private suspend fun txn(id: Long): TransactionEntity =
        db.transactionDao().findById(id)!!

    private inline fun expectIllegalArgument(message: String, block: () -> Unit) {
        try {
            block()
            fail(message)
        } catch (e: IllegalArgumentException) {
            // expected - the contract rejects this edit outright
        }
    }

    @Test
    fun editingTheDateMovesBothLegsTogetherAndKeepsEverySign() = runBlocking {
        val a = insertAccount(AccountType.BANK)
        val b = insertAccount(AccountType.BANK)
        val (outId, inId) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), "rent move")
        assertEquals(-5_000L, balance(a))
        assertEquals(5_000L, balance(b))

        transactionRepository.updateEditableTransaction(outId, occurredAtEpochMillis = at(5))

        val out = txn(outId)
        val inn = txn(inId)
        assertEquals(at(5), out.occurredAtEpochMillis)
        assertEquals(at(5), inn.occurredAtEpochMillis)
        // Signs and placement intact on both sides - no inversion from an amount-less edit.
        assertEquals(0L, out.creditMinorUnits)
        assertEquals(5_000L, out.debitMinorUnits)
        assertEquals(5_000L, inn.creditMinorUnits)
        assertEquals(0L, inn.debitMinorUnits)
        assertEquals(inId, out.linkedTransferTransactionId)
        assertEquals(outId, inn.linkedTransferTransactionId)
        // An edit is not a movement: both balances stay exactly where they were.
        assertEquals(-5_000L, balance(a))
        assertEquals(5_000L, balance(b))
    }

    @Test
    fun editingTheAmountMovesBothLegsAtomicallyAcrossBothBalances() = runBlocking {
        val a = insertAccount(AccountType.BANK)
        val b = insertAccount(AccountType.BANK)
        val (outId, inId) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), null)

        transactionRepository.updateEditableTransaction(outId, amount = Money(7_000))

        val out = txn(outId)
        val inn = txn(inId)
        assertEquals(7_000L, out.debitMinorUnits)
        assertEquals(0L, out.creditMinorUnits)
        assertEquals(7_000L, inn.creditMinorUnits)
        assertEquals(0L, inn.debitMinorUnits)
        assertEquals(-7_000L, balance(a))
        assertEquals(7_000L, balance(b))

        // Editing from the other leg mirrors identically - the pair has one amount.
        transactionRepository.updateEditableTransaction(inId, amount = Money(6_500))
        assertEquals(6_500L, txn(outId).debitMinorUnits)
        assertEquals(6_500L, txn(inId).creditMinorUnits)
        assertEquals(-6_500L, balance(a))
        assertEquals(6_500L, balance(b))
    }

    @Test
    fun perLegFactsStayPerLegAndAmountlessEditsNeverInvertSigns() = runBlocking {
        val a = insertAccount(AccountType.BANK)
        val b = insertAccount(AccountType.BANK)
        val (outId, inId) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), null)

        transactionRepository.updateEditableTransaction(
            outId,
            notes = "sent via IMPS",
            merchantReceiverSender = "To savings",
            paymentType = PaymentType.IMPS
        )

        val out = txn(outId)
        val inn = txn(inId)
        // This leg took the per-leg edits...
        assertEquals("sent via IMPS", out.notes)
        assertEquals("To savings", out.merchantReceiverSender)
        assertEquals(PaymentType.IMPS, out.paymentType)
        // ...the peer kept its own values entirely.
        assertNull(inn.notes)
        assertEquals("Transfer", inn.merchantReceiverSender)
        assertEquals(PaymentType.ONLINE_TRANSFER, inn.paymentType)
        // Sign integrity: an edit that carries no amount leaves the stored magnitude untouched.
        assertEquals(5_000L, out.debitMinorUnits)
        assertEquals(5_000L, inn.creditMinorUnits)
        assertEquals(-5_000L, balance(a))
        assertEquals(5_000L, balance(b))
    }

    @Test
    fun aLegCannotBeMovedOntoTheAccountReceivingItsOtherSide() = runBlocking {
        val a = insertAccount(AccountType.BANK)
        val b = insertAccount(AccountType.BANK)
        val c = insertAccount(AccountType.BANK)
        val (outId, _) = transactionRepository.createTransfer(at(1), a, b, Money(5_000), null)

        expectIllegalArgument("moving the leg onto the peer's account must be rejected") {
            runBlocking {
                transactionRepository.updateEditableTransaction(outId, accountId = b)
            }
        }
        // Nothing changed anywhere - the rejected edit was atomic.
        assertEquals(a, txn(outId).accountId)
        assertEquals(-5_000L, balance(a))
        assertEquals(5_000L, balance(b))

        // Re-pointing at a THIRD account is legitimate: the pair follows.
        transactionRepository.updateEditableTransaction(outId, accountId = c)
        assertEquals(c, txn(outId).accountId)
        assertEquals(0L, balance(a))
        assertEquals(-5_000L, balance(c))
        assertEquals(5_000L, balance(b))
    }

    @Test
    fun cardBillPaymentPairsRejectAmountEditsButMoveDatesOnBothLegs() = runBlocking {
        val bankId = insertAccount(AccountType.BANK)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
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
        val bankLegId = txn(cardLegId).linkedTransferTransactionId!!

        expectIllegalArgument("the bill payment's amount must not change here") {
            runBlocking {
                transactionRepository.updateEditableTransaction(bankLegId, amount = Money(6_000))
            }
        }
        expectIllegalArgument("the card side must not change the amount either") {
            runBlocking {
                transactionRepository.updateEditableTransaction(cardLegId, amount = Money(4_000))
            }
        }
        // Both legs, the allocation and the settlement state all survived untouched.
        assertEquals(5_000L, txn(bankLegId).debitMinorUnits)
        assertEquals(5_000L, txn(cardLegId).creditMinorUnits)
        val allocations = db.billDao().findAllocationsForInstance(instanceId)
        assertEquals(1, allocations.size)
        assertEquals(5_000L, allocations.single().amountMinorUnits)
        assertEquals(5_000L, db.billDao().findInstanceById(instanceId)!!.paidAmountMinorUnits)
        assertEquals(true, db.billDao().findInstanceById(instanceId)!!.paid)

        // The date is still pair-level and moves both legs together.
        transactionRepository.updateEditableTransaction(bankLegId, occurredAtEpochMillis = at(19))
        assertEquals(at(19), txn(bankLegId).occurredAtEpochMillis)
        assertEquals(at(19), txn(cardLegId).occurredAtEpochMillis)
        assertEquals(cardLegId, txn(bankLegId).linkedTransferTransactionId)
        assertEquals(bankLegId, txn(cardLegId).linkedTransferTransactionId)
    }
}
