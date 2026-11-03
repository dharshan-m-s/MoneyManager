package com.moneymanager.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun MmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(MmSpacing.card),
    backgroundColor: Color = MmColors.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .animateContentSize(animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f))
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .pressScale(interactionSource, pressedScale = 0.975f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ) else Modifier
            ),
        shape = cardShape,
        color = backgroundColor,
        border = null,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}


/**
 * Editorial spotlight used at the start of focused workflows. It adds a clear visual anchor
 * without turning every page into a loud gradient banner.
 */
@Composable
fun MmSpotlightPanel(
    eyebrow: String,
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MmColors.accent
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MmColors.surfaceContainer)
            .border(BorderStroke(0.75.dp, tint.copy(alpha = 0.14f)), shape)
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(MmSpacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                eyebrow.uppercase(),
                style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 1.05.sp),
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(description, style = MmType.caption, color = MmColors.textSecondary,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Compact financial metric tile shared by overview screens. The icon is a quiet category cue;
 * the amount remains the strongest element and uses tabular numerals from the theme.
 */
@Composable
fun MmMetricCard(
    label: String,
    value: String,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(shape)
            .background(MmColors.surfaceContainer)
            .border(BorderStroke(0.75.dp, tint.copy(alpha = 0.15f)), shape)
            .then(
                if (onClick != null) Modifier
                    .pressScale(interactionSource, pressedScale = 0.975f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ) else Modifier
            )
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(MmSpacing.sm))
            Text(
                label,
                style = MmType.caption,
                color = MmColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(MmSpacing.md))
        Text(
            value,
            style = MmType.amountTile,
            color = MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Spacious amount panel for detail and wallet pages. Unlike a promotional banner, it keeps the
 * amount on a calm surface and uses a slim accent rule to establish hierarchy in both themes.
 */
@Composable
fun MmBalancePanel(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = MmColors.accent,
    animatedValueMinorUnits: Long? = null,
    visualValues: List<Long> = emptyList(),
    visualLabel: String? = null,
    artworkKind: String = "overview",
    glass: Boolean = false,
    compactProgress: State<Float>? = null
) {
    val shape = RoundedCornerShape(28.dp)
    val maxVisual = visualValues.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1L) ?: 1L
    val compact = (compactProgress?.value ?: 0f).coerceIn(0f, 1f)
    val panelPadding by animateDpAsState(
        targetValue = MmSpacing.xl - (8.dp * compact),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
        label = "balancePanelPadding"
    )
    val contentGap by animateDpAsState(
        targetValue = MmSpacing.md - (6.dp * compact),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
        label = "balancePanelGap"
    )
    val artworkSize by animateDpAsState(
        targetValue = 76.dp - (22.dp * compact),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
        label = "balancePanelArtwork"
    )
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glass) Modifier.mmLiquidGlass(cornerRadius = 28.dp, intensity = 0.88f) else Modifier)
            .clip(shape)
            .background(if (glass) Color.Transparent else MmColors.surfaceContainer)
            .padding(panelPadding)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint)
            )
            Spacer(Modifier.width(MmSpacing.sm))
            Text(label, style = MmType.label, color = MmColors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.height(contentGap))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (animatedValueMinorUnits != null) {
                    MmAnimatedAmount(
                        value = animatedValueMinorUnits,
                        formatter = { MoneyFormat.rupeesNoDecimals(com.moneymanager.app.domain.model.Money(it)) },
                        style = MmType.amountHero,
                        color = MmColors.textPrimary
                    )
                } else {
                    Text(
                        value,
                        style = MmType.amountHero,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!caption.isNullOrBlank() && compact < 0.72f) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        caption,
                        style = MmType.caption,
                        color = MmColors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(MmSpacing.md))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                FinanceArtwork(
                    kind = artworkKind,
                    tint = tint,
                    modifier = Modifier.size(artworkSize)
                )
                if (compact < 0.72f && !visualLabel.isNullOrBlank()) {
                    Text(
                        visualLabel.uppercase(),
                        style = MmType.caption.copy(fontSize = 8.sp, letterSpacing = 0.75.sp),
                        color = MmColors.textTertiary,
                        maxLines = 1
                    )
                }
                if (compact < 0.72f && visualValues.isNotEmpty()) {
                    Row(
                        Modifier.width(94.dp).height(42.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        visualValues.takeLast(6).forEachIndexed { index, amount ->
                            val fraction = (kotlin.math.abs(amount).toFloat() / maxVisual.toFloat())
                                .coerceIn(0.06f, 1f)
                            val animatedFraction by animateFloatAsState(
                                targetValue = fraction,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 250f),
                                label = "balancePanelBar$index"
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MmColors.surface)
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedFraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tint.copy(alpha = 0.38f + fraction * 0.54f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun MmPlainCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .pressScale(interactionSource, pressedScale = 0.985f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ) else Modifier
            ),
        shape = RoundedCornerShape(24.dp),
        color = MmColors.surfaceContainer,
        border = BorderStroke(0.65.dp, MmColors.outline.copy(alpha = 0.30f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MmSpacing.card),
            content = content
        )
    }
}

@Composable
fun MmSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MmType.sectionTitle,
                color = MmColors.textPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(actionLabel, style = MmType.label, color = MmColors.accent)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MmColors.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun MmIconBadge(
    icon: ImageVector,
    tint: Color = MmColors.accent,
    backgroundColor: Color = tint.copy(alpha = 0.12f),
    size: androidx.compose.ui.unit.Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(15.dp),
        color = backgroundColor,
        border = BorderStroke(0.6.dp, tint.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun MmPill(
    label: String = "",
    modifier: Modifier = Modifier,
    tint: Color = MmColors.accent,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
    text: String = label
) {
    val bg = if (filled) tint.copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (filled) tint.copy(alpha = 0.22f) else MmColors.outline.copy(alpha = 0.45f)
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.pressableClick(haptic = true, onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(text, style = MmType.label, color = if (filled) tint else MmColors.textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
fun MmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.96f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MmColors.accent.copy(alpha = 0.12f) else MmColors.surfaceContainer,
        border = BorderStroke(0.7.dp, if (selected) MmColors.accent.copy(alpha = 0.26f) else MmColors.outline.copy(alpha = 0.34f))
    ) {
        Text(label, style = MmType.label, color = if (selected) MmColors.accent else MmColors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
fun MmSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = MmColors.surfaceContainer,
        border = BorderStroke(0.8.dp, MmColors.outline.copy(alpha = 0.62f)),
        elevation = 0.dp
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(46.dp).padding(4.dp)
        ) {
            val slotWidth = maxWidth / options.size.toFloat()
            val safeIndex = selectedIndex.coerceIn(options.indices)
            val indicatorOffset by animateDpAsState(
                targetValue = slotWidth * safeIndex.toFloat(),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                label = "segmentedSelection"
            )
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MmColors.surface)
                    .border(0.6.dp, MmColors.accent.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, option ->
                    val selected = index == safeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .pressableClick(haptic = true, onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            option,
                            style = MmType.label,
                            color = if (selected) MmColors.textPrimary else MmColors.textSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MmSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(MmSpacing.radiusRow))
            .pressableClick(haptic = true) { onCheckedChange(!checked) }
            .padding(MmSpacing.card),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MmType.body, color = MmColors.textPrimary)
                badge?.invoke()
            }
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
            }
        }
        androidx.compose.material.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material.SwitchDefaults.colors(
                checkedThumbColor = MmColors.onAccent,
                checkedTrackColor = MmColors.accent,
                uncheckedThumbColor = MmColors.textTertiary,
                uncheckedTrackColor = MmColors.surfaceContainerHigh
            )
        )
    }
}

