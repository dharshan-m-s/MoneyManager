package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.entity.BillingCycle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillScheduleTest {

    private fun epoch(year: Int, month: Int, day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(year, month, day))

    @Test
    fun monthlyKeepsAnchorAcrossShortMonth() {
        val jan31 = epoch(2026, 1, 31)
        val feb28 = BillSchedule.nextOccurrence(jan31, BillingCycle.MONTHLY, anchorDay = 31)
        assertEquals(epoch(2026, 2, 28), feb28)
        // Anchor is restored: no drift to the 28th forever.
        val mar31 = BillSchedule.nextOccurrence(feb28, BillingCycle.MONTHLY, anchorDay = 31)
        assertEquals(epoch(2026, 3, 31), mar31)
    }

    @Test
    fun monthlyHandlesLeapFebruary() {
        val jan31 = epoch(2024, 1, 31)
        val feb29 = BillSchedule.nextOccurrence(jan31, BillingCycle.MONTHLY, anchorDay = 31)
        assertEquals(epoch(2024, 2, 29), feb29)
        assertEquals(epoch(2024, 3, 31), BillSchedule.nextOccurrence(feb29, BillingCycle.MONTHLY, 31))
    }

    @Test
    fun bimonthlyIsOneOccurrenceEveryTwoCalendarMonths() {
        val jan31 = epoch(2026, 1, 31)
        val mar31 = BillSchedule.nextOccurrence(jan31, BillingCycle.BIMONTHLY, 31)
        assertEquals(epoch(2026, 3, 31), mar31)
        assertEquals(epoch(2026, 5, 31), BillSchedule.nextOccurrence(mar31, BillingCycle.BIMONTHLY, 31))

        val nov30 = epoch(2026, 11, 30)
        assertEquals(
            epoch(2027, 1, 31),
            BillSchedule.nextOccurrence(nov30, BillingCycle.BIMONTHLY, anchorDay = 31)
        )
    }

    @Test
    fun quarterlyAndYearlyAdvanceCorrectly() {
        assertEquals(
            epoch(2026, 4, 30),
            BillSchedule.nextOccurrence(epoch(2026, 1, 31), BillingCycle.QUARTERLY, 31)
        )
        assertEquals(
            epoch(2025, 2, 28),
            BillSchedule.nextOccurrence(epoch(2024, 2, 29), BillingCycle.YEARLY, 29)
        )
        assertEquals(
            epoch(2027, 1, 1),
            BillSchedule.nextOccurrence(epoch(2026, 1, 1), BillingCycle.YEARLY, 1)
        )
    }

    @Test
    fun weeklyIsSevenCalendarDaysAcrossYearBoundary() {
        assertEquals(
            epoch(2027, 1, 4),
            BillSchedule.nextOccurrence(epoch(2026, 12, 28), BillingCycle.WEEKLY, 28)
        )
    }

    @Test
    fun occurrencesThroughCatchesUpMissedRunsAndExcludesFuture() {
        val jan31 = epoch(2026, 1, 31)
        val through = epoch(2026, 4, 30)
        val occurrences = BillSchedule.occurrencesThrough(jan31, BillingCycle.MONTHLY, 31, through)
        assertEquals(
            listOf(epoch(2026, 2, 28), epoch(2026, 3, 31), epoch(2026, 4, 30)),
            occurrences
        )
        // The next occurrence is strictly outside the window.
        assert(occurrences.last() <= through)
    }

    @Test
    fun anchorFallsBackToCurrentDueDateDay() {
        val due = epoch(2026, 6, 15)
        assertEquals(15, BillSchedule.anchorDay(due, null))
        assertEquals(15, BillSchedule.anchorDay(due, 15))
    }

    @Test
    fun bimonthlyLabelIsUnambiguous() {
        assertEquals("Every 2 months (bimonthly)", BillSchedule.label(BillingCycle.BIMONTHLY))
    }
}
