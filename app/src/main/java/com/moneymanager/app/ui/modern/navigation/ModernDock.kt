package com.moneymanager.app.ui.modern.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.components.mmGlassHighlight
import com.moneymanager.app.ui.components.mmLiquidGlass
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
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
 * Floating glass navigation. The backdrop is recorded by ModernRoot from the actual NavHost
 * page; this component renders the material and owns presentation/interaction state.
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
    val selectedSlot = when {
        currentRoute == DockTab.TRANSACTIONS.route -> 1
        currentRoute == DockTab.ACCOUNTS.route -> 3
        currentRoute in setOf(
            Destination.Bills.route,
            Destination.Cash.route,
            Destination.Income.route,
            Destination.SpendSummary.route,
            Destination.Budget.route,
            Destination.Categories.route,
            Destination.Reimbursements.route,
            Destination.PersonalLoan.route,
            Destination.Settings.route
        ) || overlay == DockOverlay.MORE -> 4
        else -> 0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
            .height(80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .mmLiquidGlass(cornerRadius = 36.dp, intensity = 0.48f)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = maxWidth / 4.8f
                val selectedCenter = when (selectedSlot) {
                    0 -> unit * 0.5f
                    1 -> unit * 1.5f
                    3 -> unit * 3.3f
                    else -> unit * 4.3f
                }
                val indicatorTarget = (selectedCenter - 30.dp).coerceAtLeast(0.dp)
                val indicatorX by animateDpAsState(
                    targetValue = indicatorTarget,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
                    label = "dockSelectionPosition"
                )
                // The pill draws out a little while it is travelling, then gathers back
                // to its resting shape. This is driven by the spring's remaining distance,
                // not a second animation, so rapid taps retarget naturally.
                val travelLag = (
                    kotlin.math.abs((indicatorTarget - indicatorX).value) /
                        unit.value.coerceAtLeast(1f)
                    ).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorX)
                        .size(width = 62.dp, height = 58.dp)
                        .graphicsLayer {
                            scaleX = 1f + travelLag * 0.22f
                            scaleY = 1f - travelLag * 0.10f
                        }
                        // A second, smaller lens lets the selected item refract the page
                        // independently of the dock's broader frosted surface.
                        .clip(RoundedCornerShape(27.dp))
                        .mmLiquidGlass(cornerRadius = 27.dp, intensity = 0.30f)
                )

                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockItem(DockTab.HOME, selectedSlot == 0, Modifier.weight(1f)) {
                        onNavigate(DockTab.HOME.route!!)
                    }
                    DockItem(DockTab.TRANSACTIONS, selectedSlot == 1, Modifier.weight(1f)) {
                        onNavigate(DockTab.TRANSACTIONS.route!!)
                    }
                    Box(Modifier.weight(0.8f).fillMaxHeight())
                    DockItem(DockTab.ACCOUNTS, selectedSlot == 3, Modifier.weight(1f)) {
                        onNavigate(DockTab.ACCOUNTS.route!!)
                    }
                    DockItem(DockTab.MORE, selectedSlot == 4, Modifier.weight(1f), onToggleMore)
                }
            }
        }

        AddOrb(
            expanded = overlay == DockOverlay.ADD,
            onClick = onToggleAdd,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-13).dp)
        )
    }
}

@Composable
private fun AddOrb(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "dockAdd"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "dockAddScale"
    )
    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MmColors.accent.copy(alpha = 0.98f),
                        MmColors.accentFill,
                        MmColors.accent.copy(alpha = 0.88f)
                    )
                )
            )
            .mmGlassHighlight(cornerRadius = 27.dp, intensity = 0.62f)
            .pressableClick(haptic = true, onClick = onClick)
            .semantics {
                contentDescription = if (expanded) "Close add menu" else "Add transaction"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MmColors.onAccent,
            modifier = Modifier.size(27.dp).graphicsLayer { rotationZ = rotation }
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
        targetValue = if (selected) {
            if (MaterialTheme.colors.isLight) MmColors.accent else Color.White
        } else MmColors.textTertiary,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "dockTint"
    )
    val iconScale = remember { Animatable(1f) }
    val iconRotation = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            // A tiny squash, overshoot and settle gives the selected symbol a tactile arrival.
            iconScale.snapTo(0.88f)
            iconRotation.snapTo(-7f)
            iconScale.animateTo(
                targetValue = 1.16f,
                animationSpec = spring(dampingRatio = 0.48f, stiffness = 620f)
            )
            iconRotation.animateTo(
                targetValue = 4f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = 560f)
            )
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 560f)
            )
            iconRotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 620f)
            )
        } else {
            iconScale.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 520f))
            iconRotation.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 600f))
        }
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(24.dp))
            .pressableClick(haptic = true, onClick = onClick)
            .semantics {
                contentDescription = if (selected) "${tab.label}, selected" else tab.label
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
                rotationZ = iconRotation.value
            }
        )
        Text(
            tab.label,
            color = tint,
            style = MmType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