@Composable
fun MmInfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    showWhenBlank: Boolean = false
) {
    if (value.isNullOrBlank() && !showWhenBlank) return
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MmType.body, color = MmColors.textSecondary, modifier = Modifier.weight(1f))
        Text(value.orEmpty(), style = MmType.body, color = MmColors.textPrimary)
    }
}

@Composable
fun MmEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = MmSpacing.xl, vertical = if (compact) MmSpacing.xl else MmSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        MmIconBadge(icon = icon, tint = MmColors.textTertiary, backgroundColor = MmColors.surfaceContainer, size = 56.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary, textAlign = TextAlign.Center)
            Text(message, style = MmType.body, color = MmColors.textSecondary, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Surface(
                modifier = Modifier.pressableClick(haptic = true, onClick = onAction),
                shape = RoundedCornerShape(999.dp),
                color = MmColors.accentFill,
                border = BorderStroke(1.dp, MmColors.accentFill)
            ) {
                Text(actionLabel, style = MmType.label, color = MmColors.onAccent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
fun MmLoadingState(
    text: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MmSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        androidx.compose.material.CircularProgressIndicator(color = MmColors.accent, strokeWidth = 2.dp)
        Text(text, style = MmType.caption, color = MmColors.textSecondary)
    }
}

@Composable
fun MmErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MmSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        Text(message, style = MmType.body, color = MmColors.textSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Surface(
                modifier = Modifier.clickable(onClick = onRetry),
                shape = RoundedCornerShape(999.dp),
                color = MmColors.surface,
                border = BorderStroke(1.dp, MmColors.outline)
            ) {
                Text("Retry", style = MmType.label, color = MmColors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun MmProgressBar(
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    color: Color = MmColors.accent,
    fraction: Float? = null,
    height: androidx.compose.ui.unit.Dp = 6.dp
) {
    val value = (fraction ?: progress).coerceIn(0f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 360),
        label = "progressBar"
    )
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(999.dp),
        color = MmColors.surfaceContainerHigh
    ) {
        Box(
            Modifier
                .fillMaxWidth(animatedValue)
                .height(height)
                .background(color)
        )
    }
}

@Composable
fun MmBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(topStart = MmSpacing.radiusSheet, topEnd = MmSpacing.radiusSheet))
                    .clip(RoundedCornerShape(topStart = MmSpacing.radiusSheet, topEnd = MmSpacing.radiusSheet))
                    .background(MmColors.surface)
                    .border(BorderStroke(0.65.dp, MmColors.outline.copy(alpha = 0.40f)))
                    .navigationBarsPadding()
            ) {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MmColors.outline)
                        .align(Alignment.CenterHorizontally)
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = MmSpacing.screen, end = MmSpacing.sm, top = MmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MmColors.textSecondary)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = MmSpacing.screen)
                        .padding(bottom = MmSpacing.xl),
                    content = content
                )
            }
        }
    }
}

@Immutable
data class MmOption(val id: Long, val label: String, val subtitle: String? = null)

@Composable
fun MmQuickAction(
    icon: ImageVector,
    label: String,
    tint: Color = MmColors.accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(MmSpacing.radiusRow))
            .pressableClick(haptic = true, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp).padding(10.dp))
        }
        Text(label, style = MmType.caption, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Modifier.mmBrandWash(): Modifier {
    return this.background(MmColors.background)
}
