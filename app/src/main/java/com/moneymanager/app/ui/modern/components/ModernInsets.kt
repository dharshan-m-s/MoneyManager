package com.moneymanager.app.ui.modern.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bottom clearance for scrollable content while the floating dock overlays the screen. */
val LocalDockContentPadding = compositionLocalOf { 0.dp }

/** Consumes the safe top edge (status bar plus display cutout) once, inside the screen chrome. */
@Composable
fun Modifier.modernTopInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
