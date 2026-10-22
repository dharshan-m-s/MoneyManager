package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.repository.CreditCardStatementService
import com.moneymanager.app.domain.model.AccountType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central, idempotent recurring-bill lifecycle.
 *
 * There is exactly one place that decides which bill occurrences should exist. Every screen,
 * worker, foreground resume and app start delegates here. The unique
 * (billId, dueDateEpochMillis) index plus INSERT OR IGNORE makes repeated execution safe.
 *
 * Invariants:
 *  - The template's first occurrence is always materialized (preserves the manual-bill flow).
 *  - Occurrences are generated only when `autoGenerateBills` is on and their due date is within
 *    `autoGenerateLeadDays` of the reconciliation time.
 *  - Missed runs catch up: every occurrence up to the lead window is created, including overdue
 *    history, but never more than once.
 *  - Paid instances and instances with a linked payment are never deleted or regenerated.
 *  - The next due date is advanced from the schedule, never from the date a bill was marked
 *    paid.
 *  - A CREDIT-CARD biller's occurrence amounts come from the card's own statement for the
 *    cycle that occurrence settles (see [CreditCardStatement]); a cycle that has not started
 *    yet has no statement yet and falls back to an estimate.
 */
@Singleton
class BillRecurrenceService @Inject constructor(
    private val billDao: BillDao,
    private val accountDao: AccountDao,
    private val statementService: CreditCardStatementService
) {

    suspend fun reconcileAll(now: Long = System.currentTimeMillis()) {
        billDao.findAll().forEach { bill -> reconcileBill(bill.id, now) }
    }

    suspend fun reconcileBill(billId: Long, now: Long = System.currentTimeMillis()) {
        val bill = billDao.findById(billId) ?: return
        if (!bill.active) return
        reconcile(bill, now)
    }

    /**
     * Call after a template was edited. Future unpaid occurrences that are no longer on the
     * edited schedule are removed, while historical/paid occurrences and their transactions
     * remain untouched.
     */
    suspend fun reconcileAfterEdit(billId: Long, now: Long = System.currentTimeMillis()) {
        val bill = billDao.findById(billId) ?: return
        if (!bill.active) return
        val anchor = BillSchedule.anchorDay(bill.nextDueDateEpochMillis, bill.scheduleAnchorDay)
        val scheduled = HashSet<Long>()
        scheduled.add(BillSchedule.startOfDay(bill.nextDueDateEpochMillis))
        var cursor = bill.nextDueDateEpochMillis
        repeat(MAX_GENERATION_STEPS) {
            cursor = BillSchedule.nextOccurrence(cursor, bill.billingCycle, anchor)
            scheduled.add(cursor)
        }
        billDao.findInstancesForBill(bill.id)
            .filter { instance ->
                !instance.paid &&
                    instance.paidAmountMinorUnits == 0L &&
                    instance.dueDateEpochMillis > now &&
                    instance.dueDateEpochMillis !in scheduled
            }
            .forEach { stale -> billDao.deleteInstanceById(stale.id) }
        reconcile(bill, now)
    }

    private suspend fun reconcile(bill: BillEntity, now: Long) {
        val anchor = BillSchedule.anchorDay(bill.nextDueDateEpochMillis, bill.scheduleAnchorDay)
        var headDue = BillSchedule.startOfDay(bill.nextDueDateEpochMillis)

        // The template head must follow the schedule past occurrences the user has already
        // paid. Without this, nextDueDateEpochMillis stays parked on the settled occurrence
        // until the next lead window opens, and the card/bill forms keep showing the paid
        // date as the "next due" date. Advancement is driven by the schedule (never by the
        // payment timestamp), so paying early or late always yields the same next due date.
        var advanceGuard = 0
        while (advanceGuard++ < MAX_GENERATION_STEPS) {
            val headInstance = billDao.findInstance(bill.id, headDue) ?: break
            // Advance only past fully settled occurrences. A partial payment (payments below
            // the amount due) still owes money, so the occurrence remains the head.
            if (!headInstance.paid) break
            headDue = BillSchedule.nextOccurrence(headDue, bill.billingCycle, anchor)
        }

        // The template's current occurrence always exists. This keeps the manual workflow
        // (where adding a bill immediately showed its first occurrence) intact.
        ensureInstance(bill, headDue, updateAmount = true, now = now)

        if (!bill.autoGenerateBills) {
            if (headDue != BillSchedule.startOfDay(bill.nextDueDateEpochMillis) ||
                bill.scheduleAnchorDay != anchor
            ) {
                billDao.updateSchedule(bill.id, headDue, anchor, now)
            }
            return
        }

        val existing = billDao.findInstancesForBill(bill.id)
        val latestMaterialized = existing.maxOfOrNull { it.dueDateEpochMillis } ?: headDue
        val leadDays = bill.autoGenerateLeadDays.coerceIn(0, 365)
        val windowEnd = now + leadDays * DAY_MILLIS

        var cursor = maxOf(latestMaterialized, headDue)
        var guard = 0
        while (guard++ < MAX_GENERATION_STEPS) {
            val next = BillSchedule.nextOccurrence(cursor, bill.billingCycle, anchor)
            if (next > windowEnd) break
            ensureInstance(bill, next, updateAmount = false, now = now)
            cursor = next
        }

        // nextDueDate now points at the latest occurrence the template has materialized.
        // Re-running is idempotent: the next call starts from the same occurrence and the
        // unique index absorbs the existing row.
        if (cursor != BillSchedule.startOfDay(bill.nextDueDateEpochMillis) ||
            bill.scheduleAnchorDay != anchor
        ) {
            billDao.updateSchedule(bill.id, cursor, anchor, now)
        }
    }

    private suspend fun ensureInstance(
        bill: BillEntity,
        dueEpochMillis: Long,
        updateAmount: Boolean,
        now: Long
    ) {
        val due = BillSchedule.startOfDay(dueEpochMillis)
        val amount = amountDueFor(bill, due, now)
        val existing = billDao.findInstance(bill.id, due)
        if (existing == null) {
            billDao.insertInstanceIgnore(
                BillInstanceEntity(
                    billId = bill.id,
                    dueDateEpochMillis = due,
                    amountDueMinorUnits = amount
                )
            )
            return
        }
        // The estimate is not a historical payment. Refresh it only on the template's current
        // occurrence and only while that occurrence has no recorded payments at all - once a
        // (partial) payment exists the amount due is history and must not move under it.
        if (updateAmount && !existing.paid && existing.paidAmountMinorUnits == 0L &&
            existing.amountDueMinorUnits != amount
        ) {
            billDao.updateInstance(existing.copy(amountDueMinorUnits = amount))
        }
    }

    /**
     * The amount one occurrence is due.
     *
     * A credit-card biller takes its amount from the card's own statement: the occurrence that
     * falls due on [dueEpochMillis] settles the statement cycle that closed
     * [CreditCardStatement.DUE_LAG_DAYS] earlier, and that cycle's statement is a fact once
     * the cycle closed. While the cycle is still running the statement is what the card owes
     * right now, so the amount keeps moving with the card. A cycle that has not started yet
     * has no statement - the previous cycle's statement (the "previous month's bill") is used
     * as the estimate, falling back to the user's stored estimate.
     *
     * Every other biller keeps its stored estimate.
     */
    private suspend fun amountDueFor(bill: BillEntity, dueEpochMillis: Long, now: Long): Long {
        val cardId = bill.linkedAccountId ?: return bill.estimatedAmountMinorUnits
        val card = accountDao.findById(cardId) ?: return bill.estimatedAmountMinorUnits
        if (card.accountType != AccountType.CREDIT_CARD) return bill.estimatedAmountMinorUnits

        val cycleStartDay = card.billingCycleStartDay ?: 1
        val cycle = CreditCardStatement.cycleSettledByDueDate(cycleStartDay, dueEpochMillis)
        val statement = statementService.statementForCycle(card, cycle, now)
        if (statement != null) return statement

        // The cycle has not started: estimate from the most recent statement that exists -
        // the bill is expected to be about last month's statement.
        val previous = statementService.estimateStatementForCycle(card, cycle, now)
        return previous ?: bill.estimatedAmountMinorUnits
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val MAX_GENERATION_STEPS = 600
    }
}
