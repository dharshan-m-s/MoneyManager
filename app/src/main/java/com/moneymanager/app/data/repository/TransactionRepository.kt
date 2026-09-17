package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class NewTransactionInput(
    val occurredAtEpochMillis: Long,
    val accountId: Long,
    val categoryId: Long?,
    val rawCategoryName: String?,
    val merchantReceiverSender: String?,
    val amount: Money,
    val isIncome: Boolean,
    val paymentType: PaymentType,
    val businessPersonal: BusinessPersonal,
    val notes: String?,
    val reimbursable: Boolean,
    val includeInStatistics: Boolean = true
)

@Singleton
class TransactionRepository @Inject constructor(
    private val database: MoneyManagerDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountingService: AccountingService,
    private val backupManager: BackupManager
) {
    suspend fun addTransaction(input: NewTransactionInput): Long {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val credit = if (input.isIncome) input.amount.minorUnits else 0L
            val debit = if (input.isIncome) 0L else input.amount.minorUnits
            val fingerprint = fingerprint(
                input.occurredAtEpochMillis, input.accountId, credit, debit,
                input.merchantReceiverSender, input.rawCategoryName
            )
            val entity = TransactionEntity(
                occurredAtEpochMillis = input.occurredAtEpochMillis,
                rawDateString = "",
                txnType = if (input.isIncome) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
                txnSubType = if (input.isIncome) TxnSubType.INCOME else TxnSubType.EXPENSE,
                txnKind = if (input.paymentType == PaymentType.CASH) {
                    if (input.isIncome) TxnKind.CASH_INCOME else TxnKind.CASH_SPEND
                } else TxnKind.REGULAR,
                paymentType = input.paymentType,
                rawPaymentType = input.paymentType.raw,
                businessPersonal = input.businessPersonal,
                merchantReceiverSender = input.merchantReceiverSender,
                categoryId = input.categoryId,
                rawCategoryName = input.rawCategoryName,
                accountId = input.accountId,
                creditMinorUnits = credit,
                debitMinorUnits = debit,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = input.notes,
                reimbursable = input.reimbursable,
                reimbursed = false,
                includeInStatistics = input.includeInStatistics,
                isHistoricalImport = false,
                dedupeFingerprint = fingerprint,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
            val id = transactionDao.insert(entity)
            accountingService.recalculateAccounts(setOf(input.accountId))
            id
        }.also { backupManager.scheduleAfterWrite() }
    }

    /**
     * Edits only fields that are safe to change from the UI. The original transaction kind,
     * subtype, account, payment type and imported statement snapshots are deliberately retained.
     * This is important for credit cards: a purchase must continue to increase outstanding while
     * a CC bill-payment must continue to reduce outstanding after an amount edit.
     */
    suspend fun updateEditableTransaction(
        transactionId: Long,
        amount: Money,
        merchantReceiverSender: String?,
        notes: String?,
        businessPersonal: BusinessPersonal,
        reimbursable: Boolean,
        includeInStatistics: Boolean,
        categoryId: Long? = null
    ) {
        database.withTransaction {
            val existing = transactionDao.findById(transactionId) ?: return@withTransaction
            val magnitude = amount.abs().minorUnits
            val (credit, debit) = when {
                existing.creditMinorUnits != 0L -> {
                    val sign = if (existing.creditMinorUnits < 0L) -1L else 1L
                    sign * magnitude to 0L
                }
                existing.debitMinorUnits != 0L -> {
                    val sign = if (existing.debitMinorUnits < 0L) -1L else 1L
                    0L to sign * magnitude
                }
                else -> 0L to 0L
            }
            val categoryName = categoryId?.let { categoryDao.findById(it)?.name }
            val fingerprint = fingerprint(
                existing.occurredAtEpochMillis, existing.accountId, credit, debit,
                merchantReceiverSender, categoryName ?: existing.rawCategoryName
            )
            transactionDao.update(
                existing.copy(
                    creditMinorUnits = credit,
                    debitMinorUnits = debit,
                    merchantReceiverSender = merchantReceiverSender,
                    notes = notes,
                    categoryId = categoryId,
                    rawCategoryName = categoryName ?: existing.rawCategoryName,
                    businessPersonal = businessPersonal,
                    reimbursable = reimbursable,
                    includeInStatistics = includeInStatistics,
                    dedupeFingerprint = fingerprint,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
            accountingService.recalculateAccounts(setOf(existing.accountId))
        }.also { backupManager.scheduleAfterWrite() }
    }

    suspend fun markReimbursed(transactionId: Long) {
        val txn = transactionDao.findById(transactionId) ?: return
        transactionDao.update(txn.copy(reimbursed = true, updatedAtEpochMillis = System.currentTimeMillis()))
        backupManager.scheduleAfterWrite()
    }

    suspend fun createTransfer(
        occurredAtEpochMillis: Long,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Money,
        notes: String?
    ): Pair<Long, Long> {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val outFingerprint = fingerprint(occurredAtEpochMillis, fromAccountId, 0, amount.minorUnits, "Transfer", "A/c to A/c")
            val inFingerprint = fingerprint(occurredAtEpochMillis, toAccountId, amount.minorUnits, 0, "Transfer", "A/c to A/c")
            val outEntity = TransactionEntity(
                occurredAtEpochMillis = occurredAtEpochMillis,
                rawDateString = "",
                txnType = TxnType.DEBIT_TRANSACTION,
                txnSubType = TxnSubType.TRANSFER_OUT,
                txnKind = TxnKind.REGULAR,
                paymentType = PaymentType.ONLINE_TRANSFER,
                rawPaymentType = PaymentType.ONLINE_TRANSFER.raw,
                businessPersonal = BusinessPersonal.PERSONAL,
                merchantReceiverSender = "Transfer",
                categoryId = null,
                rawCategoryName = "A/c to A/c",
                accountId = fromAccountId,
                creditMinorUnits = 0,
                debitMinorUnits = amount.minorUnits,
                balanceSnapshotMinorUnits = null,
                outstandingSnapshotMinorUnits = null,
                availableLimitSnapshotMinorUnits = null,
                notes = notes,
                reimbursable = false,
                reimbursed = false,
                isHistoricalImport = false,
                dedupeFingerprint = outFingerprint,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
            val outId = transactionDao.insert(outEntity)
            val inEntity = outEntity.copy(
                id = 0,
                txnType = TxnType.CREDIT_TRANSACTION,
                txnSubType = TxnSubType.TRANSFER_IN,
                accountId = toAccountId,
                creditMinorUnits = amount.minorUnits,
                debitMinorUnits = 0,
                dedupeFingerprint = inFingerprint,
                linkedTransferTransactionId = outId
            )
            val inId = transactionDao.insert(inEntity)
            transactionDao.update(outEntity.copy(id = outId, linkedTransferTransactionId = inId))
            accountingService.recalculateAccounts(setOf(fromAccountId, toAccountId))
            outId to inId
        }.also { backupManager.scheduleAfterWrite() }
    }

    private fun fingerprint(
        epochMillis: Long,
        accountId: Long,
        credit: Long,
        debit: Long,
        merchant: String?,
        category: String?
    ): String {
        val basis = listOf(
            epochMillis.toString(), accountId.toString(), credit.toString(), debit.toString(),
            merchant ?: "", category ?: "", System.nanoTime().toString()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(basis.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
