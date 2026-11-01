package com.moneymanager.app.domain.accounting

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BillingCycleAnchorTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun cycleStart(day: Int, date: LocalDate): LocalDate {
        val epoch = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val window = AccountingEngine.billingCycleWindowFor(day, epoch, zone)
        return Instant.ofEpochMilli(window.startInclusive).atZone(zone).toLocalDate()
    }

    @Test
    fun thirtyFirstAnchorClampsInFebruaryAndRestoresInMarch() {
        assertEquals(LocalDate.of(2025, 1, 31), cycleStart(31, LocalDate.of(2025, 2, 15)))
        assertEquals(LocalDate.of(2025, 2, 28), cycleStart(31, LocalDate.of(2025, 3, 15)))
        assertEquals(LocalDate.of(2025, 3, 31), cycleStart(31, LocalDate.of(2025, 4, 15)))
    }

    @Test
    fun thirtyFirstAnchorUsesLeapDayWithoutDrifting() {
        assertEquals(LocalDate.of(2024, 1, 31), cycleStart(31, LocalDate.of(2024, 2, 15)))
        assertEquals(LocalDate.of(2024, 2, 29), cycleStart(31, LocalDate.of(2024, 3, 15)))
        assertEquals(LocalDate.of(2024, 3, 31), cycleStart(31, LocalDate.of(2024, 4, 15)))
    }

    @Test
    fun twentyNinthAndThirtiethAnchorsArePreservedAcrossShortMonths() {
        assertEquals(LocalDate.of(2025, 2, 28), cycleStart(29, LocalDate.of(2025, 3, 15)))
        assertEquals(LocalDate.of(2025, 3, 30), cycleStart(30, LocalDate.of(2025, 4, 15)))
    }
}
