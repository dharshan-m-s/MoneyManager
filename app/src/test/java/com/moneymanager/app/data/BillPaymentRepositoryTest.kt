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
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.reporting.FinancialSummary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        accounting = AccountingService(db.accountDao(), db.transactionDao())
        repository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = BillRecurrenceService(db.billDao()),
            backupManager = BackupManager(
                context, db, db.transactionDao(), db.accountDao(), db.categoryDao()
            )
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
}
