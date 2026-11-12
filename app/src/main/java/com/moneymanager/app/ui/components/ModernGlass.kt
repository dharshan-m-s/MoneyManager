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
/** User-level master switch; screen-local glass hosts cannot override this preference. */
val LocalMmGlassUserEnabled = staticCompositionLocalOf { true }
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

/**
 * Liquid Glass surface — BitChord's `liquidGlass(shape)`, the exact same material
 * (vibrancy, 8dp blur, lens refraction, 40% theme tint, highlight rim, default
 * shadow). Kept as a corner-radius wrapper because every call site in this app is a
 * rounded rectangle.
 */
@Composable
fun Modifier.mmLiquidGlass(cornerRadius: Dp = 30.dp, surfaceTint: Color? = null): Modifier =
    liquidGlass(RoundedCornerShape(cornerRadius), surfaceTint)

/** BitChord's `liquidGlassRim(shape)` for a control that keeps its own fill. */
@Composable
fun Modifier.mmGlassHighlight(cornerRadius: Dp = 20.dp): Modifier =
    liquidGlassRim(RoundedCornerShape(cornerRadius))

@Composable
fun Modifier.mmGlassRim(cornerRadius: Dp = 22.dp): Modifier =
    liquidGlassRim(RoundedCornerShape(cornerRadius))

fun isMmLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    isGlassSupported(sdkInt)
