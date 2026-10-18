package com.moneymanager.app.data

import androidx.room.Room
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.model.BusinessPersonal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["com.moneymanager.app"])
class BillRecurrenceServiceTest {

    private lateinit var db: MoneyManagerDatabase
    private lateinit var service: BillRecurrenceService

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MoneyManagerDatabase::class.java
        ).allowMainThreadQueries().build()
        service = BillRecurrenceService(db.billDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun at(year: Int, month: Int, day: Int): Long =
        BillSchedule.epochOf(LocalDate.of(year, month, day))

    private suspend fun insertBill(
        cycle: BillingCycle,
        due: Long,
        anchor: Int,
        autoGenerate: Boolean = true,
        amount: Long = 50_000L
    ): Long = db.billDao().insert(
        BillEntity(
            billerName = "Test biller",
            billerType = BillerType.OTHER,
            accountReferenceId = "ref",
            nickname = "Test",
            billingCycle = cycle,
            nextDueDateEpochMillis = due,
            scheduleAnchorDay = anchor,
            estimatedAmountMinorUnits = amount,
            autoGenerateBills = autoGenerate,
            autoGenerateLeadDays = 10,
            businessPersonal = BusinessPersonal.PERSONAL,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0
        )
    )

    @Test
    fun monthlyGenerationIsIdempotentAndCatchesUp() = runBlocking {
        val due = at(2026, 1, 31)
        val billId = insertBill(BillingCycle.MONTHLY, due, 31)

        service.reconcileBill(billId, now = at(2026, 1, 1))
        assertEquals(listOf(due), db.billDao().findInstancesForBill(billId).map { it.dueDateEpochMillis })

        // The lead window opens 10 days before Feb 28 (i.e. Feb 18). Run several times.
        val now = at(2026, 2, 20)
        service.reconcileBill(billId, now)
        service.reconcileBill(billId, now)
        service.reconcileBill(billId, now)

        val instances = db.billDao().findInstancesForBill(billId)
        assertEquals(listOf(due, at(2026, 2, 28)), instances.map { it.dueDateEpochMillis })
        // No drift: the next schedule still uses the 31st anchor.
        assertEquals(31, db.billDao().findById(billId)!!.scheduleAnchorDay)
    }

    @Test
    fun bimonthlyCreatesOneOccurrenceEveryTwoMonths() = runBlocking {
        val due = at(2026, 1, 31)
        val billId = insertBill(BillingCycle.BIMONTHLY, due, 31)
        service.reconcileBill(billId, now = at(2026, 3, 25))
        assertEquals(
            listOf(due, at(2026, 3, 31)),
            db.billDao().findInstancesForBill(billId).map { it.dueDateEpochMillis }
        )
        service.reconcileBill(billId, now = at(2026, 3, 25))
        assertEquals(2, db.billDao().findInstancesForBill(billId).size)
    }

    @Test
    fun autoGenerateOffKeepsOnlyTheFirstOccurrence() = runBlocking {
        val due = at(2026, 1, 31)
        val billId = insertBill(BillingCycle.MONTHLY, due, 31, autoGenerate = false)
        service.reconcileBill(billId, now = at(2026, 6, 1))
        assertEquals(listOf(due), db.billDao().findInstancesForBill(billId).map { it.dueDateEpochMillis })
    }

    @Test
    fun paidHistoryIsNeverRegeneratedOrDeletedOnEdit() = runBlocking {
        val due = at(2026, 1, 31)
        val billId = insertBill(BillingCycle.MONTHLY, due, 31)
        service.reconcileBill(billId, now = at(2026, 2, 20))
        val feb = db.billDao().findInstance(billId, at(2026, 2, 28))!!
        db.billDao().updateInstance(feb.copy(paid = true, paidAtEpochMillis = at(2026, 2, 25)))

        // Change the recurrence to quarterly starting 31 March.
        val bill = db.billDao().findById(billId)!!
        db.billDao().update(
            bill.copy(
                billingCycle = BillingCycle.QUARTERLY,
                nextDueDateEpochMillis = at(2026, 3, 31),
                scheduleAnchorDay = 31
            )
        )
        service.reconcileAfterEdit(billId, now = at(2026, 3, 1))

        val instances = db.billDao().findInstancesForBill(billId)
        assertTrue(instances.any { it.dueDateEpochMillis == at(2026, 2, 28) && it.paid })
        assertNotNull(db.billDao().findInstance(billId, at(2026, 3, 31)))
        // A monthly April occurrence must not survive a quarterly schedule.
        assertEquals(null, db.billDao().findInstance(billId, at(2026, 4, 30)))
    }

    @Test
    fun paidHeadAdvancesNextDueToTheNextScheduledOccurrence() = runBlocking {
        val due = at(2026, 10, 20)
        val billId = insertBill(BillingCycle.MONTHLY, due, 20)
        service.reconcileBill(billId, now = at(2026, 10, 15))
        assertEquals(listOf(due), db.billDao().findInstancesForBill(billId).map { it.dueDateEpochMillis })

        // The user settles the October occurrence.
        val october = db.billDao().findInstance(billId, due)!!
        db.billDao().updateInstance(october.copy(paid = true, paidAtEpochMillis = at(2026, 10, 18)))

        // The next reconcile advances the schedule off the paid occurrence - even though the
        // November date is still outside the 10-day lead window. nextDue must never stay
        // parked on a settled occurrence (the card/bill forms read it as "next due date").
        service.reconcileBill(billId, now = at(2026, 10, 18))
        assertEquals(at(2026, 11, 20), db.billDao().findById(billId)!!.nextDueDateEpochMillis)

        val instances = db.billDao().findInstancesForBill(billId)
        assertEquals(
            listOf(at(2026, 10, 20), at(2026, 11, 20)),
            instances.map { it.dueDateEpochMillis }
        )
        assertTrue(instances.first { it.dueDateEpochMillis == at(2026, 10, 20) }.paid)
        assertTrue(!instances.first { it.dueDateEpochMillis == at(2026, 11, 20) }.paid)

        // Idempotent: re-running changes nothing.
        service.reconcileBill(billId, now = at(2026, 10, 18))
        assertEquals(2, db.billDao().findInstancesForBill(billId).size)
        assertEquals(at(2026, 11, 20), db.billDao().findById(billId)!!.nextDueDateEpochMillis)
    }

    @Test
    fun overdueUnpaidHeadIsNeverAdvanced() = runBlocking {
        val due = at(2026, 10, 20)
        val billId = insertBill(BillingCycle.MONTHLY, due, 20)
        service.reconcileBill(billId, now = at(2026, 10, 15))

        // Past the due date with no payment: the occurrence stays the next due date.
        service.reconcileBill(billId, now = at(2026, 10, 25))
        assertEquals(due, db.billDao().findById(billId)!!.nextDueDateEpochMillis)
        assertEquals(listOf(due), db.billDao().findInstancesForBill(billId).map { it.dueDateEpochMillis })
        assertTrue(!db.billDao().findInstance(billId, due)!!.paid)
    }
}
