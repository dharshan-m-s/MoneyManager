package com.moneymanager.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletCardMaskingTest {
    @Test fun fullPanIsReducedToLastFourDigits() {
        assertEquals("•••• 1234", maskedCardNumberFromSourceId("4111111111111234"))
    }
    @Test fun accountReferenceKeepsOnlyLastFourDigits() {
        assertEquals("•••• 6058", maskedCardNumberFromSourceId("ICIC-x6058"))
    }
    @Test fun shortOrMissingIdentifiersDoNotLeak() {
        assertNull(maskedCardNumberFromSourceId(null))
        assertNull(maskedCardNumberFromSourceId(""))
        assertNull(maskedCardNumberFromSourceId("123"))
    }
}
