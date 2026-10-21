package com.moneymanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Semantic colour tokens for the Money Manager light and dark palettes.
 *
 * Colour dynamics in this app:
 *  - One brand family: green. The chrome (top bars, launcher) uses [MMGreenDark]; primary
 *    actions (buttons, FAB, chips, links) use the same green family so the app reads as one
 *    product in both modes.
 *  - Two semantic money colours that never drift: income (green) and expense (red). Warning /
 *    due / overdue are amber → red steps. These are lifted in dark mode so they stay legible
 *    on dark surfaces without vibrating.
 *  - A three-step surface stack (background → surface → surfaceMuted) gives depth without
 *    shadows: cards are surface, inset rows/tiles are surfaceMuted, the window is background.
 *  - All Compose colour literals MUST be 8-digit ARGB. `Color(0xRRGGBB)` silently produces a
 *    fully transparent colour (alpha 0x00) — that bug previously blanked the whole dark theme.
 *
 * Screens must use the MmColors accessors (below) instead of the fixed palette constants.
 */

/** Brand chrome colour — the dark green top bars and the app identity. */
val MMGreenDark = Color(0xFF006B45)
val MMWhite = Color(0xFFFFFFFF)

/**
 * Deliberate brand-header surfaces (dashboard hero, account/credit-card headers, lock gate).
 *
 * These are NOT inversions of each other: light mode uses the confident brand green as a
 * saturated identity band; dark mode uses a deeper, desaturated brand green so the band reads as
 * the same product without glare or a green-over-slate clash. Text on them is always [MMWhite].
 */
val MmBrandHeaderLight = Color(0xFF0B6B4A)
val MmBrandHeaderLightEnd = Color(0xFF0A6E4B)
val MmBrandHeaderDark = Color(0xFF0C3B2C)
val MmBrandHeaderDarkEnd = Color(0xFF0E5A40)

/** Credit-card header is a distinct brand family (blue) so a card never reads as a bank account. */
val MmCardHeaderLight = Color(0xFF1B4D8F)
val MmCardHeaderLightEnd = Color(0xFF245FA8)
val MmCardHeaderDark = Color(0xFF16305A)
val MmCardHeaderDarkEnd = Color(0xFF1C4179)

/** Light-mode palette — crisp neutral surfaces, deep green primary. */
val MmPaletteLightBackground = Color(0xFFF6F8F7)
val MmPaletteLightSurface = Color(0xFFFFFFFF)
val MmPaletteLightSurfaceMuted = Color(0xFFF0F4F2)
val MmPaletteLightDivider = Color(0xFFE1E8E3)
val MmPaletteLightOutline = Color(0xFFCBD5E1)
val MmPaletteLightTextPrimary = Color(0xFF151917)
val MmPaletteLightTextSecondary = Color(0xFF64706A)
val MmPaletteLightTextTertiary = Color(0xFF94A3B8)
val MmPaletteLightAccent = Color(0xFF067A4C)
val MmPaletteLightOnAccent = Color(0xFFFFFFFF)
val MmPaletteLightIncome = Color(0xFF047857)
val MmPaletteLightExpense = Color(0xFFDC2626)
val MmPaletteLightWarning = Color(0xFFD97706)
val MmPaletteLightAmberDue = Color(0xFFB45309)
val MmPaletteLightMaroonOverdue = Color(0xFF9F1239)
val MmPaletteLightCyan = Color(0xFF0891B2)
val MmPaletteLightPurple = Color(0xFF7C3AED)
val MmPaletteLightBrown = Color(0xFF92400E)
val MmPaletteLightIconShadow = Color(0x1A000000)

/** Dark-mode palette — deep navy-slate stack with lifted semantic colours. */
val MmPaletteDarkBackground = Color(0xFF080E1A)
val MmPaletteDarkSurface = Color(0xFF131C2E)
val MmPaletteDarkSurfaceMuted = Color(0xFF1C2740)
val MmPaletteDarkDivider = Color(0xFF2A3752)
val MmPaletteDarkOutline = Color(0xFF475569)
val MmPaletteDarkTextPrimary = Color(0xFFF1F5F9)
val MmPaletteDarkTextSecondary = Color(0xFF94A3B8)
val MmPaletteDarkTextTertiary = Color(0xFF64748B)
val MmPaletteDarkAccent = Color(0xFF34D399)
val MmPaletteDarkOnAccent = Color(0xFF052E22)
val MmPaletteDarkIncome = Color(0xFF34D399)
val MmPaletteDarkExpense = Color(0xFFF87171)
val MmPaletteDarkWarning = Color(0xFFFBBF24)
val MmPaletteDarkAmberDue = Color(0xFFF59E0B)
val MmPaletteDarkMaroonOverdue = Color(0xFFFB7185)
val MmPaletteDarkCyan = Color(0xFF22D3EE)
val MmPaletteDarkPurple = Color(0xFFA78BFA)
val MmPaletteDarkBrown = Color(0xFFFBBF24)
val MmPaletteDarkIconShadow = Color(0x32000000)

/**
 * One consistent spacing scale for the whole app. Every redesigned screen pulls its padding and
 * gaps from these tokens instead of ad-hoc 7dp/13dp/19dp values, so rhythm stays predictable.
 */
object MmSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Minimum comfortable horizontal margin (One UI reachability/breathing-room principle). */
    val screen = 20.dp

    /** Standard content padding inside a card/surface. */
    val card = 16.dp

    /** Minimum touch target (accessibility). */
    val touchTarget = 48.dp

    /** Standard corner radii (generous, confident rounding). */
    val radiusCard = 22.dp
    val radiusRow = 16.dp
    val radiusField = 16.dp
    val radiusSheet = 30.dp
}

