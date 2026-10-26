package com.moneymanager.app.ui.modern.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.modern.components.pressableClick
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmType
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.navigation.Destination

/** Which floating menu, if any, is open above the dock. Exactly one can be open at a time. */
internal enum class DockOverlay { NONE, ADD, MORE }

internal enum class DockTab(
    val label: String,
    val icon: ImageVector,
    val route: String?
) {
    HOME("Home", Icons.Filled.Home, Destination.Dashboard.route),
    TRANSACTIONS("Activity", Icons.AutoMirrored.Filled.ReceiptLong, Destination.Transactions.route),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalance, Destination.Accounts.route),
    MORE("More", Icons.Filled.GridView, null)
}

/**
 * One continuous floating navigation pill with the prominent Add control centered above it.
 */
@Composable
internal fun ModernDock(
    currentRoute: String,
    overlay: DockOverlay,
    onNavigate: (String) -> Unit,
    onToggleAdd: () -> Unit,
    onToggleMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = MmColors.surface,
            border = BorderStroke(1.dp, MmColors.outline),
            elevation = 6.dp
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItem(DockTab.HOME, currentRoute == DockTab.HOME.route && overlay == DockOverlay.NONE, Modifier.weight(1f)) { onNavigate(DockTab.HOME.route!!) }
            DockItem(DockTab.TRANSACTIONS, currentRoute == DockTab.TRANSACTIONS.route && overlay == DockOverlay.NONE, Modifier.weight(1f)) { onNavigate(DockTab.TRANSACTIONS.route!!) }
            Box(Modifier.weight(0.8f))
            DockItem(DockTab.ACCOUNTS, currentRoute == DockTab.ACCOUNTS.route && overlay == DockOverlay.NONE, Modifier.weight(1f)) { onNavigate(DockTab.ACCOUNTS.route!!) }
            DockItem(DockTab.MORE, overlay == DockOverlay.MORE, Modifier.weight(1f), onToggleMore)
        }
    }

        Box(Modifier.align(Alignment.TopCenter).offset(y = (-20).dp), contentAlignment = Alignment.Center) {
            AddOrb(expanded = overlay == DockOverlay.ADD, onClick = onToggleAdd)
        }
    }
}

@Composable
private fun AddOrb(
    expanded: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(180),
        label = "dockAdd"
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MmColors.accentFill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (expanded) "Close add menu" else "Add transaction"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MmColors.onAccent,
            modifier = Modifier
                .size(27.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun DockItem(
    tab: DockTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MmColors.accent else MmColors.textTertiary,
        animationSpec = tween(160),
        label = "dockTint"
    )
    val background by animateColorAsState(
        targetValue = if (selected) MmColors.accent.copy(alpha = 0.11f) else MmColors.surface.copy(alpha = 0f),
        animationSpec = tween(160),
        label = "dockBackground"
    )
    Column(
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .pressableClick(onClick = onClick)
            .semantics {
                contentDescription = if (selected) "${tab.label}, selected" else tab.label
            }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        Text(
            tab.label,
            color = tint,
            style = MmType.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
