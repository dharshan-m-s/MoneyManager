package com.moneymanager.app.data.repository

import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.TransactionDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.accounting.TransactionClassifier
import com.moneymanager.app.domain.billing.CreditCardStatement
import com.moneymanager.app.domain.model.AccountType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes a credit card's statement amounts from its own ledger.
 *
 * The card's outstanding is maintained continuously by [AccountingService]; this service turns
 * that running balance into the per-cycle statements the billing side needs:
 *
 *  - the CURRENT cycle's statement is what the card owes right now (it moves with every
 *    purchase, refund and payment);
 *  - a CLOSED cycle's statement is what the card owed when that cycle closed (a fact that
 *    never moves again);
 *  - a cycle that has not started yet has no statement at all - the caller uses an estimate.
 *
 * Everything is derived from the card's transactions, so a card bill can never disagree with
 * the card screen: both read the same numbers.
 */
@Singleton
class CreditCardStatementService @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {

    /** What the card owes right now - the amount its current statement is built from. */
    suspend fun currentStatementMinorUnits(card: AccountEntity): Long {
        card.outstandingMinorUnits?.let { return it }
        return derivedOutstanding(card)
    }

    /**
     * The statement amount of [cycle], or null when the cycle has not started yet (its
     * statement does not exist yet).
     */
    suspend fun statementForCycle(
        card: AccountEntity,
        cycle: AccountingEngine.BillingCycleWindow,
        nowEpochMillis: Long
    ): Long? {
        val outstandingNow = currentStatementMinorUnits(card)
        val movementSinceEnd = movementSince(card, cycle.endExclusive)
        return CreditCardStatement.statementFor(
            cycle = cycle,
            nowEpochMillis = nowEpochMillis,
            outstandingNowMinorUnits = outstandingNow,
            movementSinceCycleEndMinorUnits = movementSinceEnd
        )
    }

    /**
     * The estimate for [cycle]: the statement of the most recent cycle that has already
     * started. That is the "previous month's bill" a cycle that has not started yet is
     * estimated from - and when even that cycle has not started, the estimate walks further
     * back until it finds a cycle with a statement.
     *
     * Null when there is nothing at all to base an estimate on (a card with no history).
     */
    suspend fun estimateStatementForCycle(
        card: AccountEntity,
        cycle: AccountingEngine.BillingCycleWindow,
        nowEpochMillis: Long
    ): Long? {
        val cycleStartDay = card.billingCycleStartDay ?: 1
        var probe = cycle
        var guard = 0
        while (guard++ < MAX_ESTIMATE_CYCLES) {
            val previous = AccountingEngine.billingCycleWindowFor(
                cycleStartDay = cycleStartDay,
                epochMillis = probe.startInclusive - 1L,
                zone = CreditCardStatement.zone
            )
            val statement = statementForCycle(card, previous, nowEpochMillis)
            if (statement != null) return statement
            probe = previous
        }
        return null
    }

    /**
     * The statement of the cycle that comes right before [cycle] - the "previous month's
     * bill" an estimate for [cycle] is based on. Null when that cycle has not started either
     * (there is nothing to base an estimate on).
     */
    suspend fun previousCycleStatement(
        card: AccountEntity,
        cycle: AccountingEngine.BillingCycleWindow,
        nowEpochMillis: Long
    ): Long? {
        val previous = AccountingEngine.billingCycleWindowFor(
            cycleStartDay = card.billingCycleStartDay ?: 1,
            epochMillis = cycle.startInclusive - 1L,
            zone = CreditCardStatement.zone
        )
        return statementForCycle(card, previous, nowEpochMillis)
    }

    /** The net change to the card's outstanding from [fromEpochMillis] onwards. */
    suspend fun movementSince(card: AccountEntity, fromEpochMillis: Long): Long {
        var total = 0L
        for (txn in transactionDao.findByAccount(card.id)) {
            if (txn.occurredAtEpochMillis < fromEpochMillis) continue
            total += cardMovementMinorUnits(txn, card)
        }
        return total
    }

    /** The signed change one transaction makes to a card's outstanding. */
    fun cardMovementMinorUnits(txn: TransactionEntity, card: AccountEntity): Long {
        val semantics = TransactionClassifier.classify(
            txn.txnSubType,
            txn.txnKind,
            card.accountType
        )
        return AccountingEngine.movementFor(
            semantics = semantics,
            credit = txn.creditMinorUnits,
            debit = txn.debitMinorUnits
        ).cardOutstandingDeltaMinorUnits
    }

    /** The card's outstanding rebuilt from its ledger alone (no user checkpoint applied). */
    suspend fun derivedOutstanding(card: AccountEntity): Long {
        var total = 0L
        for (txn in transactionDao.findByAccount(card.id)) {
            total += cardMovementMinorUnits(txn, card)
        }
        return total
    }

    /** Every credit card in the app - the set the startup refresh walks. */
    suspend fun allCards(): List<AccountEntity> =
        accountDao.findAll().filter { it.accountType == AccountType.CREDIT_CARD }

    private companion object {
        /** How many cycles back an estimate may look for a statement that already exists. */
        const val MAX_ESTIMATE_CYCLES = 24
    }
}
