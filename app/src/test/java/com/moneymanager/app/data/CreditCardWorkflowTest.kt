package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
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
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate

/**
 * The credit card is the one account whose number is a LIABILITY, so its lifecycle has to be
 * exact: money out of a card is debt, money into a card is a payment, and everything the user
 * types by hand has to survive the next recalculation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreditCardWorkflowTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var transactions: TransactionRepository
    private lateinit var bills: BillRepository
    private lateinit var accounting: AccountingService

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        accounting = db.accountingService()
        val backup = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        bills = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = db.billRecurrenceService(),
            backupManager = backup
        )
        transactions = TransactionRepository(
            database = db,
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            billDao = db.billDao(),
            accountingService = accounting,
            backupManager = backup,
            attachmentRepository = com.moneymanager.app.data.repository.AttachmentRepository(
                context, db.transactionAttachmentDao()
            ),
            billRepository = bills
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(day: Int): Long = BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private suspend fun insertAccount(
        type: AccountType,
        current: Long = 0L,
        outstanding: Long? = null,
        creditLimit: Long? = if (type == AccountType.CREDIT_CARD) 100_000L else null
    ): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "acc-${type.name}-${System.nanoTime()}",
            institutionName = "Test",
            nickname = "Test ${type.name}",
            accountType = type,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = current,
            creditLimitMinorUnits = creditLimit,
            availableLimitMinorUnits = creditLimit?.let { it - (outstanding ?: 0L) },
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

    private suspend fun outstandingOf(accountId: Long): Long? =
        db.accountDao().findById(accountId)?.outstandingMinorUnits

    @Test
    fun takingCashOutOfACardIncreasesWhatTheCardOwes() = runBlocking {
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val bankId = insertAccount(AccountType.BANK, current = 20_000)

        // A cash advance: the user takes money out of the card's credit line.
        transactions.createTransfer(at(5), cardId, bankId, Money(3_000), "cash advance")

        assertEquals(
            "a cash advance is debt the card now owes",
            8_000L,
            outstandingOf(cardId)
        )
    }

    @Test
    fun payingOneCardWithAnotherChargesTheSourceAndSettlesTheTarget() = runBlocking {
        val source = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val target = insertAccount(AccountType.CREDIT_CARD, outstanding = 9_000)

        transactions.createTransfer(at(5), source, target, Money(4_000), "balance transfer")

        assertEquals("the card that paid grew", 9_000L, outstandingOf(source))
        assertEquals("the card that was paid fell", 5_000L, outstandingOf(target))
    }

    @Test
    fun moneyCreditedBackToACardReducesItsOutstandingAndIsNotAppIncome() = runBlocking {
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)

        transactions.addTransaction(
            com.moneymanager.app.data.repository.NewTransactionInput(
                occurredAtEpochMillis = at(5),
                accountId = cardId,
                categoryId = null,
                rawCategoryName = "Refund",
                merchantReceiverSender = "Shop",
                amount = Money(2_000),
                isIncome = true,
                paymentType = PaymentType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                notes = null,
                reimbursable = false
            )
        )

        assertEquals("a refund credited to the card clears debt", 3_000L, outstandingOf(cardId))
        val income = db.transactionDao().observeTotalIncomeInRange(at(1), at(31)).first()
        assertEquals("a card refund is not income", 0L, income)
    }

    @Test
    fun aUserEnteredCreditLimitSurvivesTheNextRecalculation() = runBlocking {
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000, creditLimit = 100_000)
        // The engine derives a limit from statement snapshots; a card with such a row cannot
        // have its user-typed limit silently replaced by the derived one.
        db.transactionDao().insert(
            com.moneymanager.app.data.local.entity.TransactionEntity(
                occurredAtEpochMillis = at(1),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.CREDIT_CARD,
                rawPaymentType = PaymentType.CREDIT_CARD.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Statement",
                categoryId = null,
                rawCategoryName = "Statement",
                accountId = cardId,
                creditMinorUnits = 0,
                debitMinorUnits = 1_000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = 5_000,
                availableLimitSnapshotMinorUnits = 95_000,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = true,
                dedupeFingerprint = "statement-row",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        accounting.recalculateAccounts(setOf(cardId))

        // The user corrects the limit in the card form.
        val account = db.accountDao().findById(cardId)!!
        db.accountDao().update(account.copy(creditLimitOverrideMinorUnits = 250_000))
        accounting.recalculateAccounts(setOf(cardId))

        val after = db.accountDao().findById(cardId)!!
        assertEquals("the user's limit wins", 250_000L, after.creditLimitMinorUnits)
        assertEquals(
            "available limit follows the effective limit",
            250_000L - (after.outstandingMinorUnits ?: 0L),
            after.availableLimitMinorUnits
        )
    }

    @Test
    fun aCardCannotBeUsedToPayAnotherCardsBill() = runBlocking {
        val cardA = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val cardB = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card B",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "b",
                nickname = "Card B",
                linkedAccountId = cardB,
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
            com.moneymanager.app.data.local.entity.BillInstanceEntity(
                billId = billId,
                dueDateEpochMillis = at(20),
                amountDueMinorUnits = 5_000
            )
        )

        val failure = runCatching {
            bills.recordCreditCardBillPayment(
                instanceId = instanceId,
                sourceAccountId = cardA,
                amountMinorUnits = 5_000,
                occurredAtEpochMillis = at(5)
            )
        }.exceptionOrNull()

        assertTrue(
            "paying a card bill with another card must be rejected: $failure",
            failure is IllegalArgumentException
        )
    }

    @Test
    fun movingATransferLegOntoACardTurnsItIntoACardPayment() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 20_000)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val (_, inId) = transactions.createTransfer(at(5), bankId, bankId, Money(1_000), null)

        // Re-point the receiving leg onto the card: it now settles card debt.
        transactions.updateEditableTransaction(inId, accountId = cardId)

        assertEquals(4_000L, outstandingOf(cardId))
        val leg = db.transactionDao().findById(inId)!!
        assertEquals(TxnKind.CC_BILL_PAYMENT, leg.txnKind)
        assertTrue(leg.isCreditCardBillPayment)
        assertEquals(cardId, leg.paysOffAccountId)
    }

    @Test
    fun movingACardPaymentLegAwayRestoresTheCardsOutstanding() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 20_000)
        val otherBank = insertAccount(AccountType.BANK, current = 0)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val (_, inId) = transactions.createTransfer(at(5), bankId, cardId, Money(2_000), null)
        assertEquals("the payment cleared the debt", 3_000L, outstandingOf(cardId))

        // The leg is re-pointed at a plain bank account: the card no longer gets the credit.
        transactions.updateEditableTransaction(inId, accountId = otherBank)

        assertEquals("the card is owed the money again", 5_000L, outstandingOf(cardId))
        val leg = db.transactionDao().findById(inId)!!
        assertEquals(TxnKind.REGULAR, leg.txnKind)
        assertTrue(!leg.isCreditCardBillPayment)
        assertEquals(null, leg.paysOffAccountId)
    }

    @Test
    fun aLegThatSettlesABillCannotBeRePointed() = runBlocking {
        val bankId = insertAccount(AccountType.BANK, current = 20_000)
        val otherBank = insertAccount(AccountType.BANK, current = 0)
        val cardId = insertAccount(AccountType.CREDIT_CARD, outstanding = 5_000)
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "b",
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
            com.moneymanager.app.data.local.entity.BillInstanceEntity(
                billId = billId,
                dueDateEpochMillis = at(20),
                amountDueMinorUnits = 5_000
            )
        )
        val cardLegId = bills.recordCreditCardBillPayment(
            instanceId = instanceId,
            sourceAccountId = bankId,
            amountMinorUnits = 5_000,
            occurredAtEpochMillis = at(5)
        )

        val failure = runCatching {
            transactions.updateEditableTransaction(cardLegId, accountId = otherBank)
        }.exceptionOrNull()

        assertTrue(
            "a leg that settles a bill must not be re-pointed: $failure",
            failure is IllegalArgumentException
        )
    }
}
