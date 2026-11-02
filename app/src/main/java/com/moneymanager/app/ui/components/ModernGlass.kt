package com.moneymanager.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.MaterialTheme
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.LayerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.drawBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.effects.blur
import com.moneymanager.app.ui.components.glass.backdrop.effects.colorControls
import com.moneymanager.app.ui.components.glass.backdrop.effects.lens
import com.moneymanager.app.ui.components.glass.backdrop.highlight.Highlight
import com.moneymanager.app.ui.components.glass.backdrop.shadow.Shadow

/**
 * Shared page recording sampled by floating glass controls. The layer is owned by ModernRoot;
 * components never capture screenshots or allocate their own full-window bitmaps.
 */
val LocalMmGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalMmGlassEnabled = staticCompositionLocalOf { true }

private const val BACKDROP_SCALE = 0.33f

fun isMmLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.S

/**
 * Full Liquid Glass material: samples the real app content, applies downsampled RenderEffect
 * blur and (on Android 13+) rounded-lens refraction, then layers theme tint, edge highlight
 * and a restrained shadow. API 30 and unavailable-layer fallbacks remain opaque/readable.
 */
@Composable
fun Modifier.mmLiquidGlass(
    cornerRadius: Dp = 30.dp,
    intensity: Float = 1f
): Modifier {
    val shape: CornerBasedShape = RoundedCornerShape(cornerRadius)
    val enabled = LocalMmGlassEnabled.current
    val backdrop = LocalMmGlassBackdrop.current
    val light = MaterialTheme.colors.isLight
    val tint = if (light) Color(0xFFFDFEFF) else Color(0xFF111722)
    val edge = if (light) Color.White.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.18f)
    val safeIntensity = intensity.coerceIn(0.25f, 1.25f)

    if (!enabled || !isMmLiquidGlassSupported() || backdrop == null) {
        return clip(shape)
            .background(if (light) Color(0xFFF8FAFF) else Color(0xFF171E29), shape)
            .border(0.65.dp, edge, shape)
    }

    val density = LocalDensity.current
    val blurRadius = with(density) { 12.dp.toPx() * BACKDROP_SCALE * safeIntensity }
    val lensHeight = with(density) { 30.dp.toPx() * BACKDROP_SCALE * safeIntensity }
    val lensAmount = with(density) { 22.dp.toPx() * BACKDROP_SCALE * safeIntensity }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.0f + (0.5f * safeIntensity))
            blur(blurRadius)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeight,
                    refractionAmount = lensAmount,
                    depthEffect = true,
                    chromaticAberration = true
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = {
            Shadow(
                radius = 22.dp,
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 7.dp),
                color = Color.Black.copy(alpha = if (light) 0.13f else 0.30f)
            )
        },
        onDrawSurface = {
            drawRect(tint.copy(alpha = if (light) 0.22f else 0.30f))
        },
        backdropScale = BACKDROP_SCALE
    ).clip(shape)
        .border(0.8.dp, edge, shape)
}

/**
 * Lightweight specular treatment for selected pills, small chips and compact actions. It does
 * not record or blur the page; use mmLiquidGlass only for a few prominent floating surfaces.
 */
@Composable
fun Modifier.mmGlassHighlight(
    cornerRadius: Dp = 20.dp,
    intensity: Float = 1f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val light = MaterialTheme.colors.isLight
    val alpha = (0.16f * intensity.coerceIn(0.25f, 1.25f)).coerceIn(0.04f, 0.22f)
    val rim = if (light) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.25f)
    return clip(shape).drawWithCache {
        val radius = CornerRadius(cornerRadius.toPx())
        val sheen = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.32f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.9f, size.height)
        )
        onDrawWithContent {
            drawContent()
            drawRoundRect(brush = sheen, cornerRadius = radius)
            drawRoundRect(
                color = rim,
                cornerRadius = radius,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawRoundRect(
                color = if (light) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(
                    (size.width - 2.dp.toPx()).coerceAtLeast(0f),
                    (size.height - 2.dp.toPx()).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius((cornerRadius - 1.dp).coerceAtLeast(0.dp).toPx()),
                style = Stroke(width = 0.45.dp.toPx())
            )
        }
    }
}

/** Small stable-surface rim; unlike full glass this never samples the page behind it. */
@Composable
fun Modifier.mmGlassRim(
    cornerRadius: Dp = 20.dp,
    alpha: Float = 0.18f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val color = if (MaterialTheme.colors.isLight) Color.White else Color.White.copy(alpha = alpha)
    return clip(shape).border(0.75.dp, color, shape)
}
