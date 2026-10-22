package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.domain.reporting.FinancialSummary
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When a bill occurrence may actually be settled by the user.
 *
 * A bill template always has a "current" occurrence (the schedule head), even when that
 * occurrence is months away - an annual insurance premium due in six months is materialized
 * immediately so the screen can show it. Offering "Mark as paid" for such an occurrence is
 * noise: there is nothing to settle yet, and the user explicitly asked for the action to
 * appear only once the payment date is near.
 *
 * The window is driven by the bill's own reminder setting (`reminderDaysBefore` - the "days
 * before the due date" the user configured on the bill, default 10 days). The same number the
 * user uses to decide when they want to be told about a bill also decides when the bill
 * becomes actionable, so there is exactly ONE lead-time dial per bill and no second,
 * contradictory setting to keep in sync. An occurrence is payable when:
 *  - it is already overdue (it must be settled, now),
 *  - it is partially paid (the rest of it has to be recorded), or
 *  - its due date is within the bill's reminder window.
 *
 * The rules live here - not in a composable - so the screen, the payment flow and the tests
 * all agree on what "payable" means.
 */
object BillPaymentWindow {

    /** Even a bill with the reminder set to 0 keeps a one-day window, so a bill that is
     *  due today is always actionable. */
    const val MIN_LEAD_DAYS: Int = 1

    /** The bill's own reminder lead time ("days before the due date"), clamped to a sane range. */
    fun leadDays(bill: BillEntity): Int = bill.reminderDaysBefore.coerceIn(MIN_LEAD_DAYS, 365)

    /** Whole days from today until the due date. Negative means overdue. */
    fun daysUntilDue(
        dueDateEpochMillis: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = FinancialSummary.zone
    ): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val due = Instant.ofEpochMilli(dueDateEpochMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, due)
    }

    /**
     * True when the occurrence may be marked as paid right now.
     *
     * A null instance (the bill has no open occurrence), an already settled occurrence and an
     * occurrence that is still far in the future are all NOT payable.
     */
    fun isPayableNow(
        bill: BillEntity,
        instance: BillInstanceEntity?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = FinancialSummary.zone
    ): Boolean {
        if (instance == null || instance.paid) return false
        // A partial payment is already recorded: the remainder is still owed.
        if (instance.paidAmountMinorUnits > 0L) return true
        val days = daysUntilDue(instance.dueDateEpochMillis, now, zone)
        if (days < 0L) return true
        return days <= leadDays(bill)
    }
}
