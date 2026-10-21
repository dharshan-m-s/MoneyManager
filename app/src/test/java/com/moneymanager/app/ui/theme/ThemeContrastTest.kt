package com.moneymanager.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Section 11: a reproducible WCAG contrast audit of the semantic palette. Every assertion is
 * computed from the ACTUAL colour values (sRGB relative luminance), not asserted by hand, and the
 * failure messages print the measured ratio so the numbers can be trusted.
 *
 * Targets (WCAG 2.1):
 *  - ordinary text: >= 4.5:1
 *  - large text / non-text UI (icons, borders, controls): >= 3:1
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertAtLeast(min: Double, fg: Color, bg: Color, label: String) {
        val r = ratio(fg, bg)
        assertTrue(
            "$label: measured ${"%.2f".format(r)}:1, required >= $min:1 " +
                "(fg=#${fg.value.toString(16)}, bg=#${bg.value.toString(16)})",
            r >= min
        )
    }

    private val lightBackground = MmPaletteLightBackground
    private val lightSurface = MmPaletteLightSurface
    private val lightSurfaceMuted = MmPaletteLightSurfaceMuted
    private val darkBackground = MmPaletteDarkBackground
    private val darkSurface = MmPaletteDarkSurface
    private val darkSurfaceMuted = MmPaletteDarkSurfaceMuted

    // --- Light mode ---

    @Test
    fun lightTextOnBackgroundAndSurfaces() {
        assertAtLeast(4.5, MmPaletteLightTextPrimary, lightBackground, "light textPrimary / background")
        assertAtLeast(4.5, MmPaletteLightTextPrimary, lightSurface, "light textPrimary / surface")
        assertAtLeast(4.5, MmPaletteLightTextSecondary, lightSurface, "light textSecondary / surface")
        assertAtLeast(4.5, MmPaletteLightTextPrimary, lightSurfaceMuted, "light textPrimary / surfaceMuted")
    }

    @Test
    fun lightSemanticColoursOnSurfaces() {
        assertAtLeast(4.5, MmPaletteLightIncome, lightSurface, "light income / surface")
        assertAtLeast(4.5, MmPaletteLightExpense, lightSurface, "light expense / surface")
        assertAtLeast(3.0, MmPaletteLightWarning, lightSurface, "light warning / surface (large/icon)")
        assertAtLeast(4.5, MmPaletteLightAccent, lightSurface, "light accent / surface")
        assertAtLeast(4.5, MmPaletteLightOnAccent, MmPaletteLightAccent, "light onAccent / accent")
    }

    // --- Dark mode ---

    @Test
    fun darkTextOnBackgroundAndSurfaces() {
        assertAtLeast(4.5, MmPaletteDarkTextPrimary, darkBackground, "dark textPrimary / background")
        assertAtLeast(4.5, MmPaletteDarkTextPrimary, darkSurface, "dark textPrimary / surface")
        assertAtLeast(4.5, MmPaletteDarkTextSecondary, darkSurface, "dark textSecondary / surface")
        assertAtLeast(4.5, MmPaletteDarkTextPrimary, darkSurfaceMuted, "dark textPrimary / surfaceMuted")
    }

    @Test
    fun darkSemanticColoursOnSurfaces() {
        assertAtLeast(4.5, MmPaletteDarkIncome, darkSurface, "dark income / surface")
        assertAtLeast(4.5, MmPaletteDarkExpense, darkSurface, "dark expense / surface")
        assertAtLeast(4.5, MmPaletteDarkWarning, darkSurface, "dark warning / surface")
        assertAtLeast(4.5, MmPaletteDarkAccent, darkSurface, "dark accent / surface")
        assertAtLeast(4.5, MmPaletteDarkOnAccent, MmPaletteDarkAccent, "dark onAccent / accent")
    }

    // --- Header bands carry white text, so the band must stay dark enough in BOTH themes ---

    @Test
    fun whiteTextIsReadableOnEveryBrandHeaderBand() {
        assertAtLeast(4.5, MMWhite, MmBrandHeaderLight, "white / brandHeaderLight start")
        assertAtLeast(4.5, MMWhite, MmBrandHeaderLightEnd, "white / brandHeaderLight end")
        assertAtLeast(4.5, MMWhite, MmBrandHeaderDark, "white / brandHeaderDark start")
        assertAtLeast(4.5, MMWhite, MmBrandHeaderDarkEnd, "white / brandHeaderDark end")
        assertAtLeast(4.5, MMWhite, MmCardHeaderLight, "white / cardHeaderLight start")
        assertAtLeast(4.5, MMWhite, MmCardHeaderLightEnd, "white / cardHeaderLight end")
        assertAtLeast(4.5, MMWhite, MmCardHeaderDark, "white / cardHeaderDark start")
        assertAtLeast(4.5, MMWhite, MmCardHeaderDarkEnd, "white / cardHeaderDark end")
    }

    // --- Structural separation: surfaces must be distinguishable without relying on text ---

    @Test
    fun darkSurfaceStackIsDistinguishable() {
        assertAtLeast(1.1, darkSurface, darkBackground, "dark surface vs background")
        assertAtLeast(1.1, darkSurfaceMuted, darkSurface, "dark surfaceMuted vs surface")
    }

    @Test
    fun lightSurfaceStackIsDistinguishable() {
        assertTrue("light surface differs from background", lightSurface != lightBackground)
        assertTrue("light surfaceMuted differs from surface", lightSurfaceMuted != lightSurface)
    }
}