/**
 * Colour ROLES resolved against the active theme. Screens must use these accessors instead of
 * the fixed palette constants above, so dark mode gets correct surfaces, dividers and text
 * contrast rather than inverted light values.
 */
object MmColors {
    private val isLight: Boolean @Composable get() = MaterialTheme.colors.isLight

    val background: Color @Composable get() = MaterialTheme.colors.background
    val surface: Color @Composable get() = MaterialTheme.colors.surface
    val surfaceMuted: Color @Composable get() = if (isLight) MmPaletteLightSurfaceMuted else MmPaletteDarkSurfaceMuted
    val divider: Color @Composable get() = if (isLight) MmPaletteLightDivider else MmPaletteDarkDivider
    val outline: Color @Composable get() = if (isLight) MmPaletteLightOutline else MmPaletteDarkOutline
    val textPrimary: Color @Composable get() = MaterialTheme.colors.onSurface
    val textSecondary: Color @Composable get() = if (isLight) MmPaletteLightTextSecondary else MmPaletteDarkTextSecondary
    val textTertiary: Color @Composable get() = if (isLight) MmPaletteLightTextTertiary else MmPaletteDarkTextTertiary

    /** Brand/primary accent that stays readable on both light and dark surfaces. */
    val accent: Color @Composable get() = MaterialTheme.colors.primary

    /**
     * Foreground for content sitting ON [accent] (filled buttons, brand headers).
     * Uses MaterialTheme.colors.onPrimary which automatically adapts to light/dark.
     */
    val onAccent: Color @Composable get() = MaterialTheme.colors.onPrimary
    /** Income — resolves to correct light/dark value. */
    val income: Color @Composable get() = if (isLight) MmPaletteLightIncome else MmPaletteDarkIncome
    /** Expense — resolves to correct light/dark value. */
    val expense: Color @Composable get() = if (isLight) MmPaletteLightExpense else MmPaletteDarkExpense
    /** Warning — resolves to correct light/dark value. */
    val warning: Color @Composable get() = if (isLight) MmPaletteLightWarning else MmPaletteDarkWarning

    /**
     * Foreground for text/icons sitting on a [brandHeader] / [brandGradient] band. These bands are
     * always dark enough for white, so this is deliberately constant rather than theme-derived.
     */
    val onBrand: Color get() = MMWhite

    /**
     * Restrained brand gradient for identity bands (dashboard hero, account header, lock gate).
     * Two closely-related brand stops, not a green→background wash: it improves hierarchy on the
     * header itself and never fades into an unrelated surface colour.
     */
    val brandGradient: List<Color> @Composable get() = if (isLight) {
        listOf(MmBrandHeaderLight, MmBrandHeaderLightEnd)
    } else {
        listOf(MmBrandHeaderDark, MmBrandHeaderDarkEnd)
    }

    /** Blue gradient for the credit-card header — the same treatment, a different brand family. */
    val cardHeaderGradient: List<Color> @Composable get() = if (isLight) {
        listOf(MmCardHeaderLight, MmCardHeaderLightEnd)
    } else {
        listOf(MmCardHeaderDark, MmCardHeaderDarkEnd)
    }
}

/** Semantic text styles used across redesigned screens (financial hierarchy in one place). */
object MmType {
    // "tnum" gives financial digits equal widths, so amounts stay perfectly aligned and stop
    // jittering as values change — a signature of polished money UIs.
    private const val TABULAR_NUMS = "tnum"

    val screenTitle: TextStyle @Composable get() = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val sectionTitle: TextStyle @Composable get() = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val label: TextStyle @Composable get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val caption: TextStyle @Composable get() = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val amountHero: TextStyle @Composable get() = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, fontFeatureSettings = TABULAR_NUMS)
    val amountLarge: TextStyle @Composable get() = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, fontFeatureSettings = TABULAR_NUMS)
    val amountRow: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_NUMS)
    val amountTile: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_NUMS)
}

@Composable
fun MoneyManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) DarkMoneyManagerColors else LightMoneyManagerColors,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        typography = Typography(
            h4 = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            h5 = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            h6 = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            subtitle1 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            subtitle2 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            body1 = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
            body2 = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            button = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

/** Light-mode Colors object — built from semantic tokens above. */
private val LightMoneyManagerColors: Colors = lightColors(
    primary = MmPaletteLightAccent,
    primaryVariant = Color(0xFF07704A),
    secondary = MmPaletteLightCyan,
    background = MmPaletteLightBackground,
    surface = MmPaletteLightSurface,
    onPrimary = MmPaletteLightOnAccent,
    onSecondary = MmPaletteLightTextPrimary,
    onBackground = MmPaletteLightTextPrimary,
    onSurface = MmPaletteLightTextPrimary,
    error = MmPaletteLightExpense
)

/** Dark-mode Colors object — built from semantic tokens above. */
private val DarkMoneyManagerColors: Colors = darkColors(
    primary = MmPaletteDarkAccent,
    primaryVariant = Color(0xFF059669),
    secondary = MmPaletteDarkCyan,
    background = MmPaletteDarkBackground,
    surface = MmPaletteDarkSurface,
    onPrimary = MmPaletteDarkOnAccent,
    onSecondary = MmPaletteDarkTextPrimary,
    onBackground = MmPaletteDarkTextPrimary,
    onSurface = MmPaletteDarkTextPrimary,
    error = MmPaletteDarkExpense
)
