package com.moneymanager.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneymanager.app.ui.theme.DashboardColors
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import kotlinx.coroutines.launch

/**
 * The four "Add" options mirror the PDF's dashboard FAB speed-dial exactly (final screenshot):
 * Add Account Income, Add Account Spend, Add Cash Income, Add Cash Spend. Each navigates to
 * the real Add Transaction screen with the matching entry point, so tapping one is a genuine
 * save path, not a decorative dead end.
 */
@Composable
fun MoneyManagerRoot() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Dashboard.route
    var fabExpanded by remember { mutableStateOf(false) }

    fun openAddTransaction(entryPoint: String) {
        fabExpanded = false
        val isAccountEntry = entryPoint == "ACCOUNT_INCOME" || entryPoint == "ACCOUNT_SPEND"
        if (isAccountEntry) {
            navController.navigate(Destination.AccountSelection.route(entryPoint))
        } else {
            navController.navigate(Destination.AddTransaction.route(entryPoint, null))
        }
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(currentRoute = currentRoute) { dest ->
                scope.launch { drawerState.close() }
                navController.navigate(dest.route) {
                    popUpTo(Destination.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            MoneyManagerNavHost(
                navController = navController,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                modifier = Modifier.fillMaxSize()
            )
            if (currentRoute == Destination.Dashboard.route) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
                ) {
                    if (fabExpanded) {
                        SpeedDialOption("Add Cash Spend", Icons.Filled.ShoppingCart) { openAddTransaction("CASH_SPEND") }
                        SpeedDialOption("Add Cash Income", Icons.AutoMirrored.Filled.CallReceived) { openAddTransaction("CASH_INCOME") }
                        SpeedDialOption("Add Account Spend", Icons.Filled.Payment) { openAddTransaction("ACCOUNT_SPEND") }
                        SpeedDialOption("Add Account Income", Icons.Filled.AccountBalance) { openAddTransaction("ACCOUNT_INCOME") }
                    }
                    DashboardSpeedDialFab(expanded = fabExpanded, onClick = { fabExpanded = !fabExpanded })
                }
            }
        }
    }
}

@Composable
private fun DashboardSpeedDialFab(expanded: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    )
    // One UI 9: true circle, gradient fill, perfectly centered icon.
    Box(
        modifier = Modifier
            .size(60.dp)
            .shadow(
                10.dp, CircleShape, clip = false,
                ambientColor = DashboardColors.gradientEnd.copy(alpha = 0.45f),
                spotColor = DashboardColors.gradientEnd.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(DashboardColors.gradientStart, DashboardColors.gradientEnd)))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.0f))
                    )
                )
        )
        Icon(
            if (expanded) Icons.Filled.Close else Icons.Filled.Add,
            contentDescription = if (expanded) "Close" else "Add",
            tint = MMWhite,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun SpeedDialOption(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            elevation = 2.dp,
            shape = RoundedCornerShape(14.dp),
            color = androidx.compose.material.MaterialTheme.colors.surface
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        // Mini 46dp white circle with a guaranteed-centered icon.
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(6.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(androidx.compose.material.MaterialTheme.colors.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = MMGreenDark, modifier = Modifier.size(22.dp))
        }
    }
}
