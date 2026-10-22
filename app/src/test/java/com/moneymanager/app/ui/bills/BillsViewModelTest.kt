package com.moneymanager.app.ui.bills

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
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The Bills list must be ordered by urgency - the occurrence due soonest first - and must
 * surface a payment that already reached a linked card so the user can confirm it in one tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillsViewModelTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var viewModel: BillsViewModel
    private lateinit var collectJob: kotlinx.coroutines.Job

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        val accounting = db.accountingService()
        val backup = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        val repository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = accounting,
            billRecurrenceService = db.billRecurrenceService(),
            backupManager = backup
        )
        viewModel = BillsViewModel(db.billDao(), db.accountDao(), db.transactionDao(), repository)
        // stateIn(WhileSubscribed) only runs while it has a collector.
        collectJob = viewModel.viewModelScope.launch { viewModel.bills.collect {} }
    }

    @After
    fun tearDown() {
        collectJob.cancel()
        viewModel.viewModelScope.cancel()
        db.close()
    }

    private fun at(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, 1).plusDays((day - 1).toLong()))

    private suspend fun insertBill(
        name: String,
        dueDay: Int,
        linkedAccountId: Long? = null,
        amount: Long = 5_000
    ): Long = db.billDao().insert(
        BillEntity(
            billerName = name,
            billerType = if (linkedAccountId != null) BillerType.CREDIT_CARD else BillerType.OTHER,
            accountReferenceId = "ref-$name",
            nickname = name,
            linkedAccountId = linkedAccountId,
            billingCycle = BillingCycle.MONTHLY,
            nextDueDateEpochMillis = at(dueDay),
            scheduleAnchorDay = dueDay,
            estimatedAmountMinorUnits = amount,
            businessPersonal = BusinessPersonal.PERSONAL,
            autoGenerateBills = false,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    private suspend fun insertInstance(billId: Long, dueDay: Int, amount: Long = 5_000): Long =
        db.billDao().insertInstance(
            BillInstanceEntity(billId = billId, dueDateEpochMillis = at(dueDay), amountDueMinorUnits = amount)
        )

    private fun pumpBills(): List<BillRow> {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val rows = viewModel.bills.value
            if (rows.isNotEmpty()) return rows
            Thread.sleep(5)
        }
        return viewModel.bills.value
    }

    @Test
    fun billsAreOrderedBySoonestDueDate() = runBlocking {
        // Insert deliberately out of order: due in 10, 3 and 20 days.
        val soon = insertBill("Three Days", 23)
        val mid = insertBill("Ten Days", 30)
        val late = insertBill("Twenty Days", 40)
        insertInstance(soon, 23)
        insertInstance(mid, 30)
        insertInstance(late, 40)

        val rows = pumpBills()
        assertEquals(3, rows.size)
        assertEquals(
            listOf(soon, mid, late),
            rows.map { it.bill.id }
        )
        // Ascending due dates.
        val dues = rows.mapNotNull { it.nextInstance?.dueDateEpochMillis }
        assertEquals(dues.sorted(), dues)
    }

    @Test
    fun settledBillsSortAfterOpenOnes() = runBlocking {
        val open = insertBill("Open", 23)
        val settled = insertBill("Settled", 21)
        insertInstance(open, 23)
        val settledInstance = insertInstance(settled, 21, amount = 5_000)
        db.billDao().insertAllocation(
            com.moneymanager.app.data.local.entity.BillPaymentAllocationEntity(
                billInstanceId = settledInstance,
                transactionId = null,
                amountMinorUnits = 5_000,
                paidAtEpochMillis = at(20),
                createdAtEpochMillis = 0
            )
        )
        // Materialize the settled state through the repository's own sync path.
        val repository = BillRepository(
            db, db.billDao(), db.transactionDao(), db.accountDao(),
            db.accountingService(),
            db.billRecurrenceService(),
            BackupManager(RuntimeEnvironment.getApplication(), db, db.transactionDao(), db.accountDao(), db.categoryDao())
        )
        repository.markAsPaid(db.billDao().findInstanceById(settledInstance)!!, null, amountMinorUnits = 5_000)

        val rows = pumpBills()
        // The open bill is listed first regardless of its due date.
        assertNotNull(rows.firstOrNull { it.bill.id == open }?.nextInstance)
        assertEquals(open, rows.first().bill.id)
    }

    @Test
    fun detectsAnUnallocatedCardPaymentForTheLinkedBill() = runBlocking {
        val cardId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "card",
                institutionName = "HDFC",
                nickname = "HDFC Card",
                accountType = AccountType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                outstandingMinorUnits = 5_000,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val billId = insertBill("HDFC Card", 23, linkedAccountId = cardId)
        insertInstance(billId, 23, amount = 5_000)

        // A card-side payment leg that is not allocated to any bill.
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = TxnType.CREDIT_TRANSACTION,
                txnSubType = TxnSubType.TRANSFER_IN,
                txnKind = TxnKind.CC_BILL_PAYMENT,
                paymentType = PaymentType.ONLINE_TRANSFER,
                rawPaymentType = PaymentType.ONLINE_TRANSFER.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "HDFC Card",
                categoryId = null,
                rawCategoryName = "A/c to A/c",
                accountId = cardId,
                creditMinorUnits = 5_000,
                debitMinorUnits = 0,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isCreditCardBillPayment = true,
                paysOffAccountId = cardId,
                isHistoricalImport = false,
                dedupeFingerprint = "card-leg",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

        val row = pumpBills().first { it.bill.id == billId }
        assertNotNull("a recorded card payment should be surfaced", row.detectedPayment)
        assertEquals(5_000L, row.detectedPayment!!.creditMinorUnits)
    }

    @Test
    fun doesNotDetectAPaymentSmallerThanWhatIsDue() = runBlocking {
        val cardId = db.accountDao().insert(
            AccountEntity(
                sourceAccountId = "card2",
                institutionName = "ICICI",
                nickname = "ICICI Card",
                accountType = AccountType.CREDIT_CARD,
                businessPersonal = BusinessPersonal.PERSONAL,
                openingBalanceMinorUnits = 0,
                currentBalanceMinorUnits = 0,
                outstandingMinorUnits = 5_000,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )
        val billId = insertBill("ICICI Card", 23, linkedAccountId = cardId)
        insertInstance(billId, 23, amount = 5_000)

        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = at(19),
                rawDateString = "",
                txnType = TxnType.CREDIT_TRANSACTION,
                txnSubType = TxnSubType.TRANSFER_IN,
                txnKind = TxnKind.CC_BILL_PAYMENT,
                paymentType = PaymentType.ONLINE_TRANSFER,
                rawPaymentType = PaymentType.ONLINE_TRANSFER.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "ICICI Card",
                categoryId = null,
                rawCategoryName = "A/c to A/c",
                accountId = cardId,
                creditMinorUnits = 1_000,
                debitMinorUnits = 0,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isCreditCardBillPayment = true,
                paysOffAccountId = cardId,
                isHistoricalImport = false,
                dedupeFingerprint = "small-card-leg",
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0
            )
        )

        val row = pumpBills().first { it.bill.id == billId }
        assertNull("a partial payment should not be offered as a full settlement", row.detectedPayment)
    }
}
