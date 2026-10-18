package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
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
 */
@Singleton
class BillRecurrenceService @Inject constructor(
    private val billDao: BillDao
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
                    instance.paymentTransactionId == null &&
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
            if (!headInstance.paid && headInstance.paymentTransactionId == null) break
            headDue = BillSchedule.nextOccurrence(headDue, bill.billingCycle, anchor)
        }

        // The template's current occurrence always exists. This keeps the manual workflow
        // (where adding a bill immediately showed its first occurrence) intact.
        ensureInstance(bill, headDue, updateAmount = true)

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
            ensureInstance(bill, next, updateAmount = false)
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

    private suspend fun ensureInstance(bill: BillEntity, dueEpochMillis: Long, updateAmount: Boolean) {
        val due = BillSchedule.startOfDay(dueEpochMillis)
        val existing = billDao.findInstance(bill.id, due)
        if (existing == null) {
            billDao.insertInstanceIgnore(
                BillInstanceEntity(
                    billId = bill.id,
                    dueDateEpochMillis = due,
                    amountDueMinorUnits = bill.estimatedAmountMinorUnits
                )
            )
            return
        }
        // The estimate is not a historical payment. Refresh it only on the template's current
        // occurrence and only while that occurrence is still unpaid.
        if (updateAmount && !existing.paid && existing.paymentTransactionId == null &&
            existing.amountDueMinorUnits != bill.estimatedAmountMinorUnits
        ) {
            billDao.updateInstance(existing.copy(amountDueMinorUnits = bill.estimatedAmountMinorUnits))
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val MAX_GENERATION_STEPS = 600
    }
}
