package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.reporting.FinancialSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillPaymentRepositoryTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var repository: BillRepository
    private lateinit var accounting: AccountingService
    private lateinit var backupManager: BackupManager
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
        repository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = BillRecurrenceService(db.billDao()),
            backupManager = backupManager
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(day: Int): Long =
        com.moneymanager.app.domain.billing.BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private suspend fun insertAccount(
        type: AccountType,
        current: Long = 0L,
        outstanding: Long? = null
    ): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "acc-${type.name}-${System.nanoTime()}",
            institutionName = "Test",
            nickname = "Test ${type.name}",
            accountType = type,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = current,
            creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
            outstandingMinorUnits = outstanding,
            manualBalanceOverrideMinorUnits = if (type != AccountType.CREDIT_CARD && current != 0L) current else null,
            manualBalanceOverrideAtEpochMillis = if (type != AccountType.CREDIT_CARD && current != 0L) at(1) else null,
            manualBalanceAdjustmentMinorUnits = if (type != AccountType.CREDIT_CARD && current != 0L) current else null,
            outstandingCheckpointMinorUnits = outstanding,
            outstandingCheckpointAtEpochMillis = if (outstanding != null) at(1) else null,
            outstandingAdjustmentMinorUnits = outstanding,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    @Test
    fun recordingACardPaymentIsAtomicIdempotentAndNotSpending() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 10_000)
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

        val cardLegId = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(18)
        )

        val instance = db.billDao().findInstanceById(instanceId)!!
        assertTrue(instance.paid)
        assertEquals(cardLegId, instance.paymentTransactionId)
        assertEquals(at(18), instance.paidAtEpochMillis)

        val bankTxns = db.transactionDao().findByAccount(bankId)
        val cardTxns = db.transactionDao().findByAccount(cardId)
        assertEquals(1, bankTxns.size)
        assertEquals(1, cardTxns.size)
        assertEquals(5_000L, bankTxns.single().debitMinorUnits)
        assertTrue(bankTxns.single().linkedTransferTransactionId == cardTxns.single().id)
        assertTrue(cardTxns.single().isCreditCardBillPayment)
        assertEquals(cardId, cardTxns.single().paysOffAccountId)
        assertEquals(5_000L, cardTxns.single().creditMinorUnits)

        assertEquals(5_000L, db.accountDao().findById(bankId)!!.currentBalanceMinorUnits)
        assertEquals(0L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertEquals(100_000L, db.accountDao().findById(cardId)!!.availableLimitMinorUnits)

        val accounts = db.accountDao().findAll()
        val byId = FinancialSummary.accountMap(accounts)
        val monthPeriod = FinancialSummary.Period(at(1), at(31))
        val txns = db.transactionDao().findInRange(at(1), at(31))
        assertTrue(FinancialSummary.spendRows(txns, byId, monthPeriod).isEmpty())
        assertTrue(FinancialSummary.incomeRows(txns, byId, monthPeriod).isEmpty())
        assertTrue(db.transactionDao().findByAccount(cardId).single().linkedTransferTransactionId != null)

        // A retry must return the same payment leg and create nothing new.
        val retried = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(18)
        )
        assertEquals(cardLegId, retried)
        assertEquals(1, db.transactionDao().findByAccount(bankId).size)
        assertEquals(1, db.transactionDao().findByAccount(cardId).size)
    }

    @Test
    fun ordinaryBillCanBeMarkedPaidWithAnExistingTransactionWithoutDuplicatingIt() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 10_000)
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Electricity",
                billerType = BillerType.ELECTRICITY,
                accountReferenceId = "x1",
                nickname = "Electricity",
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = at(20),
                scheduleAnchorDay = 20,
                estimatedAmountMinorUnits = 2_000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val instanceId = db.billDao().insertInstance(
            BillInstanceEntity(billId = billId, dueDateEpochMillis = at(20), amountDueMinorUnits = 2_000)
        )
        val txnId = db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Electricity",
                categoryId = null,
                rawCategoryName = null,
                accountId = bankId,
                creditMinorUnits = 0,
                debitMinorUnits = 2_000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "ordinary-bill-payment",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

        repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, txnId)
        val paid = db.billDao().findInstanceById(instanceId)!!
        assertTrue(paid.paid)
        assertEquals(txnId, paid.paymentTransactionId)
        assertEquals(1, db.transactionDao().findByAccount(bankId).size)
    }

    @Test
    fun markingPaidWithAnExistingBankTransactionCreatesOnlyTheMissingCardLeg() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 10_000)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 3_000)
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "9999",
                nickname = "Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = at(20),
                scheduleAnchorDay = 20,
                estimatedAmountMinorUnits = 3_000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val instanceId = db.billDao().insertInstance(
            BillInstanceEntity(billId = billId, dueDateEpochMillis = at(20), amountDueMinorUnits = 3_000)
        )
        // The user already recorded the bank-side transfer-out.
        val bankTxnId = db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                // Recorded by the user as an ordinary expense; marking it as a card payment
                // must reclassify it as a transfer so it is not counted as spending.
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Card payment",
                categoryId = null,
                rawCategoryName = null,
                accountId = bankId,
                creditMinorUnits = 0,
                debitMinorUnits = 3_000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "bank-side",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        accounting.recalculateAccounts(setOf(bankId))

        repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, bankTxnId)

        // No duplicate bank transaction: the existing row is reclassified, and exactly one
        // card-side leg is added.
        assertEquals(1, db.transactionDao().findByAccount(bankId).size)
        assertEquals(
            com.moneymanager.app.domain.model.TxnSubType.TRANSFER_OUT,
            db.transactionDao().findById(bankTxnId)!!.txnSubType
        )
        val cardTxn = db.transactionDao().findByAccount(cardId).single()
        assertTrue(cardTxn.isCreditCardBillPayment)
        assertEquals(0L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertNotNull(db.billDao().findInstanceById(instanceId)!!.paymentTransactionId)

        val byId = FinancialSummary.accountMap(db.accountDao().findAll())
        val month = FinancialSummary.Period(at(1), at(31))
        val spend = FinancialSummary.spendRows(
            db.transactionDao().findInRange(at(1), at(31)), byId, month
        )
        assertTrue("A card payment must not remain ordinary spending", spend.isEmpty())
    }

    @Test
    fun payingTheCardBillAdvancesTheScheduleImmediately() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 10_000)
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

        repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(18)
        )

        // The template's next due date follows the schedule off the paid occurrence as soon
        // as the payment lands - the card form reads this field as "next due date". The
        // repository reconciles with the wall clock, so assert the invariant (never parked
        // on the settled occurrence, anchor preserved) rather than an exact materialization.
        val paidDue = at(20)
        val bill = db.billDao().findById(billId)!!
        assertTrue(
            "next due must leave the paid occurrence",
            bill.nextDueDateEpochMillis > paidDue
        )
        assertEquals(20, BillSchedule.dateOf(bill.nextDueDateEpochMillis).dayOfMonth)

        val future = db.billDao().findInstancesForBill(billId)
            .filter { it.dueDateEpochMillis > paidDue }
        assertTrue("the next scheduled occurrence exists", future.isNotEmpty())
        assertTrue(future.all { !it.paid })
        assertTrue(db.billDao().findInstanceById(instanceId)!!.paid)
    }

    // ---------------------------------------------------------------------------------------------
    // Partial payments, allocation integrity and deletion reconciliation (spec section 3)
    // ---------------------------------------------------------------------------------------------

    private suspend fun insertBill(
        name: String,
        amountDue: Long,
        linkedAccountId: Long? = null
    ): Long = db.billDao().insert(
        BillEntity(
            billerName = name,
            billerType = if (linkedAccountId != null) BillerType.CREDIT_CARD else BillerType.OTHER,
            accountReferenceId = "ref",
            nickname = name,
            linkedAccountId = linkedAccountId,
            billingCycle = BillingCycle.MONTHLY,
            nextDueDateEpochMillis = at(20),
            scheduleAnchorDay = 20,
            estimatedAmountMinorUnits = amountDue,
            businessPersonal = BusinessPersonal.PERSONAL,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private suspend fun insertInstance(billId: Long, amountDue: Long, due: Long = at(20)): Long =
        db.billDao().insertInstance(
            BillInstanceEntity(billId = billId, dueDateEpochMillis = due, amountDueMinorUnits = amountDue)
        )

    private suspend fun bankExpense(accountId: Long, amount: Long, fingerprint: String): Long =
        db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Biller",
                categoryId = null,
                rawCategoryName = null,
                accountId = accountId,
                creditMinorUnits = 0,
                debitMinorUnits = amount,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = fingerprint,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

    @Test
    fun partialCashPaymentsAccumulateAndSettleOnlyWhenCovered() = runBlocking {
        val billId = insertBill("Rent", 10_000)
        val instanceId = insertInstance(billId, 10_000)

        val first = repository.markAsPaid(
            db.billDao().findInstanceById(instanceId)!!, null, amountMinorUnits = 5_000
        )
        assertEquals(false, first.paid)
        assertEquals(5_000L, first.paidAmountMinorUnits)
        assertEquals(
            5_000L,
            first.amountDueMinorUnits - first.paidAmountMinorUnits
        )

        val afterFirst = db.billDao().findAllocationsForInstance(instanceId)
        assertEquals(1, afterFirst.size)
        assertEquals(null, afterFirst[0].transactionId)
        assertEquals(5_000L, afterFirst[0].amountMinorUnits)

        // A partial payment must not move the schedule - the occurrence is still owed.
        assertEquals(at(20), db.billDao().findById(billId)!!.nextDueDateEpochMillis)
        assertEquals(
            1,
            db.billDao().observeUpcomingUnpaid().first().size
        )

        val second = repository.markAsPaid(
            db.billDao().findInstanceById(instanceId)!!, null, amountMinorUnits = 5_000
        )
        assertEquals(true, second.paid)
        assertEquals(10_000L, second.paidAmountMinorUnits)
        assertEquals(2, db.billDao().findAllocationsForInstance(instanceId).size)
        assertTrue("full settlement advances the schedule",
            db.billDao().findById(billId)!!.nextDueDateEpochMillis > at(20))
        // The settled occurrence leaves the upcoming set entirely; the following cycle's
        // occurrence (created by the advance) is what upcoming money now describes.
        val upcomingAfter = db.billDao().observeUpcomingUnpaid().first()
        assertTrue(upcomingAfter.none { it.id == instanceId })
        assertEquals(
            upcomingAfter.sumOf { it.amountDueMinorUnits - it.paidAmountMinorUnits },
            db.billDao().observeTotalUpcomingBillAmount().first()
        )
    }

    @Test
    fun aSmallerLinkedTransactionPartiallySettlesUntilTheRestArrives() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 50_000)
        val billId = insertBill("Electricity", 10_000)
        val instanceId = insertInstance(billId, 10_000)

        val partOne = bankExpense(bankId, 4_000, "part-one")
        val partial = repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, partOne)
        assertEquals(false, partial.paid)
        assertEquals(4_000L, partial.paidAmountMinorUnits)
        assertEquals(partOne, partial.paymentTransactionId)
        assertEquals(at(20), db.billDao().findById(billId)!!.nextDueDateEpochMillis)

        val partTwo = bankExpense(bankId, 6_000, "part-two")
        val settled = repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, partTwo)
        assertEquals(true, settled.paid)
        assertEquals(10_000L, settled.paidAmountMinorUnits)
        assertEquals(partOne, settled.paymentTransactionId)
        assertEquals(2, db.billDao().findAllocationsForInstance(instanceId).size)
        assertTrue(
            db.billDao().findById(billId)!!.nextDueDateEpochMillis > at(20)
        )
    }

    @Test
    fun overPaymentIsRecordedInFullAndLeavesNoUpcomingAmount() = runBlocking {
        val billId = insertBill("Internet", 10_000)
        val instanceId = insertInstance(billId, 10_000)

        val result = repository.markAsPaid(
            db.billDao().findInstanceById(instanceId)!!, null, amountMinorUnits = 12_000
        )
        assertEquals(true, result.paid)
        assertEquals(12_000L, result.paidAmountMinorUnits)
        val allocation = db.billDao().findAllocationsForInstance(instanceId).single()
        assertEquals(12_000L, allocation.amountMinorUnits)
        // Fully paid: the occurrence is gone from the upcoming set (the advanced next cycle
        // may exist, but this occurrence never contributes again).
        assertTrue(db.billDao().observeUpcomingUnpaid().first().none { it.id == instanceId })
    }

    @Test
    fun oneTransactionCanNeverSettleTwoBills() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 50_000)
        val txnId = bankExpense(bankId, 3_000, "single-payment")
        val billA = insertBill("Bill A", 3_000)
        val instanceA = insertInstance(billA, 3_000)
        val billB = insertBill("Bill B", 3_000)
        val instanceB = insertInstance(billB, 3_000)

        repository.markAsPaid(db.billDao().findInstanceById(instanceA)!!, txnId)
        assertEquals(true, db.billDao().findInstanceById(instanceA)!!.paid)

        val failure = runCatching {
            repository.markAsPaid(db.billDao().findInstanceById(instanceB)!!, txnId)
        }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("already recorded"))

        val stateB = db.billDao().findInstanceById(instanceB)!!
        assertEquals(0L, stateB.paidAmountMinorUnits)
        assertEquals(false, stateB.paid)
        assertEquals(0, db.billDao().findAllocationsForInstance(instanceB).size)
        // Bill A keeps its settlement untouched.
        assertEquals(3_000L, db.billDao().findInstanceById(instanceA)!!.paidAmountMinorUnits)
        assertEquals(1, db.billDao().findAllocationsForInstance(instanceA).size)
    }

    @Test
    fun deletingThePaymentTransactionKeepsOtherPaymentsAndRestoresTheRightState() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 50_000)
        val transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            accounting, backupManager,
            com.moneymanager.app.data.repository.AttachmentRepository(
                appContext, db.transactionAttachmentDao()
            ),
            repository
        )

        // Case 1: a single linked payment - deleting it restores full unpaid.
        val billOne = insertBill("Water", 5_000)
        val instanceOne = insertInstance(billOne, 5_000)
        val waterTxn = bankExpense(bankId, 5_000, "water")
        repository.markAsPaid(db.billDao().findInstanceById(instanceOne)!!, waterTxn)
        assertEquals(true, db.billDao().findInstanceById(instanceOne)!!.paid)

        transactionRepository.deleteTransaction(waterTxn)
        val afterDeleteOne = db.billDao().findInstanceById(instanceOne)!!
        assertEquals(false, afterDeleteOne.paid)
        assertEquals(0L, afterDeleteOne.paidAmountMinorUnits)
        assertEquals(null, afterDeleteOne.paymentTransactionId)
        assertEquals(0, db.billDao().findAllocationsForInstance(instanceOne).size)

        // Case 2: partial linked payment + cash - deleting the linked one keeps the cash.
        val billTwo = insertBill("Gas", 10_000)
        val instanceTwo = insertInstance(billTwo, 10_000)
        val gasTxn = bankExpense(bankId, 7_000, "gas")
        repository.markAsPaid(db.billDao().findInstanceById(instanceTwo)!!, gasTxn)
        repository.markAsPaid(
            db.billDao().findInstanceById(instanceTwo)!!, null, amountMinorUnits = 3_000
        )
        assertEquals(true, db.billDao().findInstanceById(instanceTwo)!!.paid)

        transactionRepository.deleteTransaction(gasTxn)
        val afterDeleteTwo = db.billDao().findInstanceById(instanceTwo)!!
        assertEquals(false, afterDeleteTwo.paid)
        assertEquals(3_000L, afterDeleteTwo.paidAmountMinorUnits)
        assertEquals(null, afterDeleteTwo.paymentTransactionId)
        val remaining = db.billDao().findAllocationsForInstance(instanceTwo)
        assertEquals(1, remaining.size)
        assertEquals(null, remaining[0].transactionId)
        assertEquals(3_000L, remaining[0].amountMinorUnits)
    }

    @Test
    fun deletingTheTransferPairRestoresTheCardBillAndOutstanding() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 10_000)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val billId = insertBill("HDFC Card", 5_000, linkedAccountId = cardId)
        val instanceId = insertInstance(billId, 5_000)
        val transactionRepository = TransactionRepository(
            db, db.transactionDao(), db.accountDao(), db.categoryDao(), db.billDao(),
            accounting, backupManager,
            com.moneymanager.app.data.repository.AttachmentRepository(
                appContext, db.transactionAttachmentDao()
            ),
            repository
        )

        val cardLegId = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(18)
        )
        val bankLegId = db.transactionDao().findById(cardLegId)!!.linkedTransferTransactionId!!
        assertEquals(true, db.billDao().findInstanceById(instanceId)!!.paid)

        transactionRepository.deleteTransaction(bankLegId)
        val afterDelete = db.billDao().findInstanceById(instanceId)!!
        assertEquals(false, afterDelete.paid)
        assertEquals(0L, afterDelete.paidAmountMinorUnits)
        assertEquals(0, db.billDao().findAllocationsForInstance(instanceId).size)
        assertEquals(0, db.transactionDao().findByAccount(bankId).size)
        assertEquals(0, db.transactionDao().findByAccount(cardId).size)
        assertEquals(5_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
    }

    @Test
    fun cardBillRejectsPurchasesOnTheLinkedOrAnotherCreditCard() = runBlocking {
        val cardA = insertAccount(AccountType.CREDIT_CARD, outstanding = 3_000)
        val cardB = insertAccount(AccountType.CREDIT_CARD, outstanding = 3_000)
        val billId = insertBill("Airtel Card", 3_000, linkedAccountId = cardA)
        val instanceId = insertInstance(billId, 3_000)

        // A purchase recorded on the linked card itself is spending, not a payment.
        val ownCardPurchase = db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Airtel",
                categoryId = null,
                rawCategoryName = null,
                accountId = cardA,
                creditMinorUnits = 0,
                debitMinorUnits = 3_000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "own-card-purchase",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val ownFailure = runCatching {
            repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, ownCardPurchase)
        }.exceptionOrNull()
        assertNotNull(ownFailure)
        assertTrue(ownFailure!!.message!!.contains("on the card itself"))

        // An expense on a DIFFERENT credit card must never become a leg on this bill.
        val otherCardExpense = db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Airtel",
                categoryId = null,
                rawCategoryName = null,
                accountId = cardB,
                creditMinorUnits = 0,
                debitMinorUnits = 3_000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "other-card-purchase",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val otherFailure = runCatching {
            repository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, otherCardExpense)
        }.exceptionOrNull()
        assertNotNull(otherFailure)
        assertTrue(otherFailure!!.message!!.contains("another credit card"))

        // Nothing was mutated: still unpaid, no allocations, no payment legs created beyond
        // the purchase rows the test itself inserted.
        val state = db.billDao().findInstanceById(instanceId)!!
        assertEquals(false, state.paid)
        assertEquals(0L, state.paidAmountMinorUnits)
        assertEquals(0, db.billDao().findAllocationsForInstance(instanceId).size)
        assertTrue(db.transactionDao().findByAccount(cardA).none { it.isCreditCardBillPayment })
        assertTrue(db.transactionDao().findByAccount(cardB).none { it.isCreditCardBillPayment })
        assertEquals(1, db.transactionDao().findByAccount(cardA).size)
        assertEquals(1, db.transactionDao().findByAccount(cardB).size)
    }

    @Test
    fun partialCardPaymentKeepsTheOccurrenceOpenUntilFullyCovered() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 20_000)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val billId = insertBill("HDFC Card", 5_000, linkedAccountId = cardId)
        val instanceId = insertInstance(billId, 5_000)

        val firstLeg = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 2_000,
            occurredAtEpochMillis = at(17)
        )
        val partial = db.billDao().findInstanceById(instanceId)!!
        assertEquals(false, partial.paid)
        assertEquals(2_000L, partial.paidAmountMinorUnits)
        // 5,000 outstanding minus the 2,000 payment actually landed on the card.
        assertEquals(3_000L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        // Schedule must wait for full settlement.
        assertEquals(at(20), db.billDao().findById(billId)!!.nextDueDateEpochMillis)
        // A partial card payment is still a transfer, never spending.
        val byId = com.moneymanager.app.domain.reporting.FinancialSummary.accountMap(db.accountDao().findAll())
        val month = com.moneymanager.app.domain.reporting.FinancialSummary.Period(at(1), at(31))
        val spend = com.moneymanager.app.domain.reporting.FinancialSummary.spendRows(
            db.transactionDao().findInRange(at(1), at(31)), byId, month
        )
        assertTrue(spend.isEmpty())

        val secondLeg = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 3_000,
            occurredAtEpochMillis = at(18)
        )
        val settled = db.billDao().findInstanceById(instanceId)!!
        assertEquals(true, settled.paid)
        assertEquals(5_000L, settled.paidAmountMinorUnits)
        assertEquals(0L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertEquals(at(18), settled.paidAtEpochMillis)
        assertTrue(
            "settlement advances the schedule",
            db.billDao().findById(billId)!!.nextDueDateEpochMillis > at(20)
        )
        assertEquals(2, db.billDao().findAllocationsForInstance(instanceId).size)

        // Retry after settlement: same first payment id, no new transactions anywhere.
        val retried = repository.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 3_000,
            occurredAtEpochMillis = at(18)
        )
        assertEquals(firstLeg, retried)
        assertEquals(2, db.transactionDao().findByAccount(bankId).size)
        assertEquals(2, db.transactionDao().findByAccount(cardId).size)
    }
}
