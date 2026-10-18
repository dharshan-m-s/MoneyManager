package com.moneymanager.app.ui.navigation

import android.os.SystemClock
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.security.LockMethod
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.mmBrandWash
import com.moneymanager.app.ui.security.PinUnlockScreen
import com.moneymanager.app.ui.security.SystemLockPrompt
import com.moneymanager.app.ui.settings.SettingsEntryPoint
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import timber.log.Timber

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
    // Bumped when the configured method changes so a method switch re-runs the right gate.
    var lockSession by remember { mutableIntStateOf(0) }

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
                        lockSession += 1
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (locked) {
        var pinFallback by remember(lockSession) { mutableStateOf(false) }
        var systemUnavailable by remember(lockSession) { mutableStateOf(false) }
        // Re-arms the platform prompt after a failed attempt ("Try again").
        var promptAttempt by remember(lockSession) { mutableIntStateOf(0) }
        when {
            appLock.method() == LockMethod.SYSTEM_BIOMETRIC && !pinFallback -> {
                // key() re-runs the prompt's LaunchedEffect after "Try again".
                key(promptAttempt) {
                    SystemLockPrompt(
                        title = "Unlock Money Manager",
                        subtitle = "Confirm it's you to open your ledger",
                        onUnlocked = { locked = false },
                        onCancelled = {
                            // A deliberate dismissal only falls back when a recovery PIN exists;
                            // otherwise the user would hit a PIN screen that can never let them in.
                            if (appLock.hasPin()) pinFallback = true
                        },
                        onUnavailable = {
                            systemUnavailable = true
                            if (appLock.hasPin()) pinFallback = true
                        }
                    )
                }
                if (systemUnavailable && !appLock.hasPin()) {
                    // No screen lock usable and no recovery PIN: retrying is the only way in.
                    SystemLockRetry(onRetry = {
                        systemUnavailable = false
                        promptAttempt += 1
                    })
                } else {
                    // Branded surface while the system prompt is showing.
                    Box(Modifier.fillMaxSize().mmBrandWash())
                }
            }
            else -> PinUnlockScreen(
                // With the system method this verifies the optional recovery PIN.
                subtitle = if (appLock.method() == LockMethod.SYSTEM_BIOMETRIC)
                    "Enter your recovery PIN to continue"
                else "Enter your PIN to continue",
                expectedLength = appLock.pinLength(),
                onUnlock = { pin ->
                    // Measured stage: PBKDF2 verification. This runs on Dispatchers.Default (see
                    // PinUnlockScreen), never on the main thread. The Trace section shows up in
                    // Perfetto; the debug log makes the figure readable without a profiler.
                    val startedAt = SystemClock.elapsedRealtime()
                    android.os.Trace.beginSection("AppLock.verify")
                    val ok = appLock.verify(pin)
                    android.os.Trace.endSection()
                    if (BuildConfig.DEBUG) {
                        Timber.d(
                            "PIN verification took %d ms (accepted=%s)",
                            SystemClock.elapsedRealtime() - startedAt,
                            ok
                        )
                    }
                    if (ok) {
                        // Authenticated state is available: drop the gate immediately. The
                        // authenticated screen composes its own loading states; nothing here
                        // waits for nonessential dashboard work.
                        android.os.Trace.beginSection("AppLock.unlockNavigate")
                        locked = false
                        android.os.Trace.endSection()
                    }
                    ok
                }
            )
        }
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

    // System Back closes an open drawer first instead of popping the screen underneath it.
    if (drawerState.isOpen) {
        androidx.activity.compose.BackHandler {
            scope.launch { drawerState.close() }
        }
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(currentRoute = currentRoute) { dest ->
                scope.launch { drawerState.close() }
                // NAVIGATION_TEST_MATRIX: drawer navigation resets to Dashboard + the selected
                // section. It must never restore a nested editor stack from saved state, so no
                // saveState/restoreState round-trip here - each section opens at its own root.
                navController.navigate(dest.route) {
                    popUpTo(Destination.Dashboard.route)
                    launchSingleTop = true
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
                        shape = RoundedCornerShape(18.dp),
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
 * Shown when the system screen lock cannot be used and no recovery PIN exists: retrying the
 * platform prompt is the only way in, so that is the single action offered.
 */
@Composable
private fun SystemLockRetry(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .mmBrandWash()
            .navigationBarsPadding()
            .padding(MmSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MmIconBadge(icon = Icons.Filled.Lock, tint = MmColors.accent, size = 64.dp)
        Spacer(Modifier.height(MmSpacing.lg))
        Text(
            "Screen lock unavailable",
            style = MmType.screenTitle,
            color = MmColors.textPrimary
        )
        Spacer(Modifier.height(MmSpacing.xs))
        Text(
            "No screen lock is enrolled on this device and no recovery PIN was set. Add a " +
                "screen lock in this device's settings, then come back to unlock. Setting a " +
                "recovery PIN beforehand avoids this.",
            style = MmType.caption,
            color = MmColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MmSpacing.xl))
        ExtendedFloatingActionButton(
            onClick = onRetry,
            backgroundColor = MmColors.accent,
            contentColor = MmColors.onAccent,
            shape = RoundedCornerShape(MmSpacing.touchTarget / 2),
            text = { Text("Try again", style = MmType.label, color = MmColors.onAccent) },
            icon = {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MmColors.onAccent)
            }
        )
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
