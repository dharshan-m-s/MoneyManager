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

// One UI-inspired Money Manager palette: calm green brand, warm neutral surfaces,
// restrained semantic accents, and enough contrast for a finance-focused UI.
val MMGreenDark = Color(0xFF006B45)
val MMGreen = Color(0xFF0A8F5A)
val MMGreenLight = Color(0xFF49C98A)
val MMGreenTint = Color(0xFFE8F7EF)
val MMWhite = Color(0xFFFFFFFF)
val MMBlack = Color(0xFF151917)
val MMGrayText = Color(0xFF64706A)
val MMGrayDivider = Color(0xFFE1E8E3)
val MMBackground = Color(0xFFF6F8F7)
val MMSurfaceMuted = Color(0xFFEDF3EF)
val MMSurfaceElevated = Color(0xFFFFFFFF)
val MMOutline = Color(0xFFD5DED9)
val MMBlue = Color(0xFF3B82F6)
val MMBlueTint = Color(0xFFEBF3FF)
val MMRedExpense = Color(0xFFD94B4B)
val MMRedTint = Color(0xFFFFEEEE)
val MMGreenIncome = Color(0xFF078455)
val MMAmberDue = Color(0xFFB66A08)
val MMAmberTint = Color(0xFFFFF3DE)
val MMMaroonOverdue = Color(0xFF9A312E)
val MMCyan = Color(0xFF0B8CA5)
val MMPurple = Color(0xFF7A63C9)
val MMBrown = Color(0xFF987057)
val MMIconShadow = Color(0x22000000)
val MMCashForward = Color(0xFFFFF8E4)

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
