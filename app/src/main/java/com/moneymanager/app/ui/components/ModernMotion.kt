package com.moneymanager.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import kotlin.math.roundToLong
import kotlinx.coroutines.currentCoroutineContext

/**
 * Shared motion tokens. Keep touch feedback and layout springs consistent across components.
 * Spring-based transitions remain interruptible when a new pointer interaction arrives.
 */
internal object MmMotion {
    const val touchScale = 0.988f
    const val microDamping = 0.92f
    const val microStiffness = 760f
    const val standardDamping = 0.90f
    const val standardStiffness = 420f
    const val spatialDamping = 0.92f
    const val spatialStiffness = 300f
    const val elasticDamping = 0.84f
    const val elasticStiffness = 360f

    fun <T> microSpring(): SpringSpec<T> = spring(
        dampingRatio = microDamping,
        stiffness = microStiffness
    )

    fun <T> standardSpring(): SpringSpec<T> = spring(
        dampingRatio = standardDamping,
        stiffness = standardStiffness
    )

    fun <T> spatialSpring(): SpringSpec<T> = spring(
        dampingRatio = spatialDamping,
        stiffness = spatialStiffness
    )

    fun <T> elasticSpring(): SpringSpec<T> = spring(
        dampingRatio = elasticDamping,
        stiffness = elasticStiffness
    )

    fun <T> reducedMotion(): SnapSpec<T> = snap()
}

/**
 * Shared motion language for Money Manager.
 *
 * Prefer interruptible, velocity-aware springs for touch-driven movement. Keep financial
 * information legible: numeric changes settle smoothly, while decorative movement stays subtle.
 * Compose's platform overscroll remains enabled so Android 12+ supplies its native stretch edge.
 */

/** Press feedback: a subtle, interruptible compression that preserves the surface shape. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MmMotion.touchScale,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressed && enabled
    val compression = pressedScale.coerceIn(0.90f, 1f)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) compression else 1f,
        animationSpec = MmMotion.microSpring(),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
    }
}

/** A clickable surface with elastic press feedback and optional platform haptics. */
@Composable
fun Modifier.pressableClick(
    enabled: Boolean = true,
    haptic: Boolean = false,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    return this
        .pressScale(interactionSource, enabled = enabled)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            onClick = {
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        )
}

/** A soft entrance: the element fades in and rises a little, staggered by [index]. */
@Composable
fun Modifier.revealOnAppear(
    index: Int = 0,
    delayPerItemMillis: Int = 12,
    maxStaggerSteps: Int = 4
): Modifier {
    // Lazy lists save this flag with each stable item key. A row that scrolls out and
    // back into composition therefore does not replay its entrance animation.
    var hasAppeared by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasAppeared) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (hasAppeared) {
            progress.snapTo(1f)
        } else if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
            hasAppeared = true
            progress.animateTo(1f, animationSpec = MmMotion.reducedMotion())
        } else {
            hasAppeared = true
            val delay = (index.coerceAtMost(maxStaggerSteps) * delayPerItemMillis).toLong()
            kotlinx.coroutines.delay(delay)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** A money value that animates to its new number, with stable tabular widths. */
@Composable
fun MmAnimatedAmount(
    value: Long,
    formatter: (Long) -> String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MmType.amountLarge,
    color: Color = MmColors.textPrimary
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(dampingRatio = 0.94f, stiffness = 190f),
        label = "amount"
    )
    Text(
        text = formatter(animated.roundToLong()),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** A shimmering placeholder block for loading states. */
@Composable
fun MmSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShift"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MmColors.surfaceMuted)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { translationX = (shift * 600f) - 300f }
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, MmColors.surface.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
        )
    }
}

/**
 * The primary action: a filled indigo capsule with a restrained glow and a specular sheen. The
 * glow is drawn behind the button, so it never occupies its own band or shifts the layout.
 */
@Composable
fun MmPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MmColors.accentFill,
    contentColor: Color = MmColors.onAccent,
    loading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    val glow = MmColors.accentFill
    Box(
        modifier = modifier
            .pressScale(interactionSource, enabled = enabled)
            .clip(MmShape.row)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(containerColor, glow))
                } else {
                    Brush.horizontalGradient(
                        listOf(containerColor.copy(alpha = 0.32f), containerColor.copy(alpha = 0.32f))
                    )
                }
            )
            .then(if (enabled) Modifier.mmGlassHighlight(cornerRadius = 18.dp) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = MmSpacing.xl, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor)
            }
            Text(
                text = text,
                style = MmType.label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (icon != null || loading) 8.dp else 0.dp)
            )
        }
    }
}

/** The secondary action: a hairline outline that never competes with the primary button. */
@Composable
fun MmSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MmColors.accent
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Box(
        modifier = modifier
            .pressScale(interactionSource, enabled = enabled)
            .clip(MmShape.row)
            .background(MmColors.surface)
            .border(1.dp, MmColors.hairline, MmShape.row)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            )
            .padding(horizontal = MmSpacing.xl, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.7f)
                )
            }
            Text(
                text = text,
                style = MmType.label,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp)
            )
        }
    }
}

/** A soft glass surface used behind hero numbers. */
@Composable
fun MmGlassSurface(
    modifier: Modifier = Modifier,
    tint: Color = MmColors.surfaceMuted,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(MmShape.row)
            .background(tint)
            .border(1.dp, MmColors.hairline, MmShape.row)
            .padding(MmSpacing.md),
        content = content
    )
}

/** A circular chip-sized action used inside hero surfaces. */
@Composable
fun MmHeroAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MmColors.onBrand,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .pressableClick(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** A labelled metric used in grids: icon, caption, value. */
@Composable
fun MmStatTile(
    icon: ImageVector,
    caption: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(MmShape.row)
            .background(MmColors.surfaceMuted)
            .border(1.dp, MmColors.hairline, MmShape.row)
            .then(if (onClick != null) Modifier.pressableClick(onClick = onClick) else Modifier)
            .padding(MmSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MmIconBadge(icon = icon, tint = tint, size = 40.dp)
        Column(Modifier.padding(start = MmSpacing.md)) {
            Text(
                caption,
                style = MmType.caption,
                color = MmColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MmType.amountTile,
                color = MmColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
