package com.moneymanager.app.domain.billing

import com.moneymanager.app.domain.accounting.AccountingEngine
import com.moneymanager.app.domain.accounting.AccountingEngine.BillingCycleWindow
import java.time.ZoneId

/**
 * The canonical rules for a credit card's statement periods.
 *
 * A card's billing cycle starts on a fixed day of the month. The statement for that cycle is
 * issued when the cycle closes and falls due [DUE_LAG_DAYS] later (the credit period). Every
 * card bill in this app therefore follows the same two rules:
 *
 *   statement(C) = what the card owed at the moment cycle C closed
 *   due(C)       = end of cycle C + [DUE_LAG_DAYS]
 *
 * The same relationship is why a bill settles the statement that was issued when it fell due
 * (see [cycleSettledByDueDate]) - and why the reference design's "START DATE 04 Sep / DUE
 * DATE 24 Aug" pair works: the payment day falls before the next cycle even starts, so that
 * payment settles the cycle that closed before it.
 *
 * Three kinds of cycle matter to the app:
 *
 *  - CLOSED   - the cycle ended. Its statement is a fact: the card's outstanding at that
 *               moment. It never moves again, so a bill that was generated from it keeps
 *               showing the same amount forever.
 *  - ONGOING  - the cycle is still running. The statement is what the card owes RIGHT NOW:
 *               it grows with every purchase and shrinks with every refund or payment, which
 *               is what makes a card's "current bill amount" track the cycle.
 *  - FUTURE   - the cycle has not started. Its statement does not exist yet, so the amount is
 *               an ESTIMATE (the previous cycle's statement, or the user's typed estimate).
 */
object CreditCardStatement {

    /** Days between the end of a statement cycle and the bill's due date (the credit period). */
    const val DUE_LAG_DAYS = 20L

    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private const val DAY_MILLIS = 86_400_000L

    /**
     * The statement cycle a bill occurrence with [dueEpochMillis] settles.
     *
     * A bill pays for the statement that has been issued when it falls due - never for one
     * that is still running. So the settled cycle is the one that closed most recently before
     * the due date:
     *
     *  - the cycle running on the due date has already closed  -> that cycle is settled;
     *  - the cycle running on the due date closes after it       -> the cycle before it is
     *    settled, because that statement is the one the user is paying for.
     *
     * With the default due date (the cycle's end plus [DUE_LAG_DAYS]) this is exactly the
     * cycle the due date was derived from. With a card whose payment day falls before the
     * cycle ends - the reference card: cycle starts on the 4th, payments due on the 24th - it
     * settles the previous cycle, which is how a real card behaves.
     */
    fun cycleSettledByDueDate(cycleStartDay: Int, dueEpochMillis: Long): BillingCycleWindow {
        val running = AccountingEngine.billingCycleWindowFor(
            cycleStartDay = cycleStartDay,
            epochMillis = dueEpochMillis,
            zone = zone
        )
        return if (running.endExclusive <= dueEpochMillis) {
            running
        } else {
            AccountingEngine.billingCycleWindowFor(
                cycleStartDay = cycleStartDay,
                epochMillis = running.startInclusive - 1L,
                zone = zone
            )
        }
    }

    /**
     * The next due date for a card whose cycle starts on [cycleStartDay]: the current cycle's
     * statement closes at the end of the cycle and falls due [DUE_LAG_DAYS] later.
     */
    fun defaultDueDate(cycleStartDay: Int, nowEpochMillis: Long): Long {
        val window = AccountingEngine.billingCycleWindowFor(cycleStartDay, nowEpochMillis, zone)
        return window.endExclusive + DUE_LAG_DAYS * DAY_MILLIS
    }

    /** The statement cycle that is running at [nowEpochMillis]. */
    fun currentCycle(cycleStartDay: Int, nowEpochMillis: Long): BillingCycleWindow =
        AccountingEngine.billingCycleWindowFor(cycleStartDay, nowEpochMillis, zone)

    /**
     * The statement amount of [cycle], or null when the cycle has not started yet (its
     * statement does not exist and the caller has to fall back to an estimate).
     *
     * @param outstandingNowMinorUnits what the card owes right now.
     * @param movementSinceCycleEndMinorUnits the net card movement (purchases, cash advances,
     *        refunds, payments) that happened at or after the cycle's end - subtracting it from
     *        the current outstanding reconstructs what was owed when the cycle closed.
     */
    fun statementFor(
        cycle: BillingCycleWindow,
        nowEpochMillis: Long,
        outstandingNowMinorUnits: Long,
        movementSinceCycleEndMinorUnits: Long
    ): Long? = when {
        cycle.startInclusive > nowEpochMillis -> null
        cycle.endExclusive <= nowEpochMillis ->
            outstandingNowMinorUnits - movementSinceCycleEndMinorUnits
        else -> outstandingNowMinorUnits
    }
}
