package com.moneymanager.app.data.repository

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillPaymentAllocationEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AccountRepository
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.accountingService
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountDeletionDependencyTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var repository: AccountRepository
    private lateinit var backupManager: BackupManager
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        appContext = context
        db = Room.inMemoryDatabaseBuilder(context, MoneyManagerDatabase::class.java)
            .allowMainThreadQueries().build()
        backupManager = BackupManager(context, db, db.transactionDao(), db.accountDao(), db.categoryDao())
        repository = AccountRepository(
            accountDao = db.accountDao(),
            backupManager = backupManager,
            transactionDao = db.transactionDao(),
            billDao = db.billDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun account(
        type: AccountType = AccountType.BANK,
        id: Long = 0L,
        sourceAccountId: String = "acc-$id",
        deleted: Boolean = false
    ): AccountEntity = AccountEntity(
        id = id,
        sourceAccountId = sourceAccountId,
        institutionName = "Test",
        nickname = "Test",
        accountType = type,
        businessPersonal = BusinessPersonal.PERSONAL,
        openingBalanceMinorUnits = 0,
        currentBalanceMinorUnits = 0,
        creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
        outstandingMinorUnits = if (type == AccountType.CREDIT_CARD) null else 0L,
        deleted = deleted,
        active = true,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    private suspend fun expenseTx(accountId: Long, amount: Long): Long = db.transactionDao().insert(
        TransactionEntity(
            occurredAtEpochMillis = now(),
            rawDateString = "",
            txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
            txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
            txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
            paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
            rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
            businessPersonal = BusinessPersonal.PERSONAL,
            merchantReceiverSender = "Test",
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
            dedupeFingerprint = "tx-$accountId-$amount",
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L
        )
    )

    @Test
    fun accountWithPendingReimbursementsCannotBeHardDeleted() = runBlocking {
        val id = db.accountDao().insert(account(type = AccountType.BANK, sourceAccountId = "reimb")) + 0L
        val txId = db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = now(),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.CASH,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.CASH.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Reimbursable",
                categoryId = null,
                rawCategoryName = null,
                accountId = id,
                creditMinorUnits = 0,
                debitMinorUnits = 1000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = true,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "reimb-tx",
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )
        val check = repository.checkDeletion(id)
        assertFalse(check.canDelete)
        assertTrue(check.hasPendingReimbursements)
        assertTrue(check.reason!!.contains("Pending reimbursements"))
    }

    @Test
    fun accountWithBillAllocationsCannotBeHardDeleted() = runBlocking {
        val bankId = db.accountDao().insert(account(type = AccountType.BANK, sourceAccountId = "bill-alloc")) + 0L
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Rent",
                billerType = BillerType.OTHER,
                accountReferenceId = "x1",
                nickname = "Rent",
                linkedAccountId = bankId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 500,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )
        val instanceId = db.billDao().insertInstance(
            com.moneymanager.app.data.local.entity.BillInstanceEntity(
                billId = billId,
                dueDateEpochMillis = now() + 1000,
                amountDueMinorUnits = 500
            )
        )
        db.billDao().insertAllocation(
            BillPaymentAllocationEntity(
                billInstanceId = instanceId,
                transactionId = expenseTx(bankId, 500),
                amountMinorUnits = 500,
                paidAtEpochMillis = now(),
                createdAtEpochMillis = now()
            )
        )
        val check = repository.checkDeletion(bankId)
        assertFalse(check.canDelete)
        assertTrue(check.hasAllocations)
        assertTrue(check.hasLinkedBills)
    }

    @Test
    fun linkedDebitCardCountIsReported() = runBlocking {
        val bankId = db.accountDao().insert(account(type = AccountType.BANK, sourceAccountId = "parent")) + 0L
        db.accountDao().insert(
            account(type = AccountType.DEBIT_CARD, sourceAccountId = "card1", id = 0L).copy(parentAccountId = bankId)
        )
        db.accountDao().insert(
            account(type = AccountType.DEBIT_CARD, sourceAccountId = "card2", id = 0L).copy(parentAccountId = bankId)
        )
        val check = repository.checkDeletion(bankId)
        assertFalse(check.canDelete)
        assertEquals(2, check.linkedDebitCardsCount)
        assertTrue(check.reason!!.contains("Linked debit cards"))
    }

    @Test
    fun accountWithLinkedBillsCannotBeHardDeleted() = runBlocking {
        val bankId = db.accountDao().insert(account(type = AccountType.BANK, sourceAccountId = "bill-linked")) + 0L
        db.billDao().insert(
            BillEntity(
                billerName = "Electricity",
                billerType = BillerType.ELECTRICITY,
                accountReferenceId = "x1",
                nickname = "Electricity",
                linkedAccountId = bankId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 1000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )
        val check = repository.checkDeletion(bankId)
        assertFalse(check.canDelete)
        assertTrue(check.hasLinkedBills)
    }

    @Test
    fun reasonSummarizesMultipleDependencies() = runBlocking {
        val id = db.accountDao().insert(account(type = AccountType.BANK, sourceAccountId = "multi")) + 0L
        expenseTx(id, 500)
        db.billDao().insert(
            BillEntity(
                billerName = "Biller",
                billerType = BillerType.OTHER,
                accountReferenceId = "x1",
                nickname = "Biller",
                linkedAccountId = id,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 100,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )
        val check = repository.checkDeletion(id)
        assertFalse(check.canDelete)
        assertTrue(check.reason!!.contains("Transactions"))
        assertTrue(check.reason!!.contains("Linked bills"))
        assertTrue(check.reason!!.contains("hide/archive"))
    }
}
