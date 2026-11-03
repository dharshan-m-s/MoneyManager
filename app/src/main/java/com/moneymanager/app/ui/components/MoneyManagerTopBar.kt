package com.moneymanager.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.components.scrollCompactHeight
import com.moneymanager.app.ui.components.scrollCollapse
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Shared stationary navigation chrome. By default the complete top strip is a Liquid Glass
 * surface that samples the active destination; callers may set [transparentChrome] when the
 * toolbar is part of a larger, continuous glass hero.
 */
/**
 * Lets a header draw above the scrolling viewport without consuming vertical layout space.
 * The header is measured normally; scrollable content reserves the measured height separately.
 */
fun Modifier.overlayWithoutLayoutSpace(): Modifier = zIndex(1f).layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, 0) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
fun MoneyManagerTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick: (() -> Unit)? = onBack,
    actions: @Composable RowScope.() -> Unit = {},
    compact: Boolean = false,
    glassChrome: Boolean = true,
    collapseProgress: State<Float>? = null,
    keepTitleOnCollapse: Boolean = false,
    hideNavigationOnCollapse: Boolean = false,
    collapseToolbarCompletely: Boolean = false,
    modifier: Modifier = Modifier,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
    transparentChrome: Boolean = false
) {
    Column(modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalMmGlassEnabled provides true) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            transparentChrome -> Modifier
                            glassChrome -> Modifier.mmLiquidGlass(
                                cornerRadius = 24.dp,
                                intensity = 1.08f,
                                blurMultiplier = 1.4f
                            )
                            else -> Modifier.mmTopBarSurface(accentStrength = 0.72f)
                        }
                    )
                    .modernTopInsets()
                    .padding(horizontal = MmSpacing.sm)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            collapseProgress?.let {
                                Modifier.scrollCompactHeight(
                                    progress = it,
                                    expandedHeight = if (compact) 56.dp else 74.dp,
                                    compactHeight = if (collapseToolbarCompletely) 0.dp else 54.dp
                                )
                            } ?: Modifier.heightIn(min = if (compact) 56.dp else 74.dp).animateContentSize()
                        )
                        .padding(
                            horizontal = MmSpacing.md,
                            vertical = if (compact) 2.dp else 5.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigationClick != null) {
                        IconButton(
                            onClick = onNavigationClick,
                            modifier = Modifier.size(44.dp)
                                .then(
                                    if (hideNavigationOnCollapse) {
                                        collapseProgress?.let { Modifier.scrollCollapse(it) } ?: Modifier
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                navigationIcon,
                                contentDescription = "Back",
                                tint = MmColors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(MmSpacing.xs))
                    }

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = MmSpacing.md)
                            .then(
                                if (keepTitleOnCollapse) Modifier
                                else collapseProgress?.let { Modifier.scrollCollapse(it) } ?: Modifier
                            )
                    ) {
                        if (!compact || keepTitleOnCollapse) {
                            Text(
                                title,
                                style = MmType.screenTitle.copy(fontSize = 25.sp, lineHeight = 31.sp),
                                color = MmColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    subtitle,
                                    style = MmType.caption,
                                    color = MmColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (keepTitleOnCollapse) {
                                        collapseProgress?.let { Modifier.scrollCollapse(it) } ?: Modifier
                                    } else Modifier
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs),
                        modifier = Modifier.padding(start = MmSpacing.xs),
                        content = actions
                    )
                }
                bottomContent()
            }
        }
    }
}

@Composable
fun MmTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MmColors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MmColors.warning)
            )
        }
    }
}
