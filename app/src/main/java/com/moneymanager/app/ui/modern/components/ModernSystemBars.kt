package com.moneymanager.app.ui.modern.components

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Build
import android.graphics.Color
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Keeps status-bar glyphs legible across Modern routes and system-bar edge colour in sync with the page glass and nav bar, drawing wallpaper/insets behind bars where appropriate. */
@Composable
fun StatusBarGlyphToneAndSystemBarTint(lightBackground: Boolean, accentEdge: Boolean = false) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = remember(view) { view.context.findActivity() } ?: return
    DisposableEffect(activity, lightBackground, accentEdge) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setNavigationBarColor(Color.TRANSPARENT)
            activity.window.setStatusBarColor(Color.TRANSPARENT)
        }
        onDispose {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.setStatusBarColor(Color.TRANSPARENT)
                activity.window.setNavigationBarColor(Color.TRANSPARENT)
            }
        }
    }
}

/** The Material mode, so screens do not have to read the palette to know the glyph tone. */
@Composable
fun isLightMode(): Boolean = MaterialTheme.colors.isLight

private fun Context.findActivity(): Activity? {
    var cursor: Context? = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        cursor = cursor.baseContext
    }
    return null
}
