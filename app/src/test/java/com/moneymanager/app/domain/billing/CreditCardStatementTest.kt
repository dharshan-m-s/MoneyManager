package com.moneymanager.app.domain.billing

import com.moneymanager.app.domain.accounting.AccountingEngine.BillingCycleWindow
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The credit card's statement rules are the backbone of the whole billing side: they decide
 * which cycle a bill settles and what that bill is worth. They are pure calendar math, so
 * they are pinned here rather than only through the database.
 */
class CreditCardStatementTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(year, month, day))

    private fun window(start: Long, end: Long) = BillingCycleWindow(start, end, "label")

    @Test
    fun aDueDateSettlesTheCycleThatClosedTwentyDaysEarlier() {
        // A card whose cycle starts on the 1st: the October statement closes on 31 Oct and
        // falls due on 21 Nov.
        val cycle = CreditCardStatement.cycleSettledByDueDate(1, at(2026, 11, 21))

        assertEquals(at(2026, 10, 1), cycle.startInclusive)
        assertEquals(at(2026, 11, 1), cycle.endExclusive)
    }

    @Test
    fun theCycleStartDayMovesTheStatementPeriod() {
        // A card whose cycle starts on the 4th: the statement that closes on 3 Oct is due on
        // 23 Oct - the reference design's "START DATE 04 Sep / DUE DATE 24 Aug" shape.
        val cycle = CreditCardStatement.cycleSettledByDueDate(4, at(2026, 10, 23))

        assertEquals(at(2026, 9, 4), cycle.startInclusive)
        assertEquals(at(2026, 10, 4), cycle.endExclusive)
    }

    @Test
    fun aDueDateBeforeTheCycleEndsSettlesTheCycleThatClosedBefore() = runBlocking {
        // The reference card: the cycle starts on the 3rd and payments fall due on the 24th,
        // so the payment day arrives BEFORE the cycle closes. The bill due on 24 Oct settles
        // the statement that closed on 3 Oct - the cycle before the running one.
        val cycle = CreditCardStatement.cycleSettledByDueDate(3, at(2026, 10, 24))

        assertEquals(at(2026, 9, 3), cycle.startInclusive)
        assertEquals(at(2026, 10, 3), cycle.endExclusive)
    }

    @Test
    fun aDueDateAfterTheCycleEndsSettlesTheCycleThatJustClosed() {
        // The cycle 03 Oct - 02 Nov closes on 3 Nov; the bill due on 24 Nov settles it.
        val cycle = CreditCardStatement.cycleSettledByDueDate(3, at(2026, 11, 24))

        assertEquals(at(2026, 10, 3), cycle.startInclusive)
        assertEquals(at(2026, 11, 3), cycle.endExclusive)
    }

    @Test
    fun theDefaultDueDateIsTheCycleEndPlusTheCreditPeriod() {
        val due = CreditCardStatement.defaultDueDate(1, at(2026, 10, 22))

        assertEquals(at(2026, 11, 21), due)
    }

    @Test
    fun theDefaultDueDateFollowsTheCycleStartDay() {
        val due = CreditCardStatement.defaultDueDate(4, at(2026, 9, 10))

        // The cycle 04 Sep - 03 Oct closes on 3 Oct; the bill is due 20 days later - the
        // reference card's "START DATE 04 Sep / DUE DATE 24 Aug" shape.
        assertEquals(at(2026, 10, 24), due)
    }

    @Test
    fun aCycleThatHasNotStartedHasNoStatement() {
        val future = window(at(2026, 11, 1), at(2026, 12, 1))

        val statement = CreditCardStatement.statementFor(
            cycle = future,
            nowEpochMillis = at(2026, 10, 22),
            outstandingNowMinorUnits = 2_000L,
            movementSinceCycleEndMinorUnits = 0L
        )

        assertNull("a cycle that has not started cannot have a statement", statement)
    }

    @Test
    fun aClosedCyclesStatementIsWhatWasOwedWhenItClosed() {
        val closed = window(at(2026, 9, 1), at(2026, 10, 1))

        val statement = CreditCardStatement.statementFor(
            cycle = closed,
            nowEpochMillis = at(2026, 10, 22),
            outstandingNowMinorUnits = 5_000L,
            // 3,000 of the current outstanding was added AFTER the cycle closed.
            movementSinceCycleEndMinorUnits = 3_000L
        )

        assertEquals(2_000L, statement)
    }

    @Test
    fun theRunningCyclesStatementIsWhatTheCardOwesRightNow() {
        val running = window(at(2026, 10, 1), at(2026, 11, 1))

        val statement = CreditCardStatement.statementFor(
            cycle = running,
            nowEpochMillis = at(2026, 10, 22),
            outstandingNowMinorUnits = 5_000L,
            movementSinceCycleEndMinorUnits = 0L
        )

        assertEquals(5_000L, statement)
    }
}
