package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.CreditCardBillingService
import com.moneymanager.app.data.repository.NewTransactionInput
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * A credit card's "current bill amount" is not a number the user types - it is the card's
 * statement for the cycle that is running right now, computed from the card's own
 * transactions. These tests pin that behaviour end to end: a purchase moves the statement, a
 * refund moves it back, a closed cycle keeps the amount it closed with, and the card's bill
 * always carries the same number the card screen shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreditCardStatementServiceTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var transactions: TransactionRepository
    private lateinit var billing: CreditCardBillingService

    /** The "now" every assertion is anchored to. */
    private val now = at(2026, 10, 22)

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        val accounting = db.accountingService()
        val backup = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        val bills = BillRepository(
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
        billing = db.creditCardBillingService()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(year: Int, month: Int, day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(year, month, day))

    private suspend fun insertCard(
        cycleStartDay: Int = 1,
        dueDate: Long = at(2026, 11, 21),
        outstanding: Long? = 0L
    ): Long = db.accountDao().insert(
        AccountEntity(
            sourceAccountId = "card-${System.nanoTime()}",
            institutionName = "HDFC Regalia",
            nickname = "Regalia",
            accountType = AccountType.CREDIT_CARD,
            businessPersonal = BusinessPersonal.PERSONAL,
            openingBalanceMinorUnits = 0,
            currentBalanceMinorUnits = 0,
            creditLimitMinorUnits = 100_000L,
            availableLimitMinorUnits = 100_000L - (outstanding ?: 0L),
            outstandingMinorUnits = outstanding,
            outstandingCheckpointMinorUnits = outstanding,
            outstandingCheckpointAtEpochMillis = if (outstanding != null) now else null,
            outstandingAdjustmentMinorUnits = outstanding,
            billingCycleStartDay = cycleStartDay,
            dueDate = dueDate,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private suspend fun purchase(cardId: Long, amount: Long, whenMillis: Long) {
        transactions.addTransaction(
            NewTransactionInput(
                occurredAtEpochMillis = whenMillis,
                accountId = cardId,
                categoryId = null,
                rawCategoryName = "Shopping",
                merchantReceiverSender = "Amazon",
                amount = Money(amount),
                isIncome = false,
                paymentType = PaymentType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                notes = null,
                reimbursable = false
            )
        )
    }

    private suspend fun refund(cardId: Long, amount: Long, whenMillis: Long) {
        transactions.addTransaction(
            NewTransactionInput(
                occurredAtEpochMillis = whenMillis,
                accountId = cardId,
                categoryId = null,
                rawCategoryName = "Refund",
                merchantReceiverSender = "Amazon Refund",
                amount = Money(amount),
                isIncome = true,
                paymentType = PaymentType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                notes = null,
                reimbursable = false
            )
        )
    }

    private suspend fun refresh(cardId: Long) = billing.refreshCard(cardId, now)

    private suspend fun currentBillAmount(cardId: Long): Long? =
        db.accountDao().findById(cardId)?.billAmountMinorUnits

    private suspend fun occurrenceAmount(cardId: Long): Long? {
        val bill = db.billDao().findByLinkedAccountId(cardId) ?: return null
        val instance = db.billDao().findInstancesForBill(bill.id).minByOrNull {
            it.dueDateEpochMillis
        } ?: return null
        return instance.amountDueMinorUnits
    }

    @Test
    fun aCardAlwaysGetsALinkedBillWithAnOccurrence() = runBlocking {
        val cardId = insertCard()

        refresh(cardId)

        val bill = db.billDao().findByLinkedAccountId(cardId)
        assertNotNull("a card must have a linked bill so its statement can be paid", bill)
        assertEquals(at(2026, 11, 21), bill!!.nextDueDateEpochMillis)
        assertEquals(1, db.billDao().findInstancesForBill(bill.id).size)
    }

    @Test
    fun theCurrentBillAmountFollowsTheCycleAndTheOccurrenceWithIt() = runBlocking {
        val cardId = insertCard()

        purchase(cardId, 2_500L, at(2026, 10, 5))
        refresh(cardId)

        assertEquals("the statement is what the card owes", 2_500L, currentBillAmount(cardId))
        assertEquals(
            "the bill carries the same number the card shows",
            2_500L,
            occurrenceAmount(cardId)
        )
    }

    @Test
    fun aRefundMovesTheStatementAndTheBillAmountWithIt() = runBlocking {
        val cardId = insertCard()

        purchase(cardId, 2_500L, at(2026, 10, 5))
        refresh(cardId)
        refund(cardId, 500L, at(2026, 10, 8))
        refresh(cardId)

        assertEquals(2_000L, currentBillAmount(cardId))
        assertEquals(2_000L, occurrenceAmount(cardId))
    }

    @Test
    fun theBillOccurrenceAlwaysFollowsTheCardsCurrentStatement() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))

        // Spending in the next cycle used to leave the Bills occurrence on the closed-cycle
        // snapshot while the card screen showed the full amount. Both must agree.
        purchase(cardId, 3_000L, at(2026, 11, 3))
        billing.refreshCard(cardId, at(2026, 11, 10))

        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        val instances = db.billDao().findInstancesForBill(bill.id)
        val instance = instances.first { it.dueDateEpochMillis == at(2026, 11, 21) }
        val outstanding = db.accountDao().findById(cardId)!!.outstandingMinorUnits
        assertEquals(
            "the Bills occurrence shows the same number as the card screen",
            outstanding,
            instance.amountDueMinorUnits
        )
        assertEquals(8_000L, instance.amountDueMinorUnits)
        val unpaid = instances.filter { !it.paid }
        assertTrue("there must be an unpaid occurrence to audit", unpaid.isNotEmpty())
        unpaid.forEach {
            assertEquals(
                "every unpaid occurrence must move with the card statement",
                outstanding,
                it.amountDueMinorUnits
            )
        }
    }

    @Test
    fun aCycleThatHasNotStartedIsEstimatedFromTheCycleBeforeIt() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)

        // The December bill (due 21 Dec) settles the November cycle, which has not started
        // yet - its amount can only be an estimate, based on the cycle before it.
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        db.billDao().update(
            bill.copy(
                nextDueDateEpochMillis = at(2026, 12, 21),
                scheduleAnchorDay = 21
            )
        )
        db.billDao().deleteInstanceById(
            db.billDao().findInstance(bill.id, at(2026, 11, 21))!!.id
        )
        db.billRecurrenceService().reconcileBill(bill.id, now)

        val december = db.billDao().findInstance(bill.id, at(2026, 12, 21))
        assertNotNull("the December occurrence must exist", december)
        assertEquals(
            "the estimate is the statement of the cycle before it",
            5_000L,
            december!!.amountDueMinorUnits
        )
    }

    @Test
    fun aCycleSeveralStepsAheadStillFindsTheLatestStatementToEstimateFrom() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)

        // A January bill settles the December cycle; neither the December nor the November
        // cycle has started, so the estimate walks back to the cycle that is running now.
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        db.billDao().update(
            bill.copy(
                nextDueDateEpochMillis = at(2027, 1, 21),
                scheduleAnchorDay = 21
            )
        )
        db.billDao().deleteInstanceById(
            db.billDao().findInstance(bill.id, at(2026, 11, 21))!!.id
        )
        db.billRecurrenceService().reconcileBill(bill.id, now)

        val january = db.billDao().findInstance(bill.id, at(2027, 1, 21))
        assertNotNull("the January occurrence must exist", january)
        assertEquals(
            "the estimate comes from the most recent statement that exists",
            5_000L,
            january!!.amountDueMinorUnits
        )
    }

    @Test
    fun theBillAmountIsTheStatementTheCardScreenShows() = runBlocking {
        val cardId = insertCard(outstanding = 4_000L)
        purchase(cardId, 1_000L, at(2026, 10, 5))
        refresh(cardId)

        val card = db.accountDao().findById(cardId)!!
        assertEquals(
            "the card's own statement and its bill amount are the same number",
            card.outstandingMinorUnits,
            card.billAmountMinorUnits
        )
        assertEquals(card.billAmountMinorUnits, occurrenceAmount(cardId))
    }

    @Test
    fun aManualCorrectionAndPartialPaymentAreReflectedInTheBillsAmount() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        val instance = db.billDao().findInstancesForBill(bill.id).minByOrNull {
            it.dueDateEpochMillis
        }!!
        db.billDao().updateInstance(instance.copy(paidAmountMinorUnits = 500L))

        // This is what the card edit form writes when the user corrects the outstanding.
        val card = db.accountDao().findById(cardId)!!
        db.accountDao().update(
            card.copy(
                outstandingMinorUnits = 4_500L,
                outstandingCheckpointMinorUnits = 4_500L,
                outstandingCheckpointAtEpochMillis = now,
                outstandingCheckpointTransactionId = card.outstandingCheckpointTransactionId ?: 0L,
                outstandingAdjustmentMinorUnits = 4_500L
            )
        )

        billing.refreshCard(cardId, now)

        val refreshed = db.billDao().findInstanceById(instance.id)!!
        assertEquals(4_500L, db.accountDao().findById(cardId)!!.outstandingMinorUnits)
        assertEquals(5_000L, refreshed.amountDueMinorUnits)
        assertEquals(4_500L, refreshed.amountDueMinorUnits - refreshed.paidAmountMinorUnits)
    }

    @Test
    fun foregroundRefreshRepairsOccurrencesLeftStaleByEarlierReconciliation() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        val instance = db.billDao().findInstancesForBill(bill.id).first()
        db.billDao().updateInstance(instance.copy(amountDueMinorUnits = 9_999L))

        // MainActivity/Application run reconcileAll() before refreshAllCards(). Even if an
        // earlier pass wrote a stale amount, the foreground refresh must be the final word.
        db.billRecurrenceService().reconcileAll(now)
        billing.refreshAllCards(now)

        val outstanding = db.accountDao().findById(cardId)!!.outstandingMinorUnits
        assertEquals(5_000L, outstanding)
        assertEquals(outstanding, db.billDao().findInstanceById(instance.id)!!.amountDueMinorUnits)
    }

    @Test
    fun workerReconciliationRepairsStaleCardBillAmounts() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        val instance = db.billDao().findInstancesForBill(bill.id).first()
        db.billDao().update(bill.copy(estimatedAmountMinorUnits = 1L))
        db.billDao().updateInstance(instance.copy(amountDueMinorUnits = 9_999L))

        // The background worker only calls reconcileAll(); it must still leave the card bill
        // showing the same number as the card screen.
        db.billRecurrenceService().reconcileAll(now)

        val outstanding = db.accountDao().findById(cardId)!!.outstandingMinorUnits
        assertEquals(outstanding, db.billDao().findById(bill.id)!!.estimatedAmountMinorUnits)
        assertEquals(outstanding, db.billDao().findInstanceById(instance.id)!!.amountDueMinorUnits)
    }

    @Test
    fun billEditReconciliationRepairsFutureCardBillAmounts() = runBlocking {
        val cardId = insertCard()
        purchase(cardId, 5_000L, at(2026, 10, 5))
        refresh(cardId)
        val bill = db.billDao().findByLinkedAccountId(cardId)!!
        val futureId = db.billDao().insertInstanceIgnore(
            BillInstanceEntity(
                billId = bill.id,
                dueDateEpochMillis = at(2026, 12, 21),
                amountDueMinorUnits = 111L
            )
        )
        db.billDao().update(bill.copy(estimatedAmountMinorUnits = 1L))

        db.billRecurrenceService().reconcileAfterEdit(bill.id, now)

        val outstanding = db.accountDao().findById(cardId)!!.outstandingMinorUnits
        assertEquals(outstanding, db.billDao().findById(bill.id)!!.estimatedAmountMinorUnits)
        db.billDao().findInstancesForBill(bill.id).filter { !it.paid }.forEach {
            assertEquals(outstanding, it.amountDueMinorUnits)
        }
        assertTrue("the future occurrence is still scheduled", futureId > 0L)
    }

    @Test
    fun aCardWithoutALinkedBillStillGetsOneOnRefresh() = runBlocking {
        val cardId = insertCard(dueDate = at(2026, 11, 21))
        db.accountDao().update(
            db.accountDao().findById(cardId)!!.copy(dueDate = null)
        )

        refresh(cardId)

        val bill = db.billDao().findByLinkedAccountId(cardId)
        assertNotNull("the refresh creates the missing linked bill", bill)
        assertTrue(
            "the derived due date follows the billing cycle",
            bill!!.nextDueDateEpochMillis >= now
        )
    }

    @Test
    fun aNonCardAccountNeverGetsACreditCardBill() = runBlocking {
        val bankId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "bank-${System.nanoTime()}",
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

        billing.refreshCard(bankId, now)

        assertNull(db.billDao().findByLinkedAccountId(bankId))
    }
}
