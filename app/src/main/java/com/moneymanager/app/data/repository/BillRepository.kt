package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val database: MoneyManagerDatabase,
    private val billDao: BillDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val accountingService: AccountingService,
    private val billRecurrenceService: BillRecurrenceService,
    private val backupManager: BackupManager
) {
    /**
     * Marks an existing bill instance as paid by linking it to an already-recorded transaction.
     * Passing null means the user explicitly chose "Paid by Cash" (or recorded the movement
     * elsewhere); no ledger transaction is created in that case.
     *
     * For a credit-card bill where the chosen transaction is the bank side of a payment, the
     * card-side leg is required to reduce the outstanding correctly. If the transaction is not
     * already paired with a card leg, the missing leg is created once - the original bank
     * transaction is never duplicated. Repeated calls are idempotent.
     */
    suspend fun markAsPaid(instance: BillInstanceEntity, paymentTransactionId: Long?) {
        var paidBillId: Long? = null
        database.withTransaction {
            val current = billDao.findInstanceById(instance.id) ?: return@withTransaction
            // Already paid: never pay the same occurrence twice or re-link its history.
            if (current.paid) return@withTransaction

            var effectivePaymentId = paymentTransactionId
            var paidAt = System.currentTimeMillis()

            if (paymentTransactionId != null) {
                val payment = requireNotNull(transactionDao.findById(paymentTransactionId)) {
                    "Payment transaction no longer exists"
                }
                val linked = billDao.findByPaymentTransactionId(paymentTransactionId)
                require(linked == null || linked.id == instance.id) {
                    "Payment transaction is already linked to another bill"
                }
                paidAt = payment.occurredAtEpochMillis

                val bill = billDao.findById(current.billId)
                val cardId = bill?.linkedAccountId
                if (cardId != null && payment.accountId != cardId) {
                    // Resolve the card side: an existing CC bill-payment leg if present, else
                    // create the missing leg. The bank transaction itself is untouched.
                    val peer = payment.linkedTransferTransactionId?.let { transactionDao.findById(it) }
                    val existingCardLeg = peer?.takeIf {
                        it.accountId == cardId && it.paysOffAccountId == cardId
                    }
                    if (existingCardLeg != null) {
                        val linkedTo = billDao.findByPaymentTransactionId(existingCardLeg.id)
                        require(linkedTo == null || linkedTo.id == instance.id) {
                            "This payment is already linked to another bill"
                        }
                    } else if (payment.txnSubType == TxnSubType.EXPENSE) {
                        // A card bill payment is a transfer, not ordinary spending. The user has
                        // explicitly identified this row as the payment, so reclassify it rather
                        // than leaving it in the spend totals.
                        transactionDao.update(
                            payment.copy(
                                txnSubType = TxnSubType.TRANSFER_OUT,
                                txnKind = TxnKind.REGULAR,
                                rawCategoryName = payment.rawCategoryName ?: "A/c to A/c",
                                updatedAtEpochMillis = System.currentTimeMillis()
                            )
                        )
                        accountingService.recalculateAccounts(setOf(payment.accountId))
                    }
                    effectivePaymentId = existingCardLeg?.id ?: createCardSidePayment(
                        payment = payment,
                        cardAccountId = cardId,
                        cardName = bill.billerName
                    )
                }
            }

            billDao.updateInstance(
                current.copy(
                    paid = true,
                    paidAtEpochMillis = paidAt,
                    paymentTransactionId = effectivePaymentId
                )
            )
            paidBillId = current.billId
        }
        // Advance the schedule past the settled occurrence so the template's next due date
        // reflects the recurrence immediately, not at the next app start.
        paidBillId?.let { billRecurrenceService.reconcileBill(it) }
        backupManager.scheduleAfterWrite()
    }

    /**
     * Records a new payment for a credit-card bill as a proper two-legged transfer:
     *  - a TRANSFER_OUT on the source account (real money leaves it),
     *  - a CARD_PAYMENT (TRANSFER_IN + cc-bill-payment kind) on the card (outstanding falls),
     * and links the bill instance to the card leg, atomically.
     *
     * Returns the card-side transaction id. If the instance is already paid the existing
     * payment id is returned, so retries cannot create a second payment.
     */
    suspend fun recordCreditCardBillPayment(
        instanceId: Long,
        sourceAccountId: Long,
        amountMinorUnits: Long,
        occurredAtEpochMillis: Long,
        notes: String? = null
    ): Long {
        var paidBillId: Long? = null
        val result = database.withTransaction {
            val instance = requireNotNull(billDao.findInstanceById(instanceId)) {
                "Bill occurrence no longer exists"
            }
            if (instance.paid) return@withTransaction instance.paymentTransactionId ?: -1L
            val bill = requireNotNull(billDao.findById(instance.billId)) {
                "Bill no longer exists"
            }
            val cardId = requireNotNull(bill.linkedAccountId) {
                "This bill is not linked to a credit card"
            }
            val card = requireNotNull(accountDao.findById(cardId)) {
                "Credit card account no longer exists"
            }
            require(card.accountType == AccountType.CREDIT_CARD) {
                "Linked account is not a credit card"
            }
            require(sourceAccountId != cardId) { "Choose a different payment account" }
            val source = requireNotNull(accountDao.findById(sourceAccountId)) {
                "Payment account no longer exists"
            }
            val amount = when {
                amountMinorUnits > 0L -> amountMinorUnits
                instance.amountDueMinorUnits > 0L -> instance.amountDueMinorUnits
                else -> throw IllegalArgumentException("Enter a payment amount")
            }
            val whenMillis = occurredAtEpochMillis.coerceAtLeast(0L)

            val outId = transactionDao.insert(
                paymentLeg(
                    accountId = source.id,
                    isCardLeg = false,
                    amountMinorUnits = amount,
                    occurredAtEpochMillis = whenMillis,
                    merchant = "Credit card payment",
                    notes = notes ?: "Payment for ${bill.billerName}"
                )
            )
            val cardIdTxn = transactionDao.insert(
                paymentLeg(
                    accountId = cardId,
                    isCardLeg = true,
                    amountMinorUnits = amount,
                    occurredAtEpochMillis = whenMillis,
                    merchant = bill.billerName,
                    notes = notes ?: "Credit card bill payment"
                ).copy(linkedTransferTransactionId = outId, paysOffAccountId = cardId)
            )
            transactionDao.update(
                requireNotNull(transactionDao.findById(outId)).copy(linkedTransferTransactionId = cardIdTxn)
            )

            billDao.updateInstance(
                instance.copy(
                    paid = true,
                    paidAtEpochMillis = whenMillis,
                    paymentTransactionId = cardIdTxn
                )
            )
            paidBillId = instance.billId
            accountingService.recalculateAccounts(setOf(source.id, cardId))
            cardIdTxn
        }
        // Advance the schedule past the settled occurrence immediately (see markAsPaid).
        paidBillId?.let { billRecurrenceService.reconcileBill(it) }
        backupManager.scheduleAfterWrite()
        return result
    }

    /**
     * Creates the card-side leg for an already-recorded bank payment and links the two legs so
     * deleting either restores the bill and removes the pair consistently.
     */
    private suspend fun createCardSidePayment(
        payment: TransactionEntity,
        cardAccountId: Long,
        cardName: String
    ): Long {
        // Re-read in case markAsPaid just reclassified this row (expense -> transfer).
        val current = transactionDao.findById(payment.id) ?: payment
        val amount = if (current.debitMinorUnits > 0L) current.debitMinorUnits
        else kotlin.math.abs(current.creditMinorUnits).takeIf { it > 0L }
            ?: 0L
        require(amount > 0L) { "The selected transaction has no payment amount" }
        val cardTransactionId = transactionDao.insert(
            paymentLeg(
                accountId = cardAccountId,
                isCardLeg = true,
                amountMinorUnits = amount,
                occurredAtEpochMillis = current.occurredAtEpochMillis,
                merchant = cardName,
                notes = "Credit card bill payment (linked)"
            ).copy(linkedTransferTransactionId = current.id, paysOffAccountId = cardAccountId)
        )
        transactionDao.update(current.copy(linkedTransferTransactionId = cardTransactionId))
        accountingService.recalculateAccounts(setOf(cardAccountId))
        return cardTransactionId
    }

    private fun paymentLeg(
        accountId: Long,
        isCardLeg: Boolean,
        amountMinorUnits: Long,
        occurredAtEpochMillis: Long,
        merchant: String,
        notes: String?
    ): TransactionEntity {
        val now = System.currentTimeMillis()
        return TransactionEntity(
            occurredAtEpochMillis = occurredAtEpochMillis,
            rawDateString = "",
            txnType = if (isCardLeg) TxnType.CREDIT_TRANSACTION else TxnType.DEBIT_TRANSACTION,
            txnSubType = if (isCardLeg) TxnSubType.TRANSFER_IN else TxnSubType.TRANSFER_OUT,
            txnKind = if (isCardLeg) TxnKind.CC_BILL_PAYMENT else TxnKind.REGULAR,
            paymentType = PaymentType.ONLINE_TRANSFER,
            rawPaymentType = PaymentType.ONLINE_TRANSFER.raw,
            businessPersonal = BusinessPersonal.PERSONAL,
            merchantReceiverSender = merchant,
            categoryId = null,
            rawCategoryName = "A/c to A/c",
            accountId = accountId,
            creditMinorUnits = if (isCardLeg) amountMinorUnits else 0L,
            debitMinorUnits = if (isCardLeg) 0L else amountMinorUnits,
            balanceSnapshotMinorUnits = null,
            outstandingSnapshotMinorUnits = null,
            availableLimitSnapshotMinorUnits = null,
            notes = notes,
            reimbursable = false,
            reimbursed = false,
            isCreditCardBillPayment = isCardLeg,
            paysOffAccountId = if (isCardLeg) accountId else null,
            isHistoricalImport = false,
            dedupeFingerprint = stableFingerprint(
                listOf(accountId, amountMinorUnits, occurredAtEpochMillis, isCardLeg, merchant)
            ),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
    }

    private fun stableFingerprint(parts: List<Any>): String {
        val basis = parts.joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(basis.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Convenience for callers that only have a bill id. */
    suspend fun findBill(billId: Long): BillEntity? = billDao.findById(billId)
}
