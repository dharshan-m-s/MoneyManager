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

// One UI-inspired Money Manager palette: calm green brand, warm neutral surfaces and restrained
// semantic accents.
//
// IMPORTANT: the values below are FIXED colours. Only three groups may be used directly in
// screens:
//   1. [MMGreenDark] / [MMWhite] - the brand top bar, which stays brand-green in both themes.
//   2. the chart/category hues ([MMGreen], [MMBlue], [MMBrown], [MMPurple], [MMCyan], [MMAmberDue]).
//   3. [MMIconShadow] for the category icon elevation.
// Everything else - backgrounds, surfaces, dividers, outlines, body text and the semantic
// income/expense/warning colours - MUST come from the theme-resolved [MmColors] roles, because
// these literals are light-mode values and would break dark mode.
val MMGreenDark = Color(0xFF006B45)
val MMGreen = Color(0xFF0A8F5A)
val MMWhite = Color(0xFFFFFFFF)
val MMBlack = Color(0xFF151917)
val MMGrayText = Color(0xFF64706A)
val MMGrayDivider = Color(0xFFE1E8E3)
val MMBackground = Color(0xFFF6F8F7)
val MMSurfaceElevated = Color(0xFFFFFFFF)
val MMBlue = Color(0xFF3B82F6)
val MMRedExpense = Color(0xFFD94B4B)
val MMGreenIncome = Color(0xFF078455)
val MMAmberDue = Color(0xFFB66A08)
val MMMaroonOverdue = Color(0xFF9A312E)
val MMCyan = Color(0xFF0B8CA5)
val MMPurple = Color(0xFF7A63C9)
val MMBrown = Color(0xFF987057)
val MMIconShadow = Color(0x22000000)

private val LightMoneyManagerColors: Colors = lightColors(
    primary = MMGreen,
    primaryVariant = MMGreenDark,
    secondary = MMBlue,
    background = MMBackground,
    surface = MMSurfaceElevated,
    onPrimary = MMWhite,
    onSecondary = MMWhite,
    onBackground = MMBlack,
    onSurface = MMBlack,
    error = MMRedExpense
)

private val DarkMoneyManagerColors: Colors = darkColors(
    primary = Color(0xFF74D9A7),
    primaryVariant = Color(0xFF4FC58D),
    secondary = Color(0xFF9ABEFF),
    background = Color(0xFF0C120F),
    surface = Color(0xFF151C18),
    onPrimary = Color(0xFF062116),
    onSecondary = Color(0xFF081522),
    onBackground = Color(0xFFF1F6F3),
    onSurface = Color(0xFFE8EEEA),
    error = Color(0xFFFF9690)
)

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

    /** Standard corner radii. */
    val radiusCard = 20.dp
    val radiusRow = 16.dp
    val radiusField = 16.dp
    val radiusSheet = 28.dp
}

/**
 * Colour ROLES resolved against the active theme. Screens must use these accessors instead of
 * the fixed palette constants above, so dark mode gets correct surfaces, dividers and text
 * contrast rather than inverted light values.
 */
object MmColors {
    val background: Color @Composable get() = MaterialTheme.colors.background
    val surface: Color @Composable get() = MaterialTheme.colors.surface
    val surfaceMuted: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = if (MaterialTheme.colors.isLight) 0.05f else 0.08f)
    val divider: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = if (MaterialTheme.colors.isLight) 0.12f else 0.16f)
    val outline: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = if (MaterialTheme.colors.isLight) 0.14f else 0.22f)
    val textPrimary: Color @Composable get() = MaterialTheme.colors.onSurface
    val textSecondary: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = 0.64f)
    val textTertiary: Color @Composable get() = MaterialTheme.colors.onSurface.copy(alpha = 0.48f)

    /** Brand/positive accent that stays readable on both light and dark surfaces. */
    val accent: Color @Composable get() = MaterialTheme.colors.primary

    /**
     * Foreground for content sitting ON [accent] (filled buttons, brand headers).
     * Hard-coding White here is a dark-mode bug: the dark-theme accent is a light green, so
     * white-on-accent becomes unreadable.
     */
    val onAccent: Color @Composable get() = MaterialTheme.colors.onPrimary
    val income: Color @Composable get() = if (MaterialTheme.colors.isLight) MMGreenIncome else Color(0xFF5ED9A0)
    val expense: Color @Composable get() = if (MaterialTheme.colors.isLight) MMRedExpense else Color(0xFFFF9690)
    val warning: Color @Composable get() = if (MaterialTheme.colors.isLight) MMAmberDue else Color(0xFFF0B35E)
}

/** Semantic text styles used across redesigned screens (financial hierarchy in one place). */
object MmType {
    val screenTitle: TextStyle @Composable get() = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val sectionTitle: TextStyle @Composable get() = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val label: TextStyle @Composable get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val caption: TextStyle @Composable get() = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val amountHero: TextStyle @Composable get() = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val amountLarge: TextStyle @Composable get() = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val amountRow: TextStyle @Composable get() = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
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
