package com.moneymanager.app.ui.modern.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmShape
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import kotlin.math.roundToLong

/**
 * The interaction layer for the Modern Look.
 *
 * Motion is short (120–320ms), eased and never bouncing: it acknowledges a tap, assembles content
 * calmly and counts changed money values. Nothing loops except the loading shimmer, and nothing
 * delays a financial action.
 */

/** Press feedback: the surface dips slightly while held. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A clickable that also dips under the finger (the ripple stays). */
@Composable
fun Modifier.pressableClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressScale(interactionSource, enabled = enabled)
        .clickable(enabled = enabled, interactionSource = interactionSource, onClick = onClick)
}

/** A soft entrance: the element fades in and rises a little, staggered by [index]. */
@Composable
fun Modifier.revealOnAppear(
    index: Int = 0,
    delayPerItemMillis: Int = 32,
    maxStaggerSteps: Int = 8
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        val delay = (index.coerceAtMost(maxStaggerSteps) * delayPerItemMillis).toLong()
        kotlinx.coroutines.delay(delay)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        )
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
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
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
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "primaryButton"
    )
    val glow = MmColors.accentFill
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            .then(if (enabled) Modifier.mmGlassHighlight(cornerRadius = 18.dp, intensity = 0.75f) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick
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
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "secondaryButton"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MmShape.row)
            .background(MmColors.surface)
            .border(1.dp, MmColors.hairline, MmShape.row)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick
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
