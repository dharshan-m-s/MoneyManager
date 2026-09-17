package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
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
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val billDao: BillDao,
    private val accountingService: AccountingService,
    private val backupManager: BackupManager,
    private val attachmentRepository: AttachmentRepository
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


    /** Updates all user-editable transaction fields (amount, description, category, date,
     *  account, payment type, flags). Historical snapshot fields stay intact. When the account
     *  changes, both the old and new accounts are recalculated by the canonical engine; when a
     *  category is chosen, the human-readable rawCategoryName is synced from the categories
     *  table so the detail screens keep showing a sensible name. */
    suspend fun updateEditableTransaction(
        transactionId: Long,
        amount: Money? = null,
        merchantReceiverSender: String? = null,
        notes: String? = null,
        businessPersonal: BusinessPersonal? = null,
        reimbursable: Boolean? = null,
        includeInStatistics: Boolean? = null,
        categoryId: Long? = null,
        occurredAtEpochMillis: Long? = null,
        accountId: Long? = null,
        paymentType: PaymentType? = null,
        /** Set only when the user explicitly switched this row between income and expense.
         *  Null means "keep the existing direction and sign". Ignored for transfers. */
        isIncome: Boolean? = null
    ) {
        database.withTransaction {
            val existing = transactionDao.findById(transactionId) ?: return@withTransaction
            val isTransfer = existing.txnSubType == TxnSubType.TRANSFER_IN || existing.txnSubType == TxnSubType.TRANSFER_OUT
            val amountValue = amount ?: Money(if (existing.creditMinorUnits != 0L) existing.creditMinorUnits else -existing.debitMinorUnits)
            var credit = if (existing.creditMinorUnits != 0L) {
                if (existing.creditMinorUnits < 0L) -amountValue.minorUnits else amountValue.minorUnits
            } else 0L
            var debit = if (existing.debitMinorUnits != 0L) {
                if (existing.debitMinorUnits < 0L) -amountValue.minorUnits else amountValue.minorUnits
            } else 0L
            var newTxnType = existing.txnType
            var newTxnSubType = existing.txnSubType
            var newTxnKind = existing.txnKind
            if (isIncome != null && !isTransfer) {
                val magnitude = amountValue.abs().minorUnits
                credit = if (isIncome) magnitude else 0L
                debit = if (isIncome) 0L else magnitude
                newTxnType = if (isIncome) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION
                newTxnSubType = if (isIncome) TxnSubType.INCOME else TxnSubType.EXPENSE
                newTxnKind = if (existing.paymentType == PaymentType.CASH) {
                    if (isIncome) TxnKind.CASH_INCOME else TxnKind.CASH_SPEND
                } else TxnKind.REGULAR
            }
            val newCategoryId = if (categoryId == null && existing.categoryId != null) existing.categoryId else categoryId
            val categoryName = newCategoryId?.let { categoryDao.findById(it)?.name } ?: existing.rawCategoryName
            val newAccountId = accountId ?: existing.accountId
            val newDate = occurredAtEpochMillis ?: existing.occurredAtEpochMillis
            val newPaymentType = paymentType ?: existing.paymentType
            val fingerprint = fingerprint(newDate, newAccountId, credit, debit, merchantReceiverSender ?: existing.merchantReceiverSender, categoryName)
            transactionDao.update(existing.copy(
                occurredAtEpochMillis = newDate,
                txnType = newTxnType,
                txnSubType = newTxnSubType,
                txnKind = newTxnKind,
                creditMinorUnits = credit, debitMinorUnits = debit,
                merchantReceiverSender = merchantReceiverSender ?: existing.merchantReceiverSender,
                notes = notes ?: existing.notes,
                categoryId = newCategoryId,
                rawCategoryName = categoryName,
                accountId = newAccountId,
                paymentType = newPaymentType,
                rawPaymentType = if (paymentType != null) newPaymentType.raw else existing.rawPaymentType,
                businessPersonal = businessPersonal ?: existing.businessPersonal,
                reimbursable = reimbursable ?: existing.reimbursable,
                includeInStatistics = includeInStatistics ?: existing.includeInStatistics,
                dedupeFingerprint = fingerprint,
                updatedAtEpochMillis = System.currentTimeMillis()
            ))
            val affected = if (newAccountId != existing.accountId) setOf(existing.accountId, newAccountId) else setOf(existing.accountId)
            accountingService.recalculateAccounts(affected)
        }.also { backupManager.scheduleAfterWrite() }
    }

    /** Permanently deletes a transaction and everything linked to it: the other leg of a
     *  transfer, and any bill-instance payment marker (so a linked bill returns to unpaid and
     *  never shows a stale payment). The owning account (and the peer leg's account for
     *  transfers) is recalculated through the canonical engine afterwards. */
    suspend fun deleteTransaction(transactionId: Long) {
        database.withTransaction {
            val existing = transactionDao.findById(transactionId) ?: return@withTransaction
            val affected = linkedAccountIdsFor(existing)
            val peerIdForAttachments = existing.linkedTransferTransactionId

            // Un-link a credit-card/bill payment so the bill instance returns to unpaid.
            billDao.findByPaymentTransactionId(transactionId)?.let { instance ->
                billDao.updateInstance(instance.copy(paid = false, paidAtEpochMillis = null, paymentTransactionId = null))
            }

            transactionDao.deleteById(transactionId)

            // Delete the paired transfer leg too, keeping the linkedTransferTransactionId
            // columns consistent on both sides.
            peerIdForAttachments?.let { peerId ->
                transactionDao.findById(peerId)?.let { peer ->
                    val moreAffected = linkedAccountIdsFor(peer)
                    transactionDao.deleteById(peerId)
                    affected.addAll(moreAffected)
                }
            }

            accountingService.recalculateAccounts(affected)

            // Remove the receipt image files too - the attachment ROWS cascade with the
            // transaction, but the bytes live on disk and would otherwise be orphaned.
            attachmentRepository.deleteFilesFor(transactionId)
            peerIdForAttachments?.let { attachmentRepository.deleteFilesFor(it) }
        }.also { backupManager.scheduleAfterWrite() }
    }

    private suspend fun linkedAccountIdsFor(txn: TransactionEntity): MutableSet<Long> =
        mutableSetOf(txn.accountId)

    /** Soft-deletes a credit-card account (keeps the row for audit) and removes any biller
     *  linked to it so no orphan bill keeps referencing the card. Historical transactions stay
     *  in place; aggregate balance queries already exclude `deleted` accounts. */
    suspend fun deleteCreditCardAccount(accountId: Long) {
        database.withTransaction {
            billDao.deleteByLinkedAccountId(accountId)
            accountDao.softDeleteById(accountId, System.currentTimeMillis())
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
