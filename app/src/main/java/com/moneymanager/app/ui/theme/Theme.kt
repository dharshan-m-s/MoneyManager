package com.moneymanager.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── One UI 9 Brand Palette ──────────────────────────────────────────
// Static light-mode tokens (used by PageColors constants – unchanged).
val MMGreenDark     = Color(0xFF006B45)
val MMGreen         = Color(0xFF0A8F5A)
val MMGreenLight    = Color(0xFF49C98A)
val MMGreenTint     = Color(0xFFE8F7EF)   // accentAlpha field – unused in screens
val MMWhite         = Color(0xFFFFFFFF)
val MMBlue          = Color(0xFF3B82F6)
val MMBlueTint      = Color(0xFFEBF3FF)   // accentAlpha field – unused in screens
val MMRedExpense    = Color(0xFFD94B4B)
val MMRedTint       = Color(0xFFFFEEEE)   // accentAlpha field – unused in screens
val MMGreenIncome   = Color(0xFF078455)
val MMAmberDue      = Color(0xFFB66A08)
val MMAmberTint     = Color(0xFFFFF3DE)   // accentAlpha field – unused in screens
val MMMaroonOverdue = Color(0xFF9A312E)
val MMCyan          = Color(0xFF0B8CA5)
val MMPurple        = Color(0xFF7A63C9)
val MMBrown         = Color(0xFF987057)
val MMIconShadow    = Color(0x22000000)

// ── Dark Mode Semantic Tokens ──────────────────────────────────────
private val DarkBackground      = Color(0xFF111418)   // app background
private val DarkSurface         = Color(0xFF1C2024)   // cards / surfaces
private val DarkSurfaceMuted    = Color(0xFF191D21)   // subtle fill areas
private val DarkSurfaceElevated = Color(0xFF25292D)   // inputs / raised cards
private val DarkDivider         = Color(0xFF2C3034)   // dividers – very subtle
private val DarkOutline         = Color(0xFF383C40)   // borders / outlines
private val DarkGrayText        = Color(0xFF8A9498)   // secondary / helper text
private val DarkOnBackground    = Color(0xFFE8EDEF)   // primary text
private val DarkOnSurface       = Color(0xFFDEE1E4)   // card / surface primary text
private val DarkCashForward     = Color(0xFF282214)   // warm translucent wash

// ── Light Mode Semantic Tokens (static – usable outside composition) ──
private val LightOnBackground   = Color(0xFF151917)
private val LightGrayText       = Color(0xFF64706A)
private val LightGrayDivider    = Color(0xFFE1E8E3)
private val LightSurfaceMuted   = Color(0xFFEDF3EF)
private val LightSurfaceElevated = Color(0xFFFFFFFF)
private val LightOutline        = Color(0xFFD5DED9)
private val LightBackground     = Color(0xFFF6F8F7)
private val LightCashForward    = Color(0xFFFFF8E4)

// ── Theme-aware shared tokens (light ↔ dark) ──────────────────────
val MMBlack: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkOnSurface else LightOnBackground

val MMGrayText: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkGrayText else LightGrayText

val MMGrayDivider: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkDivider else LightGrayDivider

val MMSurfaceMuted: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSurfaceMuted else LightSurfaceMuted

val MMSurfaceElevated: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkSurfaceElevated else LightSurfaceElevated

val MMOutline: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkOutline else LightOutline

val MMBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkBackground else LightBackground

val MMCashForward: Color
    @Composable get() = if (isSystemInDarkTheme()) DarkCashForward else LightCashForward

// ── Per-Page Accent Colors (One UI 9 Dynamic Theming) ──────────────
// Dashboard - Deep Emerald (default brand)
val PageDashboardStart = Color(0xFF006B45)
val PageDashboardEnd = Color(0xFF0A8F5A)

// Transactions - Deep Teal
val PageTransactionsStart = Color(0xFF075B5B)
val PageTransactionsEnd = Color(0xFF148383)

// Income - Ocean Blue
val PageIncomeStart = Color(0xFF1F5E7A)
val PageIncomeEnd = Color(0xFF3C7FA2)

// Accounts - Royal Indigo
val PageAccountsStart = Color(0xFF3D3D74)
val PageAccountsEnd = Color(0xFF5C5A9A)

// Bills - Warm Amber
val PageBillsStart = Color(0xFF825B16)
val PageBillsEnd = Color(0xFFB78018)

// Cash - Vibrant Cyan
val PageCashStart = Color(0xFF0B6662)
val PageCashEnd = Color(0xFF2A9E98)

// Settings - Slate Blue
val PageSettingsStart = Color(0xFF3E4A52)
val PageSettingsEnd = Color(0xFF5D6972)

// Budget - Deep Rose
val PageBudgetStart = Color(0xFF6D3951)
val PageBudgetEnd = Color(0xFF9C5A6B)

// Spend Summary - Dark Violet
val PageSpendStart = Color(0xFF5A4D78)
val PageSpendEnd = Color(0xFF8A6EA7)

// Categories - Warm Orange
val PageCategoriesStart = Color(0xFF6E5540)
val PageCategoriesEnd = Color(0xFF9A7A52)

