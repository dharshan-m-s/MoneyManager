package com.moneymanager.app.domain.accounting

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckpointMathTest {

    @Test
    fun checkpointOffsetIsDifferenceBetweenEnteredAndDerivedBalance() {
        val derived = 12_000L
        val entered = 15_000L
        val adjustment = AccountingEngine.checkpointAdjustment(entered, derived)
        assertEquals(3_000L, adjustment)
        assertEquals(15_000L, AccountingEngine.effectiveBalanceWithCheckpoint(derived, adjustment))
    }

    @Test
    fun laterMovementsApplyExactlyOnceOnTopOfTheCheckpoint() {
        val derivedAtCheckpoint = 12_000L
        val adjustment = AccountingEngine.checkpointAdjustment(15_000L, derivedAtCheckpoint)

        // A purchase after the checkpoint (or a backdated one entered later) changes the
        // derived base; the stored offset is unchanged.
        assertEquals(14_800L, AccountingEngine.effectiveBalanceWithCheckpoint(11_800L, adjustment))
        // A payment/income in the other direction also applies.
        assertEquals(15_500L, AccountingEngine.effectiveBalanceWithCheckpoint(12_500L, adjustment))
    }

    @Test
    fun noCheckpointMeansDerivedBalanceIsDisplayed() {
        assertEquals(12_000L, AccountingEngine.effectiveBalanceWithCheckpoint(12_000L, null))
    }

    @Test
    fun cardOutstandingCheckpointBehavesTheSameWay() {
        val derivedOutstanding = 8_000L
        val enteredOutstanding = 10_000L
        val adjustment = AccountingEngine.checkpointAdjustment(enteredOutstanding, derivedOutstanding)
        assertEquals(10_000L, AccountingEngine.effectiveBalanceWithCheckpoint(derivedOutstanding, adjustment))
        assertEquals(9_500L, AccountingEngine.effectiveBalanceWithCheckpoint(7_500L, adjustment))
    }
}
