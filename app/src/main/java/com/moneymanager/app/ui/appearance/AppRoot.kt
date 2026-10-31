package com.moneymanager.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.moneymanager.app.ui.modern.navigation.ModernRoot
import com.moneymanager.app.ui.theme.ModernSystemColors
import com.moneymanager.app.ui.theme.ModernTheme
import com.moneymanager.app.ui.theme.MmColors

/**
 * Modern UI is now the only UI of Money Manager.
 */
@Composable
fun AppRoot(
    appearance: MmAppearance = MmAppearance.MODERN,
    darkTheme: Boolean
) {
    ModernTheme(darkTheme = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .modernAtmosphere()
        ) {
            ModernRoot()
        }
    }
}

@Composable
private fun Modifier.modernAtmosphere(): Modifier {
    val background = MmColors.backdrop
    val ambient = MmColors.accentGlow
    return background(background).drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambient.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.90f, size.height * 0.04f),
                radius = size.width * 0.8f
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.90f, size.height * 0.04f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambient.copy(alpha = 0.055f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.68f),
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.05f, size.height * 0.68f)
        )
    }
}

@Composable
private fun AppSurface(content: @Composable () -> Unit) {
    content()
}

/**
 * Window navigation-bar colour for the active look.
 */
fun navigationBarColor(appearance: MmAppearance = MmAppearance.MODERN, darkTheme: Boolean): Int {
    return ModernSystemColors.background(isLight = !darkTheme).toArgb()
}
