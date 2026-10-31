package com.moneymanager.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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

// One UI 9 inspired - clean, restrained, modern

// Light mode
internal val LightBackground = Color(0xFFF7F8FA)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceContainer = Color(0xFFF0F2F6)
internal val LightSurfaceContainerHigh = Color(0xFFE8EBF0)
internal val LightOutline = Color(0xFFD8DCE3)
internal val LightOutlineVariant = Color(0xFFE3E6EC)
internal val LightTextPrimary = Color(0xFF1C1C1E)
internal val LightTextSecondary = Color(0xFF3C3C43)
internal val LightTextTertiary = Color(0xFF6C6C70)

// Dark mode  
internal val DarkBackground = Color(0xFF000000)
internal val DarkSurface = Color(0xFF151517)
internal val DarkSurfaceContainer = Color(0xFF202022)
internal val DarkSurfaceContainerHigh = Color(0xFF242426)
internal val DarkOutline = Color(0xFF3A3A3C)
internal val DarkOutlineVariant = Color(0xFF2C2C2E)
internal val DarkTextPrimary = Color(0xFFF2F2F7)
internal val DarkTextSecondary = Color(0xFFAEAEB2)
internal val DarkTextTertiary = Color(0xFF8E8E93)

// Accents
internal val PrimaryLight = Color(0xFF2563EB)
internal val PrimaryDark = Color(0xFF5B9BFF)
internal val OnPrimary = Color(0xFFFFFFFF)

// Semantic
internal val PrimaryFillDark = Color(0xFF2F6BDB)
internal val IncomeLight = Color(0xFF047857)
internal val IncomeDark = Color(0xFF34D399)
internal val ExpenseLight = Color(0xFFDC2626)
internal val ExpenseDark = Color(0xFFF87171)
// Transfer/refund neutral-blue: distinct from both income green and expense red, without
// borrowing the brand accent used for actions and links.
internal val TransferLight = Color(0xFF4B5F7A)
internal val TransferDark = Color(0xFFA9BED8)
internal val WarningLight = Color(0xFFB45309)
internal val WarningDark = Color(0xFFFBBF24)

// Category palette
internal val CategoryLight = listOf(
    Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF059669), Color(0xFFEA580C),
    Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFF65A30D), Color(0xFFD97706)
)
internal val CategoryDark = listOf(
    Color(0xFF5B9BFF), Color(0xFFA78BFA), Color(0xFF34D399), Color(0xFFFB923C),
    Color(0xFFF472B6), Color(0xFF22D3EE), Color(0xFFA3E635), Color(0xFFFBBF24)
)

val MMGreenDark: Color = Color(0xFF1C1C1E)
val MMWhite: Color = Color(0xFFFFFFFF)

object MmColors {
    const val isModern: Boolean = true

    private val light: Boolean
        @Composable get() = MaterialTheme.colors.isLight

    val background: Color @Composable get() = if (light) LightBackground else DarkBackground
    val backdrop: Color @Composable get() = background
    val surface: Color @Composable get() = if (light) LightSurface else DarkSurface
    val surfaceContainer: Color @Composable get() = if (light) LightSurfaceContainer else DarkSurfaceContainer
    val surfaceContainerHigh: Color @Composable get() = if (light) LightSurfaceContainerHigh else DarkSurfaceContainerHigh
    val surfaceMuted: Color @Composable get() = surfaceContainer
    val surfaceElevated: Color @Composable get() = surfaceContainerHigh

    val divider: Color @Composable get() = if (light) LightOutlineVariant else DarkOutlineVariant
    val outline: Color @Composable get() = if (light) LightOutline else DarkOutline
    val hairline: Color @Composable get() = outline
    val cardBorder: Color @Composable get() = outline

    val textPrimary: Color @Composable get() = if (light) LightTextPrimary else DarkTextPrimary
    val textSecondary: Color @Composable get() = if (light) LightTextSecondary else DarkTextSecondary
    val textTertiary: Color @Composable get() = if (light) LightTextTertiary else DarkTextTertiary

    val accent: Color @Composable get() = if (light) PrimaryLight else PrimaryDark
    val accentFill: Color @Composable get() = if (light) PrimaryLight else PrimaryFillDark
    val accentGlow: Color @Composable get() = accent
    val onAccent: Color get() = OnPrimary

    val income: Color @Composable get() = if (light) IncomeLight else IncomeDark
    val expense: Color @Composable get() = if (light) ExpenseLight else ExpenseDark
    val warning: Color @Composable get() = if (light) WarningLight else WarningDark
    /** Neutral blue for transfers and refunds; never used for category names or icons. */
    val transfer: Color @Composable get() = if (light) TransferLight else TransferDark
    /** Explicit "Not a spend" rows keep the ordinary foreground in both appearances. */
    val notSpend: Color @Composable get() = textPrimary

    val onBrand: Color get() = MMWhite

    val brandGradient: List<Color> @Composable get() = listOf(accent, accent)
    val glassChromeGradient: List<Color> @Composable get() = listOf(accent, accent)
    val heroGradient: List<Color> @Composable get() = listOf(accent, accent)
    val glassHeroGradient: List<Color> @Composable get() = heroGradient
    val cardHeaderGradient: List<Color> @Composable get() = listOf(accent, accent)

    val categoryPalette: List<Color> @Composable get() = if (light) CategoryLight else CategoryDark

