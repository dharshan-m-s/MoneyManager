package com.moneymanager.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
import com.moneymanager.app.ui.components.glass.backdrop.highlight.Highlight
import com.moneymanager.app.ui.components.glass.backdrop.shadow.Shadow
import com.moneymanager.app.ui.theme.MmColors

/**
 * Shared page recording sampled by floating glass controls. The layer is owned by ModernRoot;
 * components never capture screenshots or allocate their own full-window bitmaps.
 */
val LocalMmGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalMmGlassEnabled = staticCompositionLocalOf { true }
/** User-level master switch; screen-local glass hosts cannot override this preference. */
val LocalMmGlassUserEnabled = staticCompositionLocalOf { true }
/** Root-owned stills are disabled on pages that already record a live page layer. */
val LocalMmGlassAutoCaptureAllowed = staticCompositionLocalOf { true }

/**
 * BitChord paints the window background into its backdrop before the page content. An empty
 * recording gives glass nothing to sample, so the surface stays flat until a later redraw.
 * Painting the theme floor first makes every page layer opaque from its very first frame.
 */
@Composable
internal fun rememberMmGlassBackdropFloor(): ContentDrawScope.() -> Unit {
    val background = MmColors.background
    return remember(background) {
        {
            drawRect(background)
            drawContent()
        }
    }
}

/**
 * One stable page layer per destination. Rotating it on every resume recreated the layer,
 * its GraphicsLayer and every glass subtree that reads it — the Accounts transition jank.
 * The opaque floor keeps the first recorded frame valid without rebuilding anything.
 */
@Composable
fun rememberVisiblePageGlassBackdrop(): LayerBackdrop {
    val floor = rememberMmGlassBackdropFloor()
    return rememberLayerBackdrop(onDraw = floor)
}

/**
 * Gives a destination one live, local recording for its top bar and floating glass surfaces.
 * The recording is scoped to the visible navigation entry, so scrolling content behind the
 * chrome stays current and a different destination can never appear in the reflection.
 */
@Composable
fun MmGlassPageHost(content: @Composable () -> Unit) {
    val backdrop = rememberVisiblePageGlassBackdrop()
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        CompositionLocalProvider(
            LocalMmGlassBackdrop provides backdrop,
            LocalMmGlassEnabled provides true
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
    val backdrop = rememberVisiblePageGlassBackdrop()
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
    // No hairline: on a full-width toolbar the rim reads as a border around the bar.
    return clip(shape)
        .background(Brush.verticalGradient(listOf(top, middle, bottom)), shape)
}

/**
 * Liquid Glass surface — BitChord's `liquidGlass(shape)`, the exact same material
 * (vibrancy, 8dp blur, lens refraction, 40% theme tint, highlight rim, default
 * shadow). Kept as a corner-radius wrapper because every call site in this app is a
 * rounded rectangle.
 */
@Composable
fun Modifier.mmLiquidGlass(
    cornerRadius: Dp = 30.dp,
    surfaceTint: Color? = null,
    rim: Boolean = true,
    frost: Boolean = false,
    topFade: Boolean = false,
    redrawAfterCapture: Boolean = false,
    drawDuringCapture: Boolean = false,
): Modifier = liquidGlass(
    shape = RoundedCornerShape(cornerRadius),
    surfaceTint = surfaceTint,
    rim = rim,
    frost = frost,
    topFade = topFade,
    redrawAfterCapture = redrawAfterCapture,
    drawDuringCapture = drawDuringCapture
)

/**
 * Frosted navigation chrome: BitChord's TopFadeBlur material — 24dp blur at a third resolution,
 * no tint and no scrim, masked out below the controls. The top corners are square so the frost
 * reaches the very top of the display; only the lower corners round into the page.
 */
@Composable
fun Modifier.mmFrostTopBar(cornerRadius: Dp = 24.dp): Modifier = liquidGlass(
    shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = cornerRadius,
        bottomEnd = cornerRadius
    ),
    rim = false,
    frost = true,
    topFade = true,
    redrawAfterCapture = true,
    drawDuringCapture = true
)

/**
 * BitChord's iOS menu material (`IosMenu.kt`): 24dp blur at quarter resolution with 1.8x
 * saturation, no lens, no highlight and no built-in shadow. The dark slab is pure black and
 * thin enough (0.62f) that the blurred page still reads through it — an 0.80f veil turns the
 * panel into an opaque sheet on a dark page.
 */
private const val MM_MENU_BLUR_DP = 24f
private const val MM_MENU_BACKDROP_SCALE = 0.25f
private const val MM_MENU_SATURATION = 1.8f
private const val MM_MENU_DARK_ALPHA = 0.62f
private val MmMenuFillLight = Color(0xFFF6F6F7)
private val MmMenuFillDark = Color(0xFF000000)

@Composable
fun Modifier.mmMenuFrost(shape: CornerBasedShape): Modifier {
    val light = MaterialTheme.colors.isLight
    val fill = if (light) MmMenuFillLight else MmMenuFillDark
    val slabAlpha = if (light) 0.72f else MM_MENU_DARK_ALPHA
    val backdrop = LocalMmGlassBackdrop.current
    val captureUnavailable = (backdrop as? LayerBackdrop)?.captureUnavailable == true
    val canFrost = LocalMmGlassUserEnabled.current &&
        LocalMmGlassEnabled.current &&
        isMmLiquidGlassSupported() &&
        backdrop != null &&
        !captureUnavailable
    if (!canFrost) {
        // A renderer that refused the capture still gets the translucent slab. Fully opaque
        // is reserved for the user switching Liquid Glass off or an unsupported API level.
        val alpha = if (captureUnavailable) slabAlpha else 1f
        return clip(shape).background(fill.copy(alpha = alpha), shape)
    }

    val density = LocalDensity.current
    val blurPx = with(density) { MM_MENU_BLUR_DP.dp.toPx() } * MM_MENU_BACKDROP_SCALE
    return clip(shape).drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = MM_MENU_SATURATION)
            blur(blurPx)
        },
        highlight = null,
        shadow = null,
        onDrawSurface = {
            drawRect(color = fill.copy(alpha = slabAlpha), size = size)
        },
        backdropScale = MM_MENU_BACKDROP_SCALE,
        redrawAfterCapture = true,
        drawDuringCapture = true
    )
}

/** BitChord's `liquidGlassRim(shape)` for a control that keeps its own fill. */
@Composable
fun Modifier.mmGlassHighlight(cornerRadius: Dp = 20.dp): Modifier =
    liquidGlassRim(RoundedCornerShape(cornerRadius))

@Composable
fun Modifier.mmGlassRim(cornerRadius: Dp = 22.dp): Modifier =
    liquidGlassRim(RoundedCornerShape(cornerRadius))

fun isMmLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    isGlassSupported(sdkInt)
