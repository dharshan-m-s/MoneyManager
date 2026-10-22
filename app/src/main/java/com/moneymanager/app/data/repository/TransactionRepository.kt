package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.CategoryDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
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
    private val attachmentRepository: AttachmentRepository,
    private val billRepository: BillRepository
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

            val isCard = accountDao.findById(input.accountId)?.accountType == AccountType.CREDIT_CARD
            val entity = TransactionEntity(
                occurredAtEpochMillis = input.occurredAtEpochMillis,
                rawDateString = "",
                txnType = if (input.isIncome) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
                txnSubType = if (input.isIncome) TxnSubType.INCOME else TxnSubType.EXPENSE,
                // Money credited BACK to a credit card is a refund, not income: it reduces
                // what the card owes and must never inflate the app's income totals. The
                // refund-reversal kind is exactly that meaning in the engine's decision table.
                txnKind = when {
                    isCard && input.isIncome -> TxnKind.REFUND_REVERSAL
                    input.paymentType == PaymentType.CASH ->
                        if (input.isIncome) TxnKind.CASH_INCOME else TxnKind.CASH_SPEND
                    else -> TxnKind.REGULAR
                },
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
     *  table so the detail screens keep showing a sensible name.
     *
     *  LINKED-TRANSFER CONTRACT (spec section 4): a pair shares two facts - the amount and the
     *  date. Editing either applies to BOTH legs inside this one database transaction, each leg
     *  keeping its own credit/debit placement, so the pair can never drift out of balance and
     *  net worth cannot change from an edit. Everything else (merchant, notes, payment method,
     *  flags) stays per leg. A leg may be re-pointed to a different account, but never onto the
     *  account receiving the other side. Credit-card bill-payment pairs reject amount changes
     *  here - that amount is owned by the bill-payment model (section 3) and must change
     *  through it, so an edit cannot desynchronise the payment allocations.
     *
     *  When [amount] is null the stored magnitude and its sign are left exactly as they were;
     *  the caller does not silently flip a signed row. */
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
            val existing = transactionDao.findById(transactionId)
                ?: throw NoSuchElementException("This transaction no longer exists")
            val now = System.currentTimeMillis()
            val isTransfer = existing.txnSubType == TxnSubType.TRANSFER_IN || existing.txnSubType == TxnSubType.TRANSFER_OUT
            val peer = if (isTransfer) {
                existing.linkedTransferTransactionId?.let { transactionDao.findById(it) }
            } else null
            val isBillPaymentPair = existing.isCreditCardBillPayment || existing.paysOffAccountId != null ||
                peer?.isCreditCardBillPayment == true || (peer != null && peer.paysOffAccountId != null)

            if (accountId != null && peer != null && accountId == peer.accountId) {
                throw IllegalArgumentException(
                    "A transfer cannot move into the account that receives its other side"
                )
            }
            // Re-pointing a leg that SETTLES a bill would desynchronise the bill from the
            // card it pays, so it is refused outright rather than silently half-applied.
            // A card payment that was never linked to a bill may still be moved - the card's
            // outstanding is restored by the re-derivation below.
            if (accountId != null && isBillPaymentPair) {
                val settlesBill = billRepository.instanceIdsReferencingTransactions(
                    setOfNotNull(existing.id, peer?.id)
                ).isNotEmpty()
                require(!settlesBill) {
                    "A payment that settles a bill cannot be moved to another account - edit the payment on the bill instead"
                }
            }
            val currentMagnitude = if (existing.creditMinorUnits != 0L) {
                kotlin.math.abs(existing.creditMinorUnits)
            } else {
                kotlin.math.abs(existing.debitMinorUnits)
            }
            val magnitude = kotlin.math.abs(amount?.minorUnits ?: currentMagnitude)
            if (amount != null && isBillPaymentPair && magnitude != currentMagnitude) {
                throw IllegalArgumentException(
                    "The amount of a credit-card bill payment is managed by the bill - edit the payment there"
                )
            }

            var credit: Long
            var debit: Long
            var newTxnType = existing.txnType
            var newTxnSubType = existing.txnSubType
            var newTxnKind = existing.txnKind
            if (isIncome != null && !isTransfer) {
                credit = if (isIncome) magnitude else 0L
                debit = if (isIncome) 0L else magnitude
                newTxnType = if (isIncome) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION
                newTxnSubType = if (isIncome) TxnSubType.INCOME else TxnSubType.EXPENSE
                newTxnKind = if (existing.paymentType == PaymentType.CASH) {
                    if (isIncome) TxnKind.CASH_INCOME else TxnKind.CASH_SPEND
                } else TxnKind.REGULAR
            } else {
                // Keep each side's own placement and sign: credit rows keep their credit sign,
                // debit rows their debit sign, so an edit can never invert a stored row.
                credit = if (existing.creditMinorUnits != 0L) {
                    if (existing.creditMinorUnits < 0L) -magnitude else magnitude
                } else 0L
                debit = if (existing.debitMinorUnits != 0L) {
                    if (existing.debitMinorUnits < 0L) -magnitude else magnitude
                } else 0L
            }
            val newCategoryId = if (categoryId == null && existing.categoryId != null) existing.categoryId else categoryId
            val categoryName = newCategoryId?.let { categoryDao.findById(it)?.name } ?: existing.rawCategoryName
            val newAccountId = accountId ?: existing.accountId
            val newDate = occurredAtEpochMillis ?: existing.occurredAtEpochMillis
            val newPaymentType = paymentType ?: existing.paymentType
            // Card semantics belong to the ACCOUNT the leg lives on, not to the row: a leg
            // re-pointed onto a card becomes a card payment, and a card leg moved to a bank
            // stops being one. Re-deriving here keeps the card's outstanding correct even
            // though the edit UI does not (yet) expose re-pointing.
            val cardSemantics = if (isTransfer && newAccountId != existing.accountId) {
                deriveCardSemantics(newAccountId, newTxnSubType)
            } else null
            if (cardSemantics != null) {
                newTxnKind = cardSemantics.txnKind
            }
            val isCardBillPayment = cardSemantics?.isCreditCardBillPayment ?: existing.isCreditCardBillPayment
            val paysOffAccountId = cardSemantics?.paysOffAccountId
                ?: if (cardSemantics == null) existing.paysOffAccountId else null
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
                isCreditCardBillPayment = isCardBillPayment,
                paysOffAccountId = paysOffAccountId,
                dedupeFingerprint = fingerprint,
                updatedAtEpochMillis = now
            ))

            // Pair-level mirror: the other leg follows the amount and the date together with
            // this one, keeping its own credit/debit placement, inside the same transaction.
            if (peer != null && (amount != null || occurredAtEpochMillis != null)) {
                val peerDate = if (occurredAtEpochMillis != null) newDate else peer.occurredAtEpochMillis
                val peerMagnitude = if (amount != null) magnitude else if (peer.creditMinorUnits != 0L) {
                    kotlin.math.abs(peer.creditMinorUnits)
                } else {
                    kotlin.math.abs(peer.debitMinorUnits)
                }
                val peerCredit = if (peer.creditMinorUnits != 0L) {
                    if (peer.creditMinorUnits < 0L) -peerMagnitude else peerMagnitude
                } else 0L
                val peerDebit = if (peer.debitMinorUnits != 0L) {
                    if (peer.debitMinorUnits < 0L) -peerMagnitude else peerMagnitude
                } else 0L
                transactionDao.update(peer.copy(
                    occurredAtEpochMillis = peerDate,
                    creditMinorUnits = peerCredit,
                    debitMinorUnits = peerDebit,
                    dedupeFingerprint = fingerprint(
                        peerDate, peer.accountId, peerCredit, peerDebit,
                        peer.merchantReceiverSender, peer.rawCategoryName
                    ),
                    updatedAtEpochMillis = now
                ))
            }

            // Recalculate every account either leg touched: old and new account of this leg,
            // plus the peer's account whenever the peer row changed with it.
            val affected = mutableSetOf(existing.accountId, newAccountId)
            peer?.let { affected.add(it.accountId) }
            accountingService.recalculateAccounts(affected)
        }.also { backupManager.scheduleAfterWrite() }
    }

    /** Permanently deletes a transaction and everything linked to it: the other leg of a
     *  transfer, and every bill-payment allocation backed by it (cascaded with the row), then
     *  recomputes each affected occurrence from its surviving allocations - it returns to
     *  unpaid, stays partially paid, or remains settled as the remaining payments justify,
     *  never a blanket reset that would discard a surviving partial payment. */
    suspend fun deleteTransaction(transactionId: Long) {
        database.withTransaction {
            val existing = transactionDao.findById(transactionId) ?: return@withTransaction
            val affected = linkedAccountIdsFor(existing)
            val peerIdForAttachments = existing.linkedTransferTransactionId
            val linkedInstanceIds = billRepository.instanceIdsReferencingTransactions(
                setOfNotNull(transactionId, peerIdForAttachments)
            )

            transactionDao.deleteById(transactionId)

            // Delete the paired transfer leg too, keeping the linkedTransferTransactionId
            // columns consistent on both sides. A bill may be linked to either leg (a
            // card-side payment is the usual case).
            peerIdForAttachments?.let { peerId ->
                transactionDao.findById(peerId)?.let { peer ->
                    val moreAffected = linkedAccountIdsFor(peer)
                    transactionDao.deleteById(peerId)
                    affected.addAll(moreAffected)
                }
            }

            billRepository.recomputeInstancePaymentStates(linkedInstanceIds)
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
     *  exclude (see TransactionDao.observeTotalSpendInRange, which filters on EXPENSE only).
     *
     *  When the destination IS a credit card, the incoming leg is a card bill payment: money
     *  reaching a card settles what the card owes. Recording it that way is what makes the
     *  card's outstanding fall by the amount the user just paid, instead of the user having to
     *  re-type the outstanding by hand afterwards. */
    suspend fun createTransfer(
        occurredAtEpochMillis: Long,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Money,
        notes: String?
    ): Pair<Long, Long> {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val destination = accountDao.findById(toAccountId)
            val paysOffCard = destination?.accountType == AccountType.CREDIT_CARD
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
                // Money arriving on a credit card settles card debt; the classifier turns this
                // into a card payment (outstanding falls, no balance movement).
                txnKind = if (paysOffCard) TxnKind.CC_BILL_PAYMENT else TxnKind.REGULAR,
                accountId = toAccountId,
                creditMinorUnits = amount.minorUnits,
                debitMinorUnits = 0,
                dedupeFingerprint = inFingerprint,
                linkedTransferTransactionId = outId,
                isCreditCardBillPayment = paysOffCard,
                paysOffAccountId = if (paysOffCard) toAccountId else null
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

    /**
     * The card semantics a transfer leg must carry on a given account.
     *
     *  - money arriving on a card settles card debt: a card payment (kind cc-bill-payment),
     *  - money leaving a card is a cash advance: the classifier turns the plain transfer-out
     *    into a CARD_CASH_ADVANCE, so the leg keeps the neutral kind here,
     *  - anything on a non-card account is an ordinary transfer.
     */
    private suspend fun deriveCardSemantics(
        accountId: Long,
        subType: TxnSubType
    ): CardSemantics {
        val isCard = accountDao.findById(accountId)?.accountType == AccountType.CREDIT_CARD
        return when {
            isCard && subType == TxnSubType.TRANSFER_IN -> CardSemantics(
                txnKind = TxnKind.CC_BILL_PAYMENT,
                isCreditCardBillPayment = true,
                paysOffAccountId = accountId
            )
            else -> CardSemantics(
                txnKind = TxnKind.REGULAR,
                isCreditCardBillPayment = false,
                paysOffAccountId = null
            )
        }
    }

    private data class CardSemantics(
        val txnKind: TxnKind,
        val isCreditCardBillPayment: Boolean,
        val paysOffAccountId: Long?
    )

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
