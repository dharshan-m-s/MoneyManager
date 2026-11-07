package com.moneymanager.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.moneymanager.app.ui.components.glass.backdrop.Backdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.LayerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.SnapshotBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.layerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.rememberLayerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.drawBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.effects.blur
import com.moneymanager.app.ui.components.glass.backdrop.effects.colorControls
import com.moneymanager.app.ui.components.glass.backdrop.effects.lens
import com.moneymanager.app.ui.components.glass.backdrop.highlight.Highlight
import com.moneymanager.app.ui.components.glass.backdrop.shadow.Shadow
import com.moneymanager.app.ui.theme.MmColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Shared page recording sampled by floating glass controls. The layer is owned by ModernRoot;
 * components never capture screenshots or allocate their own full-window bitmaps.
 */
val LocalMmGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalMmGlassEnabled = staticCompositionLocalOf { true }
val LocalMmGlassBlurMultiplier = staticCompositionLocalOf { 1f }
val LocalMmGlassRefractionMultiplier = staticCompositionLocalOf { 1f }
/** Root-owned stills are disabled on pages that already record a live page layer. */
val LocalMmGlassAutoCaptureAllowed = staticCompositionLocalOf { true }

/**
 * Live page layers are tied to their visible navigation entry. Some NavHost routes retain
 * their composition while hidden; rotating the layer on resume guarantees the first draw
 * records the newly visible page instead of reusing a stale HWUI RenderNode.
 */
@Composable
fun rememberVisiblePageGlassBackdrop(): LayerBackdrop {
    val lifecycleOwner = LocalLifecycleOwner.current
    var activation by remember(lifecycleOwner) { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) activation += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return key(lifecycleOwner, activation) { rememberLayerBackdrop() }
}

/**
 * Gives a destination one live, local recording for its top bar and floating glass surfaces.
 * The recording is scoped to the visible navigation entry, so scrolling content behind the
 * chrome stays current and a different destination can never appear in the reflection.
 */
@Composable
fun MmGlassPageHost(content: @Composable () -> Unit) {
    val backdrop = key(LocalMmGlassBackdrop.current) { rememberVisiblePageGlassBackdrop() }
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        CompositionLocalProvider(
            LocalMmGlassBackdrop provides backdrop
        ) {
            content()
        }
    }
}

/** Page content and fixed glass chrome as siblings, matching the stable Activity screen pattern. */
@Composable
fun MmGlassPageOverlayHost(
    pageContent: @Composable () -> Unit,
    overlayContent: @Composable () -> Unit
) {
    val backdrop = key(LocalMmGlassBackdrop.current) { rememberVisiblePageGlassBackdrop() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            pageContent()
        }
        CompositionLocalProvider(
            LocalMmGlassBackdrop provides backdrop,
            LocalMmGlassEnabled provides true
        ) {
            overlayContent()
        }
    }
}

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
/**
 * Every destination owns a clean, opaque canvas. Liquid Glass samples only this destination's
 * content; it never borrows a previous screen's colour wash or bitmap.
 */
@Composable
fun Modifier.mmPageBackdrop(): Modifier = background(MmColors.background)

