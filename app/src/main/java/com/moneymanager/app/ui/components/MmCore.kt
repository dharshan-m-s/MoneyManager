package com.moneymanager.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * A surface card with the app's single card language: rounded corners, hairline outline,
 * no heavy elevation. Cards are used only where they group related information.
 */
@Composable
fun MmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(MmSpacing.card),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(MmSpacing.radiusCard),
        elevation = 0.dp,
        backgroundColor = MmColors.surface,
        border = BorderStroke(1.dp, MmColors.outline)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/** A card that renders as plain grouped content without the outline (for dense sections). */
@Composable
fun MmPlainCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(MmSpacing.radiusCard),
        elevation = 0.dp,
        backgroundColor = MmColors.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(MmSpacing.card), content = content)
    }
}

/**
 * Section heading with an optional trailing action. Keeps the "what can I do here" affordance
 * consistent between screens without a toolbar full of icons.
 */
@Composable
fun MmSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = MmColors.accent,
                style = MmType.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(MmSpacing.radiusRow))
                    .clickable(onClick = onAction)
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm)
            )
        }
    }
}

/** Category-style icon badge: rounded square, tinted background, centred vector icon. */
@Composable
fun MmIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2.9f))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Small rounded pill used for statuses, counts and applied filters. */
@Composable
fun MmPill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) tint.copy(alpha = 0.16f) else MmColors.surfaceMuted)
            .padding(horizontal = MmSpacing.md, vertical = 5.dp)
    ) {
        Text(
            text,
            color = if (filled) tint else MmColors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Selectable chip used for filters, types and quick choices. */
@Composable
fun MmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) MmColors.accent.copy(alpha = 0.16f) else MmColors.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) MmColors.accent else MmColors.textSecondary,
            style = MmType.caption,
            maxLines = 1
        )
    }
}

/** iOS-style segmented control (One UI equivalent: simple, obvious, single-choice). */
@Composable
fun MmSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MmSpacing.radiusField))
            .background(MmColors.surfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(MmSpacing.radiusField - 3.dp))
                    .background(if (active) MmColors.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MmType.label,
                    color = if (active) MmColors.accent else MmColors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Labelled switch row used by settings/option groups. */
@Composable
fun MmSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = MmSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = MmSpacing.md)) {
            Text(title, style = MmType.body, color = MmColors.textPrimary)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MmType.caption, color = MmColors.textSecondary)
            }
        }
        androidx.compose.material.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MmColors.accent
            )
        )
    }
}

/** Thin progress indicator for budgets and cycle usage. */
@Composable
fun MmProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MmColors.surfaceMuted)
    ) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .height(height)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** One labelled fact. Renders nothing when [value] is meaningless, so screens never show "—". */
@Composable
fun MmInfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    showWhenBlank: Boolean = false
) {
    val shown = value?.takeIf { it.isNotBlank() }
    if (shown == null && !showWhenBlank) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MmType.label, color = MmColors.textSecondary, modifier = Modifier.width(122.dp))
        Text(
            shown ?: "—",
            style = MmType.body,
            color = MmColors.textPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

/** Groups [MmInfoRow]s under a heading inside one card. */
@Composable
fun MmInfoGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    MmCard(modifier = modifier) {
        Text(title, style = MmType.label, color = MmColors.textSecondary)
        Spacer(Modifier.height(MmSpacing.xs))
        content()
    }
}

/** Friendly empty state: icon, explanation, and (optionally) the action that fixes it. */
@Composable
fun MmEmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.xl, vertical = MmSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MmIconBadge(icon = icon, tint = MmColors.accent, size = 56.dp)
        Spacer(Modifier.height(MmSpacing.md))
        Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary, textAlign = TextAlign.Center)
        message?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(MmSpacing.xs))
            Text(it, style = MmType.caption, color = MmColors.textSecondary, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MmSpacing.lg))
            androidx.compose.material.Button(
                onClick = onAction,
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text(actionLabel, color = MmColors.onAccent)
            }
        }
    }
}

/** Full-screen centred loading placeholder (no frozen blank screens). */
@Composable
fun MmLoadingState(text: String = "Loading…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material.CircularProgressIndicator(
                color = MmColors.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(MmSpacing.md))
            Text(text, style = MmType.caption, color = MmColors.textSecondary)
        }
    }
}

/** Full-screen error state with a retry action. */
@Composable
fun MmErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(MmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MmIconBadge(icon = androidx.compose.material.icons.Icons.Filled.Close, tint = MmColors.expense, size = 52.dp)
        Spacer(Modifier.height(MmSpacing.md))
        Text(message, style = MmType.body, color = MmColors.textPrimary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(MmSpacing.md))
            androidx.compose.material.OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(MmSpacing.radiusRow)
            ) { Text("Try again") }
        }
    }
}

/** Attractive quick-action control (One UI "keep controls within reach"). */
@Composable
fun MmQuickAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .clickable(onClick = onClick)
            .padding(vertical = MmSpacing.sm, horizontal = MmSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MmIconBadge(icon = icon, tint = tint, size = 46.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bottom sheet surface: rounded top, drag affordance, scrollable body, nav-bar safe. */
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
                    .clip(RoundedCornerShape(topStart = MmSpacing.radiusSheet, topEnd = MmSpacing.radiusSheet))
                    .background(MmColors.surface)
                    .navigationBarsPadding()
            ) {
                Box(
                    Modifier
                        .padding(top = MmSpacing.sm)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MmColors.outline)
                        .align(Alignment.CenterHorizontally)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = MmSpacing.screen, end = MmSpacing.sm, top = MmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MmType.screenTitle, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
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

/** Immutable option model shared by the pickers. */
@Immutable
data class MmOption(val id: Long, val label: String, val subtitle: String? = null)
