package com.moneymanager.app.ui.modern.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType

@Composable
fun MoneyManagerTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick: (() -> Unit)? = onBack,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MmColors.background, elevation = 0.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .modernTopInsets()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigationClick != null) {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Back", tint = MmColors.textPrimary)
                    }
                } else {
                    Spacer(Modifier.width(MmSpacing.md))
                }
                Column(Modifier.weight(1f).padding(horizontal = MmSpacing.xs)) {
                    Text(
                        title,
                        style = MmType.sectionTitle,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs),
                    modifier = Modifier.padding(end = MmSpacing.xs),
                    content = actions
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MmColors.outline)
            )
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
        Surface(
            modifier = Modifier.clip(CircleShape),
            shape = CircleShape,
            color = MmColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MmColors.outline)
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                Icon(icon, contentDescription = contentDescription, tint = MmColors.textPrimary, modifier = Modifier.size(20.dp))
            }
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
