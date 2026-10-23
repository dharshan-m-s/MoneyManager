package com.moneymanager.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.moneymanager.app.ui.navigation.MoneyManagerRoot
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import com.moneymanager.app.ui.modern.navigation.ModernRoot
import com.moneymanager.app.ui.modern.theme.ModernSystemColors
import com.moneymanager.app.ui.modern.theme.ModernTheme
import com.moneymanager.app.ui.modern.theme.MmColors

/**
 * The single point where the two appearances meet.
 *
 * Each branch mounts a completely separate, self-contained UI tree:
 *  - **Default** → `ui.theme.MoneyManagerTheme` + `ui.navigation.MoneyManagerRoot`
 *  - **Modern** → `ui.modern.theme.ModernTheme` + `ui.modern.navigation.ModernRoot`
 *
 * They share the same Activity, database, repositories, ViewModels, navigation routes and
 * preference files — only presentation differs. Removing the retired interface later means
 * deleting one branch and its package; nothing in the Modern tree imports Default UI.
 */
@Composable
fun AppRoot(
    appearance: MmAppearance,
    darkTheme: Boolean
) {
    val appearanceState = rememberSaveableStateHolder()
    appearanceState.SaveableStateProvider(appearance.name) {
        when (appearance) {
            MmAppearance.DEFAULT -> MoneyManagerTheme(darkTheme = darkTheme) {
                AppSurface { MoneyManagerRoot() }
            }
            MmAppearance.MODERN -> ModernTheme(darkTheme = darkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .modernAtmosphere()
                ) {
                    ModernRoot()
                }
            }
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
        content = content
    )
}

/**
 * Window navigation-bar colour for the active look. Kept here so MainActivity has no palette
 * knowledge and the two appearances stay symmetric.
 */
fun navigationBarColor(appearance: MmAppearance, darkTheme: Boolean): Int = when (appearance) {
    MmAppearance.DEFAULT -> if (darkTheme) 0xFF0B1220.toInt() else 0xFFF6F8F7.toInt()
    MmAppearance.MODERN -> ModernSystemColors.background(isLight = !darkTheme).toArgb()
}
