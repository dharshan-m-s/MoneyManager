package com.moneymanager.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernGlassCompatibilityTest {

    @Test
    fun backdropBlurFallsBackBeforeAndroid12() {
        assertFalse(isMmLiquidGlassSupported(30))
    }

    @Test
    fun backdropBlurIsEnabledFromAndroid12() {
        assertTrue(isMmLiquidGlassSupported(31))
        assertTrue(isMmLiquidGlassSupported(32))
        assertTrue(isMmLiquidGlassSupported(33))
    }
}
