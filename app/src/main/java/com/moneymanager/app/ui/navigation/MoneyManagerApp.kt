package com.moneymanager.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.security.PinUnlockScreen
import com.moneymanager.app.ui.settings.SettingsEntryPoint
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Root shell: navigation host + drawer + the dashboard FAB speed dial, wrapped in the real
 * PIN gate. The app relocks when it has been in the background for longer than the configured
 * grace period, so a brief trip to the photo picker does not force a re-entry.
 */
@Composable
fun MoneyManagerRoot() {
    val context = LocalContext.current
    val appLock = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java
        ).appLock()
    }
    var locked by remember { mutableStateOf(appLock.isEnabled()) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appLock) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val since = backgroundedAt
                    if (appLock.isEnabled() && since > 0L &&
                        System.currentTimeMillis() - since > appLock.relockAfterMillis()
                    ) {
                        locked = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        PinUnlockScreen(
            onUnlock = { pin ->
                val ok = appLock.verify(pin)
                if (ok) locked = false
                ok
            }
        )
        return
    }

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
        // Edge-to-edge: each screen's own header draws under the status bar (statusBarsPadding
        // lives inside the headers), and the host is inset at the bottom so lists and forms can
        // never end up underneath the navigation bar or gesture handle.
        Box(Modifier.fillMaxSize()) {
            MoneyManagerNavHost(
                navController = navController,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                modifier = Modifier.fillMaxSize().navigationBarsPadding()
            )
            if (currentRoute == Destination.Dashboard.route) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(MmSpacing.lg)
                ) {
                    if (fabExpanded) {
                        SpeedDialOption("Cash spend", Icons.Filled.Payments, MmColors.expense) { openAddTransaction("CASH_SPEND") }
                        SpeedDialOption("Cash income", Icons.Filled.Savings, MmColors.income) { openAddTransaction("CASH_INCOME") }
                        SpeedDialOption("Account spend", Icons.AutoMirrored.Filled.TrendingDown, MmColors.expense) { openAddTransaction("ACCOUNT_SPEND") }
                        SpeedDialOption("Account income", Icons.AutoMirrored.Filled.TrendingUp, MmColors.income) { openAddTransaction("ACCOUNT_INCOME") }
                        Spacer(Modifier.height(MmSpacing.sm))
                    }
                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        backgroundColor = MmColors.accent,
                        contentColor = MmColors.onAccent,
                        text = {
                            Text(
                                if (fabExpanded) "Close" else "Add",
                                style = MmType.label,
                                color = MmColors.onAccent
                            )
                        },
                        icon = {
                            Icon(
                                if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                                contentDescription = null,
                                tint = MmColors.onAccent
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * A labelled quick action in the FAB speed dial. The whole row (label + button) is one touch
 * target so the choice is unambiguous and stays within thumb reach.
 */
@Composable
private fun SpeedDialOption(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(bottom = MmSpacing.sm)
            .clip(RoundedCornerShape(MmSpacing.touchTarget / 2))
            .background(MmColors.surface)
            .border(1.dp, MmColors.outline, RoundedCornerShape(MmSpacing.touchTarget / 2))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        Text(label, style = MmType.label, color = MmColors.textPrimary)
        MmIconBadge(icon = icon, tint = tint, size = 36.dp)
    }
}
