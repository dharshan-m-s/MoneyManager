package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.entity.BillingCycle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The single source of truth for recurring-bill due dates.
 *
 * All dates are calendar dates in the app's established `Asia/Kolkata` zone and are stored as
 * the epoch millis of the START of that day. Keeping the schedule here (instead of in each
 * screen/ViewModel) is what makes generation idempotent and deterministic.
 *
 * End-of-month rule: the bill keeps the user's intended [anchorDay] (1..31). For a destination
 * month that has fewer days, the occurrence is clamped to that month's last day. The anchor is
 * NOT rewritten, so a 31st bill due on 28 February is followed by 31 March, not 28 March.
 * Yearly bills use the same rule for 29 February -> 28 February in non-leap years.
 */
object BillSchedule {

    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private const val WEEK_DAYS = 7L

    /** Day-of-month the recurrence is anchored to. Falls back to the current due date's day. */
    fun anchorDay(dueDateEpochMillis: Long, explicitAnchorDay: Int?): Int =
        (explicitAnchorDay ?: Instant.ofEpochMilli(dueDateEpochMillis).atZone(zone).dayOfMonth)
            .coerceIn(1, 31)

    fun startOfDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The occurrence strictly after [currentDueEpochMillis].
     *
     * WEEKLY is a pure 7-calendar-day step. MONTHLY/BIMONTHLY/QUARTERLY add 1/2/3 calendar
     * months. YEARLY adds one year. CUSTOM intentionally keeps the existing app meaning
     * (monthly) because the current data model has no explicit custom interval; introducing a
     * guessed interval would be worse than preserving the established behaviour.
     */
    fun nextOccurrence(
        currentDueEpochMillis: Long,
        cycle: BillingCycle,
        anchorDay: Int
    ): Long {
        val current = dateOf(currentDueEpochMillis)
        val day = anchorDay.coerceIn(1, 31)
        val next = when (cycle) {
            BillingCycle.WEEKLY -> current.plusDays(WEEK_DAYS)
            BillingCycle.MONTHLY,
            BillingCycle.CUSTOM -> plusMonthsClamped(current, 1, day)
            BillingCycle.BIMONTHLY -> plusMonthsClamped(current, 2, day)
            BillingCycle.QUARTERLY -> plusMonthsClamped(current, 3, day)
            BillingCycle.YEARLY -> {
                val target = YearMonth.from(current).plusYears(1)
                target.atDay(day.coerceAtMost(target.lengthOfMonth()))
            }
        }
        return epochOf(next)
    }

    /**
     * Occurrences after [fromDueEpochMillis] whose due date is on or before
     * [throughInclusiveEpochMillis], in ascending order.
     *
     * [limit] bounds pathological catch-up runs for very old templates. The caller can detect
     * truncation by checking whether the last returned date is still <= the window end.
     */
    fun occurrencesThrough(
        fromDueEpochMillis: Long,
        cycle: BillingCycle,
        anchorDay: Int,
        throughInclusiveEpochMillis: Long,
        limit: Int = 600
    ): List<Long> {
        val result = ArrayList<Long>()
        var cursor = fromDueEpochMillis
        var guard = 0
        while (guard++ < limit) {
            cursor = nextOccurrence(cursor, cycle, anchorDay)
            if (cursor > throughInclusiveEpochMillis) break
            result.add(cursor)
        }
        return result
    }

    private fun plusMonthsClamped(current: LocalDate, months: Long, anchorDay: Int): LocalDate {
        val target = YearMonth.from(current).plusMonths(months)
        return target.atDay(anchorDay.coerceAtMost(target.lengthOfMonth()))
    }

    /** Human label used by bill forms and history rows. */
    fun label(cycle: BillingCycle): String = when (cycle) {
        BillingCycle.WEEKLY -> "Every week"
        BillingCycle.MONTHLY -> "Every month"
        BillingCycle.BIMONTHLY -> "Every 2 months (bimonthly)"
        BillingCycle.QUARTERLY -> "Every 3 months (quarterly)"
        BillingCycle.YEARLY -> "Every year"
        BillingCycle.CUSTOM -> "Custom (monthly)"
    }
}
