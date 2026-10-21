package com.moneymanager.app.data.repository

import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillPaymentAllocationEntity
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
import kotlin.math.abs

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
     * Records a payment against an existing bill occurrence and links it to an
     * already-recorded transaction, or (with a null [paymentTransactionId]) records an
     * explicit cash/out-of-band payment.
     *
     * Partial payments are first-class: the allocation amount may be less than the remaining
     * due, the occurrence stays unsettled with its paid aggregate updated, and the schedule
     * only advances once payments cover the amount due. Over-payments are recorded at their
     * full amount (never silently capped) and show up as an extra credit on the settled
     * occurrence.
     *
     * Candidate validation: a credit-card bill's payment must originate OUTSIDE the cards -
     * a purchase on the linked card (or any other card) is spending, not a payment, and is
     * rejected instead of being reclassified as a transfer onto the wrong card.
     *
     * For a card bill where the chosen transaction is the bank side of a payment, the
     * card-side leg is required to reduce the outstanding correctly; it is created once and
     * the original bank transaction is never duplicated. A transaction already allocated to
     * any bill (directly or as the peer of a paired transfer) is rejected.
     */
    suspend fun markAsPaid(
        instance: BillInstanceEntity,
        paymentTransactionId: Long?,
        amountMinorUnits: Long? = null
    ): BillInstanceEntity {
        var settledBillId: Long? = null
        val updated = database.withTransaction {
            val current = billDao.findInstanceById(instance.id)
                ?: return@withTransaction instance
            // Fully settled: never pay the same occurrence again or re-link its history.
            if (current.paid) return@withTransaction current
            val bill = billDao.findById(current.billId)
            val cardId = bill?.linkedAccountId
            val remaining =
                (current.amountDueMinorUnits - current.paidAmountMinorUnits).coerceAtLeast(0L)

            var effectivePaymentId: Long? = paymentTransactionId
            var moneyMoved: Long
            var paidAt: Long = System.currentTimeMillis()

            if (paymentTransactionId != null) {
                val payment = requireNotNull(transactionDao.findById(paymentTransactionId)) {
                    "Payment transaction no longer exists"
                }
                require(!isAllocatedAnywhere(paymentTransactionId)) {
                    "This transaction is already recorded as a payment for another bill"
                }
                val peerId = payment.linkedTransferTransactionId
                if (peerId != null) {
                    require(!isAllocatedAnywhere(peerId)) {
                        "This transfer is already recorded as a payment for another bill"
                    }
                }

                // A card bill is settled from outside the cards; a purchase (on the linked
                // card or any other) must never be reclassified into a payment leg.
                if (cardId != null) {
                    require(payment.accountId != cardId) {
                        "This is a transaction on the card itself, not a payment towards it. " +
                            "Record a new payment instead."
                    }
                    val sourceAccount = requireNotNull(accountDao.findById(payment.accountId)) {
                        "Payment account no longer exists"
                    }
                    require(sourceAccount.accountType != AccountType.CREDIT_CARD) {
                        "A purchase on another credit card does not pay this bill. Choose a " +
                            "transaction from a bank account."
                    }
                    require(payment.debitMinorUnits > 0L) {
                        "Choose a transaction that pays money out"
                    }
                } else {
                    require(
                        !payment.isCreditCardBillPayment && payment.paysOffAccountId == null
                    ) {
                        "Choose the transaction that paid the bill directly"
                    }
                }

                val moneyOut = payment.debitMinorUnits
                require(moneyOut > 0L) { "The selected transaction has no payment amount" }
                moneyMoved = moneyOut
                paidAt = payment.occurredAtEpochMillis

                val peer = peerId?.let { transactionDao.findById(it) }
                if (cardId != null && peer != null && peer.accountId != cardId) {
                    throw IllegalArgumentException(
                        "This transfer already pays a different credit card"
                    )
                }
                val existingCardLeg = peer?.takeIf {
                    it.accountId == cardId && it.paysOffAccountId == cardId
                }
                if (existingCardLeg != null) {
                    require(!isAllocatedAnywhere(existingCardLeg.id)) {
                        "This payment is already linked to another bill"
                    }
                } else if (cardId != null && payment.txnSubType == TxnSubType.EXPENSE) {
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
                effectivePaymentId = if (cardId != null) {
                    existingCardLeg?.id ?: createCardSidePayment(
                        payment = payment,
                        cardAccountId = cardId,
                        cardName = bill.billerName
                    )
                } else {
                    payment.id
                }
            } else {
                moneyMoved = amountMinorUnits ?: remaining
            }

            if (amountMinorUnits != null && paymentTransactionId != null) {
                require(amountMinorUnits in 1L..moneyMoved) {
                    "Payment amount must match the selected transaction"
                }
                moneyMoved = amountMinorUnits
            }
            require(moneyMoved > 0L) { "Enter a payment amount" }

            billDao.insertAllocation(
                BillPaymentAllocationEntity(
                    billInstanceId = current.id,
                    transactionId = effectivePaymentId,
                    amountMinorUnits = moneyMoved,
                    paidAtEpochMillis = paidAt,
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
            val newState = requireNotNull(syncInstancePaymentState(current.id))
            if (newState.paid) settledBillId = current.billId
            newState
        }
        // Advance the schedule past the settled occurrence so the template's next due date
        // reflects the recurrence immediately, not at the next app start. Partial payments
        // leave the schedule alone - the occurrence is still owed.
        settledBillId?.let { billRecurrenceService.reconcileBill(it) }
        backupManager.scheduleAfterWrite()
        return updated
    }

    /**
     * Records a payment for a credit-card bill as a proper two-legged transfer:
     *  - a TRANSFER_OUT on the source account (real money leaves it),
     *  - a CARD_PAYMENT (TRANSFER_IN + cc-bill-payment kind) on the card (outstanding falls),
     * and allocates it against the bill instance, atomically. The amount may be partial -
     * the occurrence stays unsettled until allocations cover the amount due, and only then
     * does the schedule advance.
     *
     * Returns the card-side transaction id. If the instance is already fully settled the
     * existing payment id is returned, so retries cannot create a second payment.
     */
    suspend fun recordCreditCardBillPayment(
        instanceId: Long,
        sourceAccountId: Long,
        amountMinorUnits: Long,
        occurredAtEpochMillis: Long,
        notes: String? = null
    ): Long {
        var settledBillId: Long? = null
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
            val remaining =
                (instance.amountDueMinorUnits - instance.paidAmountMinorUnits).coerceAtLeast(0L)
            val amount = when {
                amountMinorUnits > 0L -> amountMinorUnits
                remaining > 0L -> remaining
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

            billDao.insertAllocation(
                BillPaymentAllocationEntity(
                    billInstanceId = instance.id,
                    transactionId = cardIdTxn,
                    amountMinorUnits = amount,
                    paidAtEpochMillis = whenMillis,
                    createdAtEpochMillis = System.currentTimeMillis()
                )
            )
            val settled = requireNotNull(syncInstancePaymentState(instance.id))
            if (settled.paid) settledBillId = instance.billId
            accountingService.recalculateAccounts(setOf(source.id, cardId))
            cardIdTxn
        }
        // Advance the schedule only once the occurrence is actually settled (see markAsPaid).
        settledBillId?.let { billRecurrenceService.reconcileBill(it) }
        backupManager.scheduleAfterWrite()
        return result
    }

    /**
     * Instance ids whose payment state references any of these transactions - either through
     * an allocation row or the legacy link column. Read BEFORE deleting the transactions,
     * then call [recomputeInstancePaymentStates] after the (cascade) deletion.
     */
    suspend fun instanceIdsReferencingTransactions(transactionIds: Set<Long>): Set<Long> {
        if (transactionIds.isEmpty()) return emptySet()
        val fromAllocations = billDao.findInstanceIdsForTransactions(transactionIds)
        val fromLegacy = transactionIds.mapNotNull { billDao.findByPaymentTransactionId(it)?.id }
        return (fromAllocations + fromLegacy).toSet()
    }

    /**
     * Recomputes cached payment aggregates after payment transactions disappeared (their
     * allocation rows cascade away with them). The occurrence returns to whichever state the
     * remaining allocations justify - unpaid, partially paid or still settled - instead of a
     * blanket reset that would discard surviving partial payments.
     */
    suspend fun recomputeInstancePaymentStates(instanceIds: Set<Long>) {
        if (instanceIds.isEmpty()) return
        database.withTransaction {
            instanceIds.forEach { syncInstancePaymentState(it) }
        }
        backupManager.scheduleAfterWrite()
    }

    /**
     * Recomputes an occurrence's cached columns (paidAmount, paid, paidAt, primary payment
     * link) from its allocations. Must run inside the caller's transaction when part of a
     * larger operation. `paid` becomes true only when recorded payments cover the amount due;
     * `paidAt` is the date the coverage completed, not the latest payment date.
     */
    private suspend fun syncInstancePaymentState(instanceId: Long): BillInstanceEntity? {
        val instance = billDao.findInstanceById(instanceId) ?: return null
        val allocations = billDao.findAllocationsForInstance(instanceId)
        val sum = allocations.sumOf { it.amountMinorUnits }
        val settled = allocations.isNotEmpty() && sum >= instance.amountDueMinorUnits
        val completedAt = if (settled) {
            var running = 0L
            var crossedAt: Long? = null
            for (allocation in allocations) {
                running += allocation.amountMinorUnits
                if (running >= instance.amountDueMinorUnits) {
                    crossedAt = allocation.paidAtEpochMillis
                    break
                }
            }
            crossedAt
        } else {
            null
        }
        val updated = instance.copy(
            paid = settled,
            paidAtEpochMillis = completedAt,
            paymentTransactionId = allocations.firstOrNull { it.transactionId != null }?.transactionId,
            paidAmountMinorUnits = sum
        )
        billDao.updateInstance(updated)
        return updated
    }

    private suspend fun isAllocatedAnywhere(transactionId: Long): Boolean =
        billDao.findAllocationByTransactionId(transactionId) != null ||
            billDao.findByPaymentTransactionId(transactionId) != null

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
        else abs(current.creditMinorUnits).takeIf { it > 0L }
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
