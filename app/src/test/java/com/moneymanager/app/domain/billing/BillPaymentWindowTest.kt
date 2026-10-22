package com.moneymanager.app.domain.billing

import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.domain.model.BusinessPersonal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Mark as paid" must only be offered once the payment date is near. An occurrence that is
 * months away has nothing to settle yet; an overdue or partially paid one always does.
 */
class BillPaymentWindowTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun now(day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(2026, 10, day))

    private fun bill(reminderDays: Int = 10): BillEntity = BillEntity(
        billerName = "Electricity",
        billerType = BillerType.ELECTRICITY,
        accountReferenceId = "x1",
        nickname = "Electricity",
        billingCycle = BillingCycle.MONTHLY,
        nextDueDateEpochMillis = now(20),
        scheduleAnchorDay = 20,
        estimatedAmountMinorUnits = 5_000,
        businessPersonal = BusinessPersonal.PERSONAL,
        reminderDaysBefore = reminderDays,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0
    )

    private fun instance(dueDay: Int, paid: Boolean = false, paidAmount: Long = 0L): BillInstanceEntity =
        BillInstanceEntity(
            billId = 1,
            dueDateEpochMillis = now(dueDay),
            amountDueMinorUnits = 5_000,
            paid = paid,
            paidAmountMinorUnits = paidAmount
        )

    @Test
    fun notPayableWhenTheDueDateIsMonthsAway() {
        val b = bill(reminderDays = 10)
        // Due in six months: the head occurrence is materialized but nothing is owed yet.
        val dueFar = BillInstanceEntity(
            billId = 1,
            dueDateEpochMillis = BillSchedule.epochOf(LocalDate.of(2027, 4, 20)),
            amountDueMinorUnits = 5_000
        )
        assertFalse(BillPaymentWindow.isPayableNow(b, dueFar, now(20), zone))
    }

    @Test
    fun payableInsideTheLeadWindow() {
        val b = bill(reminderDays = 10)
        // Due in 3 days: inside a 10-day window.
        assertTrue(BillPaymentWindow.isPayableNow(b, instance(23), now(20), zone))
        // Due in 10 days: the edge of the window is inclusive.
        assertTrue(BillPaymentWindow.isPayableNow(b, instance(30), now(20), zone))
        // Due in 11 days: just outside.
        assertFalse(BillPaymentWindow.isPayableNow(b, instance(31), now(20), zone))
    }

    @Test
    fun overdueAndPartialOccurrencesAreAlwaysPayable() {
        val b = bill(reminderDays = 3)
        assertTrue("overdue must stay actionable", BillPaymentWindow.isPayableNow(b, instance(15), now(20), zone))
        assertTrue(
            "a partly paid bill still owes money",
            BillPaymentWindow.isPayableNow(b, instance(30, paidAmount = 1_000), now(20), zone)
        )
    }

    @Test
    fun settledOrMissingOccurrencesAreNotPayable() {
        val b = bill()
        assertFalse(BillPaymentWindow.isPayableNow(b, null, now(20), zone))
        assertFalse(BillPaymentWindow.isPayableNow(b, instance(20, paid = true, paidAmount = 5_000), now(20), zone))
    }

    @Test
    fun leadDaysAreClampedToASaneMinimum() {
        assertEquals(1, BillPaymentWindow.leadDays(bill(reminderDays = 0)))
        assertEquals(1, BillPaymentWindow.leadDays(bill(reminderDays = -5)))
    }

    @Test
    fun theReminderSettingIsWhatDrivesTheWindow() {
        // The "days before the due date" the user typed is the ONLY lead-time dial: a bill
        // configured to remind 20 days early becomes payable 20 days early, and the same bill
        // configured for 2 days does not.
        val wide = bill(reminderDays = 20)
        val narrow = bill(reminderDays = 2)
        val due = instance(30) // 10 days away
        assertTrue(BillPaymentWindow.isPayableNow(wide, due, now(20), zone))
        assertFalse(BillPaymentWindow.isPayableNow(narrow, due, now(20), zone))
        assertEquals(20, BillPaymentWindow.leadDays(wide))
        assertEquals(2, BillPaymentWindow.leadDays(narrow))
    }

    @Test
    fun theDefaultReminderWindowIsTenDays() {
        // A freshly created bill (no explicit reminder value) is payable ten days ahead.
        val defaulted = BillEntity(
            billerName = "Water",
            billerType = BillerType.WATER,
            accountReferenceId = "x2",
            nickname = "Water",
            billingCycle = BillingCycle.MONTHLY,
            nextDueDateEpochMillis = now(20),
            scheduleAnchorDay = 20,
            estimatedAmountMinorUnits = 5_000,
            businessPersonal = BusinessPersonal.PERSONAL,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
        assertEquals(10, defaulted.reminderDaysBefore)
        assertEquals(10, BillPaymentWindow.leadDays(defaulted))
        assertTrue(BillPaymentWindow.isPayableNow(defaulted, instance(30), now(20), zone))
        assertFalse(BillPaymentWindow.isPayableNow(defaulted, instance(31), now(20), zone))
    }

    @Test
    fun daysUntilDueIsSignedAndCalendarBased() {
        assertEquals(5L, BillPaymentWindow.daysUntilDue(now(25), now(20), zone))
        assertEquals(-5L, BillPaymentWindow.daysUntilDue(now(15), now(20), zone))
        assertEquals(0L, BillPaymentWindow.daysUntilDue(now(20), now(20), zone))
    }
}
