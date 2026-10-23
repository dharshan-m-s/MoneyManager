package com.moneymanager.app.ui.modern.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.navigation.Destination

internal enum class DockTab(
    val label: String,
    val icon: ImageVector,
    val route: String?
) {
    HOME("Home", Icons.Filled.Home, Destination.Dashboard.route),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Destination.Transactions.route),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalance, Destination.Accounts.route),
    MORE("More", Icons.Filled.GridView, null)
}

@Composable
internal fun ModernDock(
    currentRoute: String,
    fabExpanded: Boolean,
    onToggleFab: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = DockTab.entries.firstOrNull { it.route == currentRoute }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = MmColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MmColors.outline),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DockItem(DockTab.HOME, selected == DockTab.HOME, Modifier.weight(1f)) {
                    onNavigate(DockTab.HOME.route!!)
                }
                DockItem(DockTab.TRANSACTIONS, selected == DockTab.TRANSACTIONS, Modifier.weight(1f)) {
                    onNavigate(DockTab.TRANSACTIONS.route!!)
                }
                // Space for center button
                Box(Modifier.weight(0.8f))
                DockItem(DockTab.ACCOUNTS, selected == DockTab.ACCOUNTS, Modifier.weight(1f)) {
                    onNavigate(DockTab.ACCOUNTS.route!!)
                }
                DockItem(DockTab.MORE, selected = false, Modifier.weight(1f)) { onOpenMore() }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp),
            contentAlignment = Alignment.Center
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (fabExpanded) 45f else 0f,
                animationSpec = tween(200),
                label = "dockAdd"
            )
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFab)
                    .semantics {
                        contentDescription = if (fabExpanded) "Close add menu" else "Add transaction"
                    },
                shape = CircleShape,
                color = MmColors.accentFill,
                border = androidx.compose.foundation.BorderStroke(2.dp, MmColors.onAccent.copy(alpha = 0.15f)),
                elevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MmColors.onAccent,
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    tab: DockTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) MmColors.accent else MmColors.textTertiary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (selected) "${tab.label}, selected" else tab.label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            tab.label,
            color = tint,
            style = com.moneymanager.app.ui.modern.theme.MmType.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
