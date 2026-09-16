package com.moneymanager.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
                        SpeedDialOption("Add Cash Spend") { openAddTransaction("CASH_SPEND") }
                        SpeedDialOption("Add Cash Income") { openAddTransaction("CASH_INCOME") }
                        SpeedDialOption("Add Account Spend") { openAddTransaction("ACCOUNT_SPEND") }
                        SpeedDialOption("Add Account Income") { openAddTransaction("ACCOUNT_INCOME") }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        backgroundColor = MMGreenDark,
                        contentColor = MMWhite,
                        text = { Text(if (fabExpanded) "Close" else "Add") },
                        icon = { Icon(if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialOption(label: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(elevation = 2.dp, color = androidx.compose.material.MaterialTheme.colors.surface) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        androidx.compose.material.FloatingActionButton(
            onClick = onClick,
            backgroundColor = androidx.compose.material.MaterialTheme.colors.surface,
            modifier = Modifier
        ) {
            Icon(Icons.Filled.Add, contentDescription = label, tint = MMGreenDark)
        }
    }
}