/** Opaque theme gradient for navigation chrome. It never samples or reveals a prior page. */
@Composable
fun Modifier.mmTopBarSurface(
    cornerRadius: Dp = 24.dp,
    accentStrength: Float = 1f
): Modifier {
    val light = MaterialTheme.colors.isLight
    val strength = accentStrength.coerceIn(0f, 1.25f)
    val base = MmColors.surfaceContainer
    val top = androidx.compose.ui.graphics.lerp(
        base, MmColors.accent, if (light) 0.055f * strength else 0.17f * strength
    )
    val middle = androidx.compose.ui.graphics.lerp(
        base, MmColors.background, if (light) 0.025f else 0.12f
    )
    val bottom = androidx.compose.ui.graphics.lerp(
        base, MmColors.accent, if (light) 0.02f * strength else 0.055f * strength
    )
    val shape = RoundedCornerShape(cornerRadius)
    val rim = if (light) MmColors.outline.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.09f)
    return clip(shape)
        .background(Brush.verticalGradient(listOf(top, middle, bottom)), shape)
        .border(0.8.dp, rim, shape)
}
internal const val MM_GLASS_BACKDROP_SCALE = 0.5f
internal const val MM_GLASS_BLUR_RADIUS_DP = 8.2f
internal const val MM_GLASS_REFRACTION_AMOUNT_DP = 52f

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
    intensity: Float = 1f,
    blurMultiplier: Float = 1f,
    refractionMultiplier: Float = 1f,
    edgeHighlight: Boolean = true,
    tintOpacity: Float = 1f
): Modifier {
    val shape: CornerBasedShape = RoundedCornerShape(cornerRadius)
    val enabled = LocalMmGlassEnabled.current
    val backdrop = LocalMmGlassBackdrop.current
    val hardwareAccelerated = LocalView.current.isHardwareAccelerated
    val light = MaterialTheme.colors.isLight
    val tint = if (light) Color(0xFFFDFEFF) else Color(0xFF111722)
    val edge = if (light) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.09f)
    val safeIntensity = intensity.coerceIn(0.25f, 1.25f)
    val safeTintOpacity = tintOpacity.coerceIn(0.5f, 3f)
    val userBlurMultiplier = LocalMmGlassBlurMultiplier.current.coerceIn(0f, 2.5f)
    val userRefractionMultiplier = LocalMmGlassRefractionMultiplier.current.coerceIn(0f, 2.5f)

    // Explicitly disabled means a genuinely opaque, theme-aware surface.
    if (!enabled) {
        return clip(shape)
            .background(MmColors.surface, shape)
            .border(0.7.dp, MmColors.outline.copy(alpha = 0.42f), shape)
    }

    if (
        !isMmLiquidGlassSupported() ||
        !isMmLiquidGlassSupported() ||
        backdrop == null ||
        (backdrop as? LayerBackdrop)?.captureUnavailable == true ||
        (backdrop as? SnapshotBackdrop)?.hasStill == false ||
        !hardwareAccelerated
    ) {
        // Keep unavailable glass translucent too. An opaque fallback reads like a sheet and
        // hides the same scrolling content this surface is meant to reveal.
        return clip(shape)
            .background(tint.copy(alpha = (if (light) 0.18f else 0.24f) * safeTintOpacity), shape)
            .then(if (edgeHighlight) Modifier.border(0.55.dp, edge.copy(alpha = 0.72f), shape) else Modifier)
    }

    val density = LocalDensity.current
    // Refraction must dominate the blur. The old 18dp blur softened the sampled page
    // before the lens shader ran, making the surface look like a frosted sheet.
    // All pixel-space parameters are scaled with the capture resolution.
    val safeBlurMultiplier = (blurMultiplier * userBlurMultiplier).coerceIn(0f, 2.5f)
    val blurRadius = with(density) { MM_GLASS_BLUR_RADIUS_DP.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity * safeBlurMultiplier }
    val lensHeight = with(density) { 26.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity }
    val lensAmount = with(density) {
        MM_GLASS_REFRACTION_AMOUNT_DP.dp.toPx() * MM_GLASS_BACKDROP_SCALE * safeIntensity *
            refractionMultiplier.coerceIn(0f, 1.5f) * userRefractionMultiplier
    }

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
        highlight = if (edgeHighlight) {
            { Highlight(width = 0.8.dp, blurRadius = 0.65.dp, alpha = 0.30f) }
        } else null,
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
                            Color.White.copy(alpha = 0.105f * safeTintOpacity),
                            tint.copy(alpha = 0.045f * safeTintOpacity),
                            Color(0xFF64748B).copy(alpha = 0.055f * safeTintOpacity)
                        )
                    } else {
                        listOf(
                            Color(0xFFB9C9E8).copy(alpha = 0.085f * safeTintOpacity),
                            tint.copy(alpha = 0.055f * safeTintOpacity),
                            Color.Black.copy(alpha = 0.105f * safeTintOpacity)
                        )
                    }
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        },
        backdropScale = MM_GLASS_BACKDROP_SCALE
    ).clip(shape)
        .then(if (edgeHighlight) Modifier.border(0.55.dp, edge.copy(alpha = 0.50f), shape) else Modifier)
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
    if (!LocalMmGlassEnabled.current) return this
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
