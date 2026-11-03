package com.moneymanager.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernGlassCompatibilityTest {
    @Test fun backdropBlurFallsBackBeforeAndroid12() {
        assertFalse(isMmLiquidGlassSupported(30))
    }
    @Test fun backdropBlurIsEnabledFromAndroid12() {
        assertTrue(isMmLiquidGlassSupported(31))
        assertTrue(isMmLiquidGlassSupported(32))
        assertTrue(isMmLiquidGlassSupported(33))
    }
    @Test fun captureResolutionPreservesDetailForRefraction() {
        assertEquals(0.5f, mmGlassBackdropScale(), 0.001f)
    }
    @Test fun lensDominatesBlurForLiquidGlass() {
        assertTrue(mmGlassRefractionAmountDp() > mmGlassBlurRadiusDp() * 4f)
    }
}
