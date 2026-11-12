/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.moneymanager.app.ui.components.glass.backdrop] — see that package for the
 * upstream attribution.
 *
 * This file is a straight port of BitChord's ui/components/LiquidGlass.kt: the same
 * constants, the same effect chain, the same tint, rim and shadow. Only the app wiring
 * differs — it reads this app's Liquid Glass toggle and this app's page backdrop, and
 * it drops BitChord's two extra performance modes because this app exposes a single
 * on/off switch.
 */
package com.moneymanager.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.LayerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.drawBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.effects.blur
import com.moneymanager.app.ui.components.glass.backdrop.effects.colorControls
import com.moneymanager.app.ui.components.glass.backdrop.effects.lens
import com.moneymanager.app.ui.components.glass.backdrop.highlight.Highlight
import com.moneymanager.app.ui.components.glass.backdrop.highlight.HighlightElement
import com.moneymanager.app.ui.components.glass.backdrop.internal.ShapeProvider
import com.moneymanager.app.ui.components.glass.backdrop.shadow.Shadow

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Apple-matched defaults (Echo's GlassEffectConfig()), fixed rather than user sliders. */
private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.4f

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * A third, which is what Echo's `glassResolutionScale` comes to at this blur radius.
 * A third is nine times fewer pixels than full resolution through the colour matrix,
 * the blur and the lens shader, and the blur is what hides the upscale.
 */
private const val GLASS_RESOLUTION_SCALE = 0.33f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim wherever the
 * glass itself is not drawn.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/** Reference material geometry, exposed for the compatibility test. */
internal fun mmGlassBackdropScale(): Float = GLASS_RESOLUTION_SCALE
internal fun mmGlassBlurRadiusDp(): Float = BLUR_RADIUS_DP
internal fun mmGlassRefractionAmountDp(): Float = LENS_AMOUNT * LENS_MAX_DP

/** The surface tint: light glass on a light theme, dark glass on a dark one. */
@Composable
internal fun glassThemeTint(): Color =
    if (MaterialTheme.colors.isLight) Color(0xFFFAFAFA) else Color(0xFF121212)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so the usual
 * onSurface greys have nothing dependable to sit against. Pure black or white off the
 * theme's luminance is the only tint that holds against arbitrary artwork.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colors.isLight) Color.Black else Color.White

/**
 * Selected-tab indicator colour for a glass surface: white in both themes. In light
 * theme that is the inverse of [glassContentColor], a bright lift behind a dark glyph.
 * In dark theme it is a faint white lift over the bar's dark glass rather than a black
 * scrim pressed into it.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colors.isLight) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = DARK_INDICATOR_OPACITY)
    }

/** The selected tab's pill in dark theme, a step whiter than the bar. */
private const val DARK_INDICATOR_OPACITY = 0.14f

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * This keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens refraction
 * or shadow rendering. When Liquid Glass is disabled or unsupported, [fallbackColor]
 * preserves the control's existing filled appearance.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val useGlass = LocalMmGlassUserEnabled.current && LocalMmGlassEnabled.current && isGlassSupported()
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassThemeTint().copy(alpha = SURFACE_OPACITY) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { Highlight.Default },
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Just the rim of liquid glass — the directional [Highlight] that catches the light
 * along a glass edge — for a control that keeps its own fill.
 *
 * Over a flat, stable page the blur and lens of [liquidGlass] would sample a single
 * colour and change nothing a reader could see; the rim is the part that reads as
 * glass. No backdrop capture, so it costs a stroke.
 */
@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    if (!LocalMmGlassUserEnabled.current || !LocalMmGlassEnabled.current || !isGlassSupported()) return this
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return then(
        HighlightElement(
            shapeProvider = shapeProvider,
            highlight = { Highlight.Default },
        ),
    )
}

/**
 * Renders this composable as a liquid glass surface sampling the page backdrop:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint. Returns a
 * solid theme surface on devices without RenderEffect support.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect throws
 * for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    /** Drawn over the glass in place of the theme's default tint, when set. */
    surfaceTint: Color? = null,
): Modifier {
    val backdrop = LocalMmGlassBackdrop.current
    if (!isGlassSupported() || backdrop == null || !LocalMmGlassUserEnabled.current || !LocalMmGlassEnabled.current) {
        return background(MaterialTheme.colors.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val density = LocalDensity.current
    val resolutionScale = GLASS_RESOLUTION_SCALE
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val surfaceTintColor = surfaceTint ?: glassThemeTint().copy(alpha = SURFACE_OPACITY)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        backdropScale = resolutionScale,
    )
}

/** Dock pill export layer, kept for the selection pill's own lens to sample. */
internal val LocalGlassExport = androidx.compose.runtime.compositionLocalOf<LayerBackdrop?> { null }
