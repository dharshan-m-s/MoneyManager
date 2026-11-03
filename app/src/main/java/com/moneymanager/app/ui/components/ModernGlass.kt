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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.moneymanager.app.ui.theme.MmColors

/**
 * Shared page recording sampled by floating glass controls. The layer is owned by ModernRoot;
 * components never capture screenshots or allocate their own full-window bitmaps.
 */
val LocalMmGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalMmGlassEnabled = staticCompositionLocalOf { true }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMmSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMmAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mmSharedElement(key: String): Modifier {
    val sharedScope = LocalMmSharedTransitionScope.current ?: return this
    val visibilityScope = LocalMmAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@mmSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibilityScope
        )
    }
}

/** A quiet ambient page wash gives transparent glass a real, stable visual backdrop. */
@Composable
fun Modifier.mmPageBackdrop(): Modifier {
    val light = MaterialTheme.colors.isLight
    val background = MmColors.background
    val accent = MmColors.accent
    val income = MmColors.income
    return drawBehind {
        drawRect(background)
        val firstAlpha = if (light) 0.105f else 0.17f
        val secondAlpha = if (light) 0.045f else 0.085f
        val firstCenter = Offset(size.width * 0.08f, size.height * 0.08f)
        val secondCenter = Offset(size.width * 0.96f, size.height * 0.78f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = firstAlpha), Color.Transparent),
                center = firstCenter,
                radius = size.maxDimension * 0.78f
            ),
            radius = size.maxDimension * 0.78f,
            center = firstCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(income.copy(alpha = secondAlpha), Color.Transparent),
                center = secondCenter,
                radius = size.maxDimension * 0.62f
            ),
            radius = size.maxDimension * 0.62f,
            center = secondCenter
        )
    }
}

internal const val MM_GLASS_BACKDROP_SCALE = 0.5f
internal const val MM_GLASS_BLUR_RADIUS_DP = 7f
internal const val MM_GLASS_REFRACTION_AMOUNT_DP = 48f

internal fun mmGlassBackdropScale(): Float = MM_GLASS_BACKDROP_SCALE
internal fun mmGlassBlurRadiusDp(): Float = MM_GLASS_BLUR_RADIUS_DP
internal fun mmGlassRefractionAmountDp(): Float = MM_GLASS_REFRACTION_AMOUNT_DP

fun isMmLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.S

/**
 * Full Liquid Glass material: samples the real app content, applies downsampled RenderEffect
 * blur and (on Android 13+) rounded-lens refraction, then layers theme tint, edge highlight
 * and a restrained shadow. Unsupported devices receive a low-opacity material fallback.
 */
@Composable
fun Modifier.mmLiquidGlass(
    cornerRadius: Dp = 30.dp,
    intensity: Float = 1f
): Modifier {
    val shape: CornerBasedShape = RoundedCornerShape(cornerRadius)
    val enabled = LocalMmGlassEnabled.current
    val backdrop = LocalMmGlassBackdrop.current
    val hardwareAccelerated = LocalView.current.isHardwareAccelerated
    val light = MaterialTheme.colors.isLight
    val tint = if (light) Color(0xFFFDFEFF) else Color(0xFF111722)
    val edge = if (light) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.09f)
    val safeIntensity = intensity.coerceIn(0.25f, 1.25f)

    if (
        !enabled ||
        !isMmLiquidGlassSupported() ||
        backdrop == null ||
        backdrop.captureUnavailable ||
        !hardwareAccelerated
    ) {
        // Keep the dock readable without falling back to a solid, opaque sheet.
        return clip(shape)
            .background(tint.copy(alpha = if (light) 0.10f else 0.045f), shape)
            .border(0.55.dp, edge.copy(alpha = 0.72f), shape)
    }

    val density = LocalDensity.current
    // Refraction must dominate the blur. The old 18dp blur softened the sampled page
    // before the lens shader ran, making the surface look like a frosted sheet.
    // All pixel-space parameters are scaled with the capture resolution.
    val blurRadius = with(density) { MM_GLASS_BLUR_RADIUS_DP.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity }
    val lensHeight = with(density) { 26.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity }
    val lensAmount = with(density) { MM_GLASS_REFRACTION_AMOUNT_DP.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity }

    // Clip before the backdrop draw as well as after it. Without the leading clip the
    // sampled RenderNode can bleed into the dock's rectangular parent and look like a sheet.
    return clip(shape).drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.0f + (0.12f * safeIntensity))
            // A small base blur gives the glass depth while preserving page detail.
            blur(blurRadius)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeight,
                    refractionAmount = lensAmount,
                    depthEffect = true,
                    // A single-sample lens is sharper and cheaper than seven chromatic
                    // samples; the edge highlight provides the spectral glass cue.
                    chromaticAberration = false
                )
            }
        },
        highlight = { Highlight(width = 0.8.dp, blurRadius = 0.65.dp, alpha = 0.44f) },
        shadow = {
            Shadow(
                radius = 27.dp,
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 9.dp),
                color = Color.Black.copy(alpha = if (light) 0.12f else 0.19f)
            )
        },
        onDrawSurface = {
            // Draw the tint in the lens geometry itself. A full rectangular wash here can
            // leak outside the pill on renderers that do not preserve the backdrop layer clip.
            // A restrained vertical optical tint adds body to the lens without hiding
            // the sampled page. The upper specular band and darker lower falloff create
            // depth; both are drawn inside the rounded glass geometry.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (light) {
                        listOf(
                            Color.White.copy(alpha = 0.105f),
                            tint.copy(alpha = 0.045f),
                            Color(0xFF64748B).copy(alpha = 0.055f)
                        )
                    } else {
                        listOf(
                            Color(0xFFB9C9E8).copy(alpha = 0.085f),
                            tint.copy(alpha = 0.055f),
                            Color.Black.copy(alpha = 0.105f)
                        )
                    }
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        },
        backdropScale = MM_GLASS_BACKDROP_SCALE
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
    val alpha = (0.075f * intensity.coerceIn(0.25f, 1.25f)).coerceIn(0.018f, 0.10f)
    val rim = if (light) Color.White.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.11f)
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
                color = if (light) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.025f),
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
