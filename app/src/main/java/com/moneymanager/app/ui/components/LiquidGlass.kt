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
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import com.moneymanager.app.ui.theme.MmColors

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

/** BitChord's `glassPerformance` recipe: frost the sample instead of refracting it. */
private const val FROST_BLUR_RADIUS_DP = 16f
private const val FROST_RESOLUTION_SCALE = 0.25f

/**
 * BitChord's scroll-edge recipe ([TopFadeBlur] in the reference app): 24dp blur at a third
 * resolution, no tint and no scrim, eased out below the controls. The material itself is
 * blurred page only; the alpha ramp below simply fades that material into the page.
 */
private const val TOP_FADE_BLUR_DP = 24f
private const val TOP_FADE_RESOLUTION_SCALE = 0.33f
private const val TOP_FADE_FOOT_START = 0.6f
private const val TOP_FADE_STEPS = 8
/** A touch of black over the frost in dark themes, so the bar reads as dark glass on content. */
private const val TOP_FADE_DARK_TINT_ALPHA = 0.12f
private val TOP_FADE_STOPS: Array<Pair<Float, Color>> =
    Array(TOP_FADE_STEPS + 1) { i ->
        val t = i / TOP_FADE_STEPS.toFloat()
        (TOP_FADE_FOOT_START + (1f - TOP_FADE_FOOT_START) * t) to
            Color.Black.copy(alpha = 1f - EaseInOut.transform(t))
    }

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

/**
 * The surface tint: light glass on a light theme, black glass on a dark one. On a black page a
 * grey veil is what reads as a white wash, so the dark tint stays black and lets the blurred
 * backdrop carry the material.
 */
@Composable
internal fun glassThemeTint(): Color =
    if (MaterialTheme.colors.isLight) Color(0xFFFAFAFA) else Color(0xFF000000)

/** Dark glass stays thin; a heavier veil turns the sampled page into an opaque grey sheet. */
@Composable
internal fun glassSurfaceAlpha(): Float =
    if (MaterialTheme.colors.isLight) SURFACE_OPACITY else 0.34f

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
            color = if (useGlass) glassThemeTint().copy(alpha = glassSurfaceAlpha()) else fallbackColor,
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
    /**
     * BitChord draws a hairline rim on its glass. Top bars opt out: against a full-width
     * strip that same rim reads as a border around the bar, so callers setting this to
     * false get the identical material without the outline. Everything else keeps it.
     */
    rim: Boolean = true,
    /**
     * BitChord's `glassPerformance` branch: heavier blur at a lower sample scale and no
     * refraction. The app uses it for chrome, where dispersion reads as a rainbow band.
     */
    frost: Boolean = false,
    /**
     * Chrome that runs under the status bar needs a vertical veil: dense frost at y=0,
     * fading to near-clear at the lower edge so the bar melts into the page instead of
     * ending on a flat translucent sheet.
     */
    topFade: Boolean = false,
    /** Set for chrome outside the page recorder so it is redrawn after capture ends. */
    redrawAfterCapture: Boolean = false,
    /** Set for sibling chrome that must never disappear while a page layer is recording. */
    drawDuringCapture: Boolean = false,
): Modifier {
    val backdrop = LocalMmGlassBackdrop.current
    if (!isGlassSupported() || backdrop == null || !LocalMmGlassUserEnabled.current || !LocalMmGlassEnabled.current) {
        return background(MmColors.surface, shape)
            .then(if (rim) Modifier.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape) else Modifier)
    }
    val density = LocalDensity.current
    val resolutionScale = when {
        topFade -> TOP_FADE_RESOLUTION_SCALE
        frost -> FROST_RESOLUTION_SCALE
        else -> GLASS_RESOLUTION_SCALE
    }
    val blurDp = when {
        topFade -> TOP_FADE_BLUR_DP
        frost -> FROST_BLUR_RADIUS_DP
        else -> BLUR_RADIUS_DP
    }
    val blurPx = with(density) { blurDp.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val surfaceTintColor = surfaceTint ?: glassThemeTint().copy(alpha = glassSurfaceAlpha())
    val topFadeBrush = if (topFade) Brush.verticalGradient(*TOP_FADE_STOPS) else null
    // Dark themes get a whisper of black over the frost; a white veil is what made the bar
    // look washed out, but a slightly deeper black lets it sit on bright page content.
    val topFadeTint = if (topFade && !MaterialTheme.colors.isLight) {
        Color.Black.copy(alpha = TOP_FADE_DARK_TINT_ALPHA)
    } else {
        Color.Transparent
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // The scroll edge is blur only: BitChord's TopFadeBlur adds no saturation or
            // tint, so a black page stays black instead of picking up a white wash.
            colorControls(saturation = if (topFade) 1f else 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            // Dispersion is off everywhere: on chrome it reads as a rainbow fringe. Frost
            // mode and the scroll edge have no lens at all.
            if (!frost && !topFade && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = false,
                )
            }
        },
        highlight = if (rim) ({ Highlight.Default }) else null,
        shadow = { Shadow.Default },
        onDrawSurface = {
            if (topFadeBrush != null) {
                if (topFadeTint.alpha > 0f) {
                    drawRect(color = topFadeTint, size = size)
                }
                // BitChord's FootFade: mask the material itself, not the controls drawn on
                // top of it, so the frost is whole at y=0 and gone below the bar.
                drawRect(brush = topFadeBrush, blendMode = BlendMode.DstIn, size = size)
            } else {
                drawRect(color = surfaceTintColor, size = size)
            }
        },
        backdropScale = resolutionScale,
        redrawAfterCapture = redrawAfterCapture,
        drawDuringCapture = drawDuringCapture,
    )
}

/** Dock pill export layer, kept for the selection pill's own lens to sample. */
internal val LocalGlassExport = androidx.compose.runtime.compositionLocalOf<LayerBackdrop?> { null }