    // Keep for compatibility
    val glassSurface: Color @Composable get() = surface
    val glassRaised: Color @Composable get() = surfaceContainerHigh
    val glassMuted: Color @Composable get() = surfaceContainer
    val glassOutline: Color @Composable get() = outline
    val glassHighlight: Color @Composable get() = Color.Transparent
    val glassEdge: Color @Composable get() = outline
    val glassRim: Color @Composable get() = outline
    val glassSurfaceFill: Color @Composable get() = surface
    val glassRaisedFill: Color @Composable get() = surfaceContainerHigh
    val glassEdgeColor: Color @Composable get() = outline
    val glassRimColor: Color @Composable get() = outline
}

object MmSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = 20.dp
    val card = 16.dp
    val touchTarget = 48.dp
    val radiusCard = 16.dp
    val radiusRow = 12.dp
    val radiusField = 12.dp
    val radiusSheet = 28.dp
}

object MmShape {
    val card: RoundedCornerShape @Composable get() = RoundedCornerShape(MmSpacing.radiusCard)
    val row: RoundedCornerShape @Composable get() = RoundedCornerShape(MmSpacing.radiusRow)
    val field: RoundedCornerShape @Composable get() = RoundedCornerShape(MmSpacing.radiusField)
    val segment: RoundedCornerShape @Composable get() = RoundedCornerShape(999.dp)
    val sheet: RoundedCornerShape @Composable get() = RoundedCornerShape(topStart = MmSpacing.radiusSheet, topEnd = MmSpacing.radiusSheet)
}

object MmType {
    private const val TABULAR_NUMS = "tnum"

    val screenTitle: TextStyle @Composable get() = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp
    )
    val sectionTitle: TextStyle @Composable get() = TextStyle(
        fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp
    )
    val body: TextStyle @Composable get() = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val label: TextStyle @Composable get() = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val caption: TextStyle @Composable get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val amountHero: TextStyle @Composable get() = TextStyle(
        fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-1.2).sp, fontFeatureSettings = TABULAR_NUMS
    )
    val amountLarge: TextStyle @Composable get() = TextStyle(
        fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR_NUMS
    )
    val amountRow: TextStyle @Composable get() = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_NUMS
    )
    val amountTile: TextStyle @Composable get() = TextStyle(
        fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_NUMS
    )
}

object ModernSystemColors {
    fun background(isLight: Boolean): Color = if (isLight) LightBackground else DarkBackground
}

@Composable
fun ModernTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors: Colors = if (darkTheme) {
        darkColors(
            primary = PrimaryDark,
            primaryVariant = PrimaryDark,
            secondary = PrimaryDark,
            background = DarkBackground,
            surface = DarkSurface,
            error = ExpenseDark,
            onPrimary = OnPrimary,
            onSecondary = OnPrimary,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary,
            onError = OnPrimary
        )
    } else {
        lightColors(
            primary = PrimaryLight,
            primaryVariant = PrimaryLight,
            secondary = PrimaryLight,
            background = LightBackground,
            surface = LightSurface,
            error = ExpenseLight,
            onPrimary = OnPrimary,
            onSecondary = OnPrimary,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onError = OnPrimary
        )
    }
    MaterialTheme(
        colors = colors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        typography = Typography(
            h4 = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            h5 = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            h6 = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            subtitle1 = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            subtitle2 = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
            body1 = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            body2 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            button = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

// Palette colors (migrated from modern theme)
val MmPaletteLightIncome = Color(0xFF047857)
val MmPaletteDarkIncome = Color(0xFF34D399)
val MmPaletteLightExpense = Color(0xFFDC2626)
val MmPaletteDarkExpense = Color(0xFFF87171)
val MmPaletteLightOnAccent = Color(0xFFFFFFFF)
val MmPaletteDarkOnAccent = Color(0xFFFFFFFF)
val MmPaletteLightWarning = Color(0xFFEA580C)
val MmPaletteDarkWarning = Color(0xFFF59E0B)
val MmPaletteLightAmberDue = Color(0xFFF59E0B)
val MmPaletteDarkAmberDue = Color(0xFFFBBF24)
val MmPaletteLightMaroonOverdue = Color(0xFFDC2626)
val MmPaletteDarkMaroonOverdue = Color(0xFFF87171)
val MmPaletteLightCyan = Color(0xFF06B6D4)
val MmPaletteDarkCyan = Color(0xFF22D3EE)
val MmPaletteLightPurple = Color(0xFF7C3AED)
val MmPaletteDarkPurple = Color(0xFFA78BFA)
val MmPaletteLightTextPrimary = Color(0xFF1C1C1E)
val MmPaletteDarkTextPrimary = Color(0xFFFFFFFF)
val MmPaletteLightTextSecondary = Color(0xFF3C3C43)
val MmPaletteDarkTextSecondary = Color(0xFFEBEBF5)
val MmPaletteLightTextTertiary = Color(0xFF6C6C70)
val MmPaletteDarkTextTertiary = Color(0xFF8E8E93)
val MmPaletteLightSurfaceMuted = Color(0xFFF0F2F6)
val MmPaletteDarkSurfaceMuted = Color(0xFF1C1C1E)
val MmPaletteLightOutline = Color(0xFFD8DCE3)
val MmPaletteDarkOutline = Color(0xFF2C2C2E)
val MmPaletteLightDivider = Color(0xFFE3E6EC)
val MmPaletteDarkDivider = Color(0xFF3A3A3C)
val MmPaletteLightBackground = Color(0xFFF7F8FA)
val MmPaletteDarkBackground = Color(0xFF000000)
val MmPaletteLightSurface = Color(0xFFFFFFFF)
val MmPaletteDarkSurface = Color(0xFF151517)
val MmPaletteLightBrown = Color(0xFF92400E)
val MmPaletteDarkBrown = Color(0xFFF59E0B)
