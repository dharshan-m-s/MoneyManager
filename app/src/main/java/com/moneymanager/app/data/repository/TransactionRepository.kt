package com.moneymanager.app.data.repository

import androidx.room.withTransaction
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

/** Everything the Add Income / Add Expense / Add Cash Income / Add Cash Spend screens
 *  (spec section 17) need to create one new, app-native transaction. */
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
    /** "Spend ON/OFF" / "Income ON/OFF" toggle from the Add Transaction screen - whether this
     *  transaction contributes to the app's spend/income analytics totals. The ledger effect
     *  is unaffected; only the totals exclude it. Defaults to ON. */
    val includeInStatistics: Boolean = true
)

@Singleton
class TransactionRepository @Inject constructor(
    private val database: MoneyManagerDatabase,
    private val transactionDao: TransactionDao,
    private val accountingService: AccountingService,
    private val backupManager: BackupManager
) {
    /** Creates a new, live (non-historical) transaction and immediately applies its effect to
     *  the account's running balance via the app's own accounting engine - this is the path
     *  spec section 18's "Add Expense -> Transaction database -> Account balance updated ->
     *  ... -> Dashboard updated" data flow describes, distinct from the import path which
     *  preserves historical snapshots untouched. */
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
            // Recompute the account's derived balance through the ONE canonical accounting
            // engine - the same engine the CSV importer uses. No separate ledger formula.
            accountingService.recalculateAccounts(setOf(input.accountId))
            id
        }.also { backupManager.scheduleAfterWrite() }
    }


    /** Updates user-editable transaction fields while keeping the source row identity and all
     *  imported snapshot fields intact. After editing, the owning account is recalculated by the
     *  canonical accounting engine. */
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
            val isTransfer = existing.txnSubType == TxnSubType.TRANSFER_IN || existing.txnSubType == TxnSubType.TRANSFER_OUT
            val credit = if (isTransfer) existing.creditMinorUnits else if (existing.creditMinorUnits != 0L) {
                if (existing.creditMinorUnits < 0L) -amount.minorUnits else amount.minorUnits
            } else 0L
            val debit = if (isTransfer) existing.debitMinorUnits else if (existing.debitMinorUnits != 0L) {
                if (existing.debitMinorUnits < 0L) -amount.minorUnits else amount.minorUnits
            } else 0L
            val fingerprint = fingerprint(existing.occurredAtEpochMillis, existing.accountId, credit, debit, merchantReceiverSender, existing.rawCategoryName)
            transactionDao.update(existing.copy(
                creditMinorUnits = credit, debitMinorUnits = debit,
                merchantReceiverSender = merchantReceiverSender, notes = notes,
                categoryId = categoryId,
                rawCategoryName = existing.rawCategoryName,
                businessPersonal = businessPersonal, reimbursable = reimbursable,
                includeInStatistics = includeInStatistics, dedupeFingerprint = fingerprint,
                updatedAtEpochMillis = System.currentTimeMillis()
            ))
            accountingService.recalculateAccounts(setOf(existing.accountId))
        }.also { backupManager.scheduleAfterWrite() }
    }

    /** Marks a reimbursable transaction as reimbursed (spec section 12) without touching any
     *  other field - a targeted update, not a general-purpose edit path. */
    suspend fun markReimbursed(transactionId: Long) {
        val txn = transactionDao.findById(transactionId) ?: return
        transactionDao.update(txn.copy(reimbursed = true, updatedAtEpochMillis = System.currentTimeMillis()))
        backupManager.scheduleAfterWrite()
    }

    /** Creates a transfer (spec section 9) as two LINKED transactions - a transfer-out on the
     *  source account and a transfer-in on the destination account - rather than a single row.
     *  This is what keeps a transfer from inflating income/expense analytics: both legs use
     *  TxnSubType.TRANSFER_OUT / TRANSFER_IN, which the spend/income queries deliberately
     *  exclude (see TransactionDao.observeTotalSpendInRange, which filters on EXPENSE only). */
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

            // Link the out-side back to the in-side now that its id exists.
            transactionDao.update(outEntity.copy(id = outId, linkedTransferTransactionId = inId))

            // A transfer moves money WITHOUT creating or destroying net worth; both accounts
            // are recalculated by the same engine the importer uses.
            accountingService.recalculateAccounts(setOf(fromAccountId, toAccountId))

            outId to inId
        }.also { backupManager.scheduleAfterWrite() }
    }

    private fun fingerprint(
        epochMillis: Long, accountId: Long, credit: Long, debit: Long,
        merchant: String?, category: String?
    ): String {
        val basis = listOf(
            epochMillis.toString(), accountId.toString(), credit.toString(), debit.toString(),
            merchant ?: "", category ?: "", System.nanoTime().toString() // nanoTime keeps
            // app-entered transactions from ever fingerprint-colliding with each other or
            // with historical rows purely by coincidence of same amount/date/merchant.
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(basis.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
