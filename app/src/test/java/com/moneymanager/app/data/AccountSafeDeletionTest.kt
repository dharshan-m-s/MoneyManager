package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillPaymentAllocationEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.repository.AccountRepository
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.BillRepository
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountSafeDeletionTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var repository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var billRepository: BillRepository
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
        transactionRepository = TransactionRepository(
            database = db,
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            billDao = db.billDao(),
            accountingService = db.accountingService(),
            backupManager = backupManager,
            attachmentRepository = com.moneymanager.app.data.repository.AttachmentRepository(
                appContext, db.transactionAttachmentDao()
            ),
            billRepository = BillRepository(
                database = db,
                billDao = db.billDao(),
                transactionDao = db.transactionDao(),
                accountDao = db.accountDao(),
                accountingService = db.accountingService(),
                billRecurrenceService = db.billRecurrenceService(),
                backupManager = backupManager
            )
        )
        billRepository = BillRepository(
            database = db,
            billDao = db.billDao(),
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            accountingService = db.accountingService(),
            billRecurrenceService = db.billRecurrenceService(),
            backupManager = backupManager
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun baseAccount(
        id: Long = 0L,
        type: AccountType = AccountType.BANK,
        sourceAccountId: String = "acc-$id",
        current: Long = 0L,
        outstanding: Long? = null,
        deleted: Boolean = false
    ): AccountEntity = AccountEntity(
        id = id,
        sourceAccountId = sourceAccountId,
        institutionName = "Test",
        nickname = "Test",
        accountType = type,
        businessPersonal = BusinessPersonal.PERSONAL,
        openingBalanceMinorUnits = 0,
        currentBalanceMinorUnits = current,
        creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 100_000L else null,
        outstandingMinorUnits = outstanding,
        outstandingCheckpointMinorUnits = outstanding,
        outstandingCheckpointAtEpochMillis = if (outstanding != null) now() else null,
        outstandingAdjustmentMinorUnits = outstanding,
        deleted = deleted,
        active = true,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )

    private suspend fun expenseTx(
        accountId: Long,
        amount: Long,
        id: Long = 0L
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            id = id,
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
            dedupeFingerprint = "tx-$id",
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L
        )
    )

    @Test
    fun emptyBankAccountIsHardDeleted() = runBlocking {
        val id = db.accountDao().insert(baseAccount(type = AccountType.BANK, sourceAccountId = "bank-empty")) + 0L
        val account = db.accountDao().findById(id)!!
        val createdId = account.id
        val result = repository.deleteAccountSafely(createdId)
        assertTrue(result.isSuccess)
        assertEquals(result.getOrThrow(), true)
        assertNull(db.accountDao().findById(createdId))
    }

    @Test
    fun accountWithTransactionsIsArchivedNotHardDeleted() = runBlocking {
        val id = db.accountDao().insert(baseAccount(type = AccountType.BANK, sourceAccountId = "bank-history")) + 0L
        expenseTx(id, 500)
        db.accountingService().recalculateAccounts(setOf(id))

        val check = repository.checkDeletion(id)
        assertFalse(check.canDelete)
        assertTrue(check.hasTransactions)

        val result = repository.deleteAccountSafely(id)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())

        val stillThere = db.accountDao().findById(id)
        assertNotNull(stillThere)
        assertTrue(stillThere!!.deleted)
        assertFalse(stillThere!!.active)
        assertTrue(stillThere!!.hide)
        assertEquals(1, db.transactionDao().countByAccountId(id))
    }

    @Test
    fun accountWithLinkedDebitCardsIsArchivedNotHardDeleted() = runBlocking {
        val bankId = db.accountDao().insert(baseAccount(type = AccountType.BANK, sourceAccountId = "bank-parent")) + 0L
        db.accountDao().insert(
            baseAccount(
                type = AccountType.DEBIT_CARD,
                sourceAccountId = "card",
                current = 0L
            ).copy(id = 0L, parentAccountId = bankId)
        )
        db.accountingService().recalculateAccounts(setOf(bankId))

        val check = repository.checkDeletion(bankId)
        assertFalse(check.canDelete)
        assertEquals(1, check.linkedDebitCardsCount)

        val result = repository.deleteAccountSafely(bankId)
        assertTrue(result.isSuccess)
        val bank = db.accountDao().findById(bankId)!!
        assertTrue(bank.deleted)
        // The debit card itself must still exist.
        assertTrue(db.accountDao().findAll().any { it.parentAccountId == bankId })
    }

    @Test
    fun creditCardWithNoHistoryDeletesLinkedBillThenCard() = runBlocking {
        val cardId = db.accountDao().insert(
            baseAccount(
                type = AccountType.CREDIT_CARD,
                sourceAccountId = "card-clean",
                outstanding = 0L
            )
        ) + 0L
        val card = db.accountDao().findById(cardId)!!
        assertNotNull(card)

        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card Biller",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "1234",
                nickname = "Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 0,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )

        val result = repository.deleteAccountSafely(cardId)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertNull(db.accountDao().findById(cardId))
        assertNull(db.billDao().findById(billId))
    }

    @Test
    fun creditCardWithTransactionsIsArchivedAndLinkedBillStayedUntilHistoryGone() = runBlocking {
        val cardId = db.accountDao().insert(
            baseAccount(
                type = AccountType.CREDIT_CARD,
                sourceAccountId = "card-history",
                outstanding = 1000L
            )
        ) + 0L
        expenseTx(cardId, 1000)

        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card Biller",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "1234",
                nickname = "Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 1000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )
        db.accountingService().recalculateAccounts(setOf(cardId))

        val check = repository.checkDeletion(cardId)
        assertFalse(check.canDelete)
        assertTrue(check.hasTransactions)

        val result = repository.deleteAccountSafely(cardId)
        assertTrue(result.isSuccess)
        val card = db.accountDao().findById(cardId)!!
        assertTrue(card.deleted)
        assertFalse(card.active)
        assertTrue(card.hide)
        // History is preserved.
        assertEquals(1, db.transactionDao().countByAccountId(cardId))
    }

    @Test
    fun accountWithBillPaymentsIsArchivedNotHardDeleted() = runBlocking {
        val bankId = db.accountDao().insert(baseAccount(type = AccountType.BANK, sourceAccountId = "bank-billpay")) + 0L
        val txId = expenseTx(bankId, 500)
        db.billDao().insert(
            BillEntity(
                billerName = "Rent",
                billerType = BillerType.OTHER,
                accountReferenceId = "x1",
                nickname = "Rent",
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
                billId = db.billDao().findAll().last().id,
                dueDateEpochMillis = now() + 1000,
                amountDueMinorUnits = 500
            )
        )
        // Mark the existing transaction as the payment for the bill.
        billRepository.markAsPaid(db.billDao().findInstanceById(instanceId)!!, txId)

        val check = repository.checkDeletion(bankId)
        assertFalse(check.canDelete)
        assertTrue(check.hasAllocations)

        val result = repository.deleteAccountSafely(bankId)
        assertTrue(result.isSuccess)
        val account = db.accountDao().findById(bankId)!!
        assertTrue(account.deleted)
        // Historical payment stays.
        assertEquals(1, db.transactionDao().countByAccountId(bankId))
    }

    @Test
    fun emptyAccountCanBeHardDeleted() = runBlocking {
        // Exercises the runCatching path by deleting an account that exists.
        val id = db.accountDao().insert(baseAccount(type = AccountType.CASH, sourceAccountId = "cash-safe")) + 0L
        val result = repository.deleteAccountSafely(id)
        assertTrue(result.isSuccess)
        assertNull(db.accountDao().findById(id))
    }

    @Test
    fun accountWithTransactionsIsArchivedNotHardDeletedAndHistoryIsPreserved() = runBlocking {
        val id = db.accountDao().insert(baseAccount(type = AccountType.BANK, sourceAccountId = "bank-history-preserved")) + 0L
        val txId = expenseTx(id, 750)
        db.accountingService().recalculateAccounts(setOf(id))

        val check = repository.checkDeletion(id)
        assertFalse(check.canDelete)
        assertTrue(check.hasTransactions)

        val result = repository.deleteAccountSafely(id)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())

        val archived = db.accountDao().findById(id)!!
        assertTrue(archived.deleted)
        assertFalse(archived.active)
        assertTrue(archived.hide)
        // The persisted transaction count and the allocated transaction both survive.
        assertEquals(1, db.transactionDao().countByAccountId(id))
        assertNotNull(db.transactionDao().findById(txId))
    }

    @Test
    fun safeDeleteReturnsTrueWhenArchived() = runBlocking {
        val id = db.accountDao().insert(baseAccount(type = AccountType.LOAN, sourceAccountId = "loan-archive")) + 0L
        db.transactionDao().insert(
            TransactionEntity(
                occurredAtEpochMillis = now(),
                rawDateString = "",
                txnType = com.moneymanager.app.domain.model.TxnType.DEBIT_TRANSACTION,
                txnSubType = com.moneymanager.app.domain.model.TxnSubType.EXPENSE,
                txnKind = com.moneymanager.app.domain.model.TxnKind.REGULAR,
                paymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING,
                rawPaymentType = com.moneymanager.app.domain.model.PaymentType.NETBANKING.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Loan payment",
                categoryId = null,
                rawCategoryName = null,
                accountId = id,
                creditMinorUnits = 0,
                debitMinorUnits = 1000,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = null,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = "loan-tx",
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )

        val result = repository.deleteAccountSafely(id)
        assertTrue("An account with history is safely archived, not silently dropped", result.isSuccess)
        assertTrue(result.getOrThrow())
        val archived = db.accountDao().findById(id)!!
        assertTrue(archived.deleted)
        assertFalse(archived.active)
    }

    @Test
    fun linkedCreditCardBillIsRemovedOnlyWhenCardHasNoTransactions() = runBlocking {
        val cardId = db.accountDao().insert(
            baseAccount(
                type = AccountType.CREDIT_CARD,
                sourceAccountId = "card-clean-bill",
                outstanding = 0L
            )
        ) + 0L
        val billId = db.billDao().insert(
            BillEntity(
                billerName = "Card Biller",
                billerType = BillerType.CREDIT_CARD,
                accountReferenceId = "9876",
                nickname = "Card",
                linkedAccountId = cardId,
                billingCycle = BillingCycle.MONTHLY,
                nextDueDateEpochMillis = now() + 1000,
                estimatedAmountMinorUnits = 2000,
                businessPersonal = BusinessPersonal.PERSONAL,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L
            )
        )

        val result = repository.deleteAccountSafely(cardId)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertNull(db.accountDao().findById(cardId))
        assertNull(db.billDao().findById(billId))
    }
}