// Search - Slate Indigo
val PageSearchStart = Color(0xFF334155)
val PageSearchEnd = Color(0xFF596575)

// ── Semantic Gradient Helpers ───────────────────────────────────────
@Immutable
data class PageColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accent: Color,
    val accentLight: Color,
    val accentAlpha: Color
)

val DashboardColors = PageColors(PageDashboardStart, PageDashboardEnd, MMGreen, MMGreenLight, MMGreenTint)
val TransactionsColors = PageColors(PageTransactionsStart, PageTransactionsEnd, MMCyan, Color(0xFF3DCED6), Color(0xFFE0F7FA))
val IncomeColors = PageColors(PageIncomeStart, PageIncomeEnd, MMBlue, Color(0xFF6DA7E8), MMBlueTint)
val AccountsColors = PageColors(PageAccountsStart, PageAccountsEnd, MMPurple, Color(0xFFB39DDB), Color(0xFFEDE7F6))
val BillsColors = PageColors(PageBillsStart, PageBillsEnd, MMAmberDue, Color(0xFFE6B422), MMAmberTint)
val CashColors = PageColors(PageCashStart, PageCashEnd, Color(0xFF00BFA5), Color(0xFF64FFDA), Color(0xFFE0F2F1))
val SettingsColors = PageColors(PageSettingsStart, PageSettingsEnd, Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFFF1F5F9))
val BudgetColors = PageColors(PageBudgetStart, PageBudgetEnd, Color(0xFFBE3A5E), Color(0xFFF06292), Color(0xFFFCE4EC))
val SpendColors = PageColors(PageSpendStart, PageSpendEnd, Color(0xFF9C27B0), Color(0xFFCE93D8), Color(0xFFF3E5F5))
val CategoriesColors = PageColors(PageCategoriesStart, PageCategoriesEnd, Color(0xFFE67E22), Color(0xFFFFB74D), Color(0xFFFFF3E0))
val SearchColors = PageColors(PageSearchStart, PageSearchEnd, Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFFF1F5F9))

// ── Gradient Brush Builders ─────────────────────────────────────────
object Gradients {
    fun pageHeader(start: Color, end: Color) = Brush.horizontalGradient(
        colors = listOf(start, end),
        startX = 0f,
        endX = 1200f
    )

    fun pageHeaderDiagonal(start: Color, end: Color) = Brush.linearGradient(
        colors = listOf(start, end),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(1200f, 800f)
    )

    fun subtleVertical(top: Color, bottom: Color) = Brush.verticalGradient(
        colors = listOf(top, bottom)
    )

    fun shimmer(base: Color) = Brush.horizontalGradient(
        colors = listOf(
            base.copy(alpha = 0.0f),
            base.copy(alpha = 0.12f),
            base.copy(alpha = 0.0f)
        )
    )
}

// Helper extension to extract start/end from PageColors for gradient
val PageColors.headerBrush: Brush
    get() = Brush.horizontalGradient(listOf(gradientStart, gradientEnd), startX = 0f, endX = 1200f)

val PageColors.headerBrushDiagonal: Brush
    get() = Brush.linearGradient(
        listOf(gradientStart, gradientEnd),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(1200f, 800f)
    )

// ── Spring Animation Presets (One UI-inspired motion) ─────────────
object SpringPresets {
    /** Default UI transition - critically damped, no overshoot */
    fun <T> defaultResponse() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Snappy response for taps and quick interactions */
    fun <T> snappy() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Gentle bounce for momentum-driven interactions */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )

    /** Drawer / sheet spring */
    fun <T> sheet() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium
    )
}

// ── Dark Mode Colors ───────────────────────────────────────────────
private val LightMoneyManagerColors: Colors = lightColors(
    primary = MMGreen,
    primaryVariant = MMGreenDark,
    secondary = MMBlue,
    background = LightBackground,
    surface = LightSurfaceElevated,
    onPrimary = MMWhite,
    onSecondary = MMWhite,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground,
    error = MMRedExpense
)

private val DarkMoneyManagerColors: Colors = darkColors(
    primary        = Color(0xFF74D9A7),       // brand green – unchanged
    primaryVariant = Color(0xFF4FC58D),       // darker green for state layer
    secondary      = Color(0xFF7FA8CC),       // muted blue – less bright than before
    secondaryVariant = Color(0xFF4FA5BD),     // deeper muted blue (was un-set)
    background     = DarkBackground,          // calm charcoal – NOT pure black
    surface        = DarkSurface,             // card / sheet surface
    error          = Color(0xFFE86E68),       // softer red – less saturated
    onPrimary      = Color(0xFF062116),       // dark text on green actions
    onSecondary    = Color(0xFF0B1929),       // dark text on blue
    onBackground   = DarkOnBackground,        // primary text on background
    onSurface      = DarkOnSurface,           // primary text on surface
    onError        = Color(0xFF2A1215)        // dark red-tinted text on error
)

@Composable
fun MoneyManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) DarkMoneyManagerColors else LightMoneyManagerColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp)
        ),
        typography = Typography(
            h4 = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            h5 = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            h6 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            subtitle1 = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            subtitle2 = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
            body1 = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
            body2 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            button = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}
