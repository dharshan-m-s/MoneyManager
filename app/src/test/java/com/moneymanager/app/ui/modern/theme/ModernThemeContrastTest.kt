package com.moneymanager.app.ui.modern.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast audit of the Modern Look. Ratios are computed from the real palette values
 * used by the UI, never asserted by hand, so a failure prints the measured number.
 *
 * Targets: 4.5:1 for text, 3:1 for meaningful non-text glyphs, 1.1:1 for structural separation
 * between adjacent surface planes.
 */
class ModernThemeContrastTest {

    private fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun assertAtLeast(min: Double, fg: Color, bg: Color, label: String) {
        val r = ratio(fg, bg)
        assertTrue(
            "$label: measured ${"%.2f".format(r)}:1, required >= $min:1 " +
                "(fg=#${fg.value.toString(16)}, bg=#${bg.value.toString(16)})",
            r >= min
        )
    }

    // --- Dark mode: the priority mode ---

    @Test
    fun darkTextRolesAreReadableOnEverySurface() {
        listOf(
            "background" to DarkBackground,
            "surface" to DarkSurface,
            "surfaceMuted" to DarkSurfaceContainer,
            "surfaceElevated" to DarkSurfaceContainerHigh
        ).forEach { (name, bg) ->
            assertAtLeast(4.5, DarkTextPrimary, bg, "dark textPrimary / $name")
            assertAtLeast(4.5, DarkTextSecondary, bg, "dark textSecondary / $name")
        }
        assertAtLeast(4.5, DarkTextTertiary, DarkSurface, "dark textTertiary / surface")
    }

    @Test
    fun darkSemanticRolesCarryMeaningAtTextContrast() {
        assertAtLeast(4.5, IncomeDark, DarkSurface, "dark income / surface")
        assertAtLeast(4.5, ExpenseDark, DarkSurface, "dark expense / surface")
        assertAtLeast(4.5, WarningDark, DarkSurface, "dark warning / surface")
        assertAtLeast(4.5, PrimaryDark, DarkSurface, "dark accent / surface")
        assertAtLeast(4.5, PrimaryDark, DarkSurfaceContainer, "dark accent / surfaceMuted")
        assertAtLeast(4.5, OnPrimary, PrimaryFillDark, "dark onAccent / accentFill")
    }

    @Test
    fun darkSurfaceStackIsStructurallyDistinct() {
        assertAtLeast(1.1, DarkSurface, DarkBackground, "dark surface vs background")
        assertAtLeast(1.1, DarkSurfaceContainer, DarkSurface, "dark surfaceMuted vs surface")
        assertAtLeast(1.1, DarkSurfaceContainerHigh, DarkSurface, "dark surfaceElevated vs surface")
        CategoryDark.forEach { tint ->
            assertAtLeast(3.0, tint, DarkSurface, "dark category tint $tint / surface")
        }
    }

    // --- Light mode ---

    @Test
    fun lightTextRolesAreReadableOnEverySurface() {
        listOf(
            "background" to LightBackground,
            "surface" to LightSurface,
            "surfaceMuted" to LightSurfaceContainer
        ).forEach { (name, bg) ->
            assertAtLeast(4.5, LightTextPrimary, bg, "light textPrimary / $name")
            assertAtLeast(4.5, LightTextSecondary, bg, "light textSecondary / $name")
        }
        assertAtLeast(4.5, LightTextTertiary, LightSurface, "light textTertiary / surface")
    }

    @Test
    fun lightSemanticRolesCarryMeaningAtTextContrast() {
        assertAtLeast(4.5, IncomeLight, LightSurface, "light income / surface")
        assertAtLeast(4.5, ExpenseLight, LightSurface, "light expense / surface")
        assertAtLeast(4.5, WarningLight, LightSurface, "light warning / surface")
        assertAtLeast(4.5, PrimaryLight, LightSurface, "light accent / surface")
        assertAtLeast(4.5, OnPrimary, PrimaryLight, "light onAccent / accentFill")
    }

    @Test
    fun lightSurfaceStackIsStructurallyDistinct() {
        assertAtLeast(1.2, LightOutline, LightBackground, "light outline vs background")
        assertAtLeast(1.1, LightSurfaceContainerHigh, LightSurface, "light surfaceElevated vs surface")
        CategoryLight.forEach { tint ->
            assertAtLeast(3.0, tint, LightSurface, "light category tint $tint / surface")
        }
    }
}
