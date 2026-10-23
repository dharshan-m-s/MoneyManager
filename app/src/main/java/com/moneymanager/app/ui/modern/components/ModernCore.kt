package com.moneymanager.app.ui.modern.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmShape
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType

@Composable
fun MmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(MmSpacing.card),
    backgroundColor: Color = MmColors.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        shape = MmShape.card,
        color = backgroundColor,
        border = BorderStroke(1.dp, MmColors.outline),
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
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        shape = MmShape.card,
        color = MmColors.surfaceContainer,
        border = BorderStroke(1.dp, MmColors.outline),
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
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f))
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
    val bg = if (filled) tint.copy(alpha = 0.14f) else Color.Transparent
    val borderColor = if (filled) tint.copy(alpha = 0.3f) else MmColors.outline
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MmColors.accent.copy(alpha = 0.14f) else MmColors.surface,
        border = BorderStroke(1.dp, if (selected) MmColors.accent.copy(alpha = 0.4f) else MmColors.outline)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = MmColors.surfaceContainer,
        border = BorderStroke(1.dp, MmColors.outline)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier.weight(1f).clickable(onClick = { onSelect(index) }),
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MmColors.surface else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, MmColors.outline) else BorderStroke(1.dp, Color.Transparent)
                ) {
                    Text(
                        option,
                        style = MmType.label,
                        color = if (selected) MmColors.textPrimary else MmColors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
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
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(MmSpacing.radiusRow)).clickable { onCheckedChange(!checked) }.padding(MmSpacing.card),
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
                modifier = Modifier.clickable(onClick = onAction),
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
    Surface(
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(999.dp),
        color = MmColors.surfaceContainerHigh
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
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
                    .clip(RoundedCornerShape(topStart = MmSpacing.radiusSheet, topEnd = MmSpacing.radiusSheet))
                    .background(MmColors.surface)
                    .border(BorderStroke(1.dp, MmColors.outline))
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
        modifier = modifier.clip(RoundedCornerShape(MmSpacing.radiusRow)).clickable(onClick = onClick).padding(vertical = 8.dp),
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
