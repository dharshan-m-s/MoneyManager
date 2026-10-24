package com.moneymanager.app.ui.modern.navigation

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneymanager.app.ui.navigation.Destination
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.security.LockMethod
import com.moneymanager.app.ui.modern.components.MmIconBadge
import com.moneymanager.app.ui.modern.components.StatusBarGlyphToneAndSystemBarTint
import com.moneymanager.app.ui.modern.components.mmBrandWash
import com.moneymanager.app.ui.modern.screens.security.PinUnlockScreen
import com.moneymanager.app.ui.modern.screens.security.SystemLockPrompt
import com.moneymanager.app.ui.settings.SettingsEntryPoint
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

private val DOCK_ROUTES = setOf(
    Destination.Dashboard.route,
    Destination.Transactions.route,
    Destination.Accounts.route
)

@Composable
fun ModernRoot() {
    val context = LocalContext.current
    val appLock = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java
        ).appLock()
    }
    var locked by remember { mutableStateOf(appLock.isEnabled()) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }
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
        StatusBarGlyphToneAndSystemBarTint(lightBackground = false)
        var pinFallback by remember(lockSession) { mutableStateOf(false) }
        var systemUnavailable by remember(lockSession) { mutableStateOf(false) }
        var promptAttempt by remember(lockSession) { mutableIntStateOf(0) }
        when {
            appLock.method() == LockMethod.SYSTEM_BIOMETRIC && !pinFallback -> {
                key(promptAttempt) {
                    SystemLockPrompt(
                        title = "Unlock Money Manager",
                        subtitle = "Confirm it's you to open your ledger",
                        onUnlocked = { locked = false },
                        onCancelled = {
                            if (appLock.hasPin()) pinFallback = true
                        },
                        onUnavailable = {
                            systemUnavailable = true
                            if (appLock.hasPin()) pinFallback = true
                        }
                    )
                }
                if (systemUnavailable && !appLock.hasPin()) {
                    SystemLockRetry(onRetry = {
                        systemUnavailable = false
                        promptAttempt += 1
                    })
                } else {
                    Box(Modifier.fillMaxSize().mmBrandWash())
                }
            }
            appLock.method() == LockMethod.IN_APP_PIN && !appLock.hasPin() -> {
                SystemLockRetry(
                    title = "PIN unavailable",
                    message = "The saved PIN could not be read. For your safety the ledger stays " +
                        "locked. Turn app lock off in Settings to continue, then set a new PIN.",
                    onRetry = {
                        appLock.disable()
                        locked = false
                        lockSession += 1
                    }
                )
            }
            else -> PinUnlockScreen(
                subtitle = if (appLock.method() == LockMethod.SYSTEM_BIOMETRIC)
                    "Enter your recovery PIN to continue"
                else "Enter your PIN to continue",
                expectedLength = appLock.pinLength(),
                isLockedOut = { appLock.isLockedOut(System.currentTimeMillis()) },
                lockoutRemainingMillis = {
                    appLock.lockoutRemainingMillis(System.currentTimeMillis())
                },
                onFailure = { appLock.recordFailedAttempt(System.currentTimeMillis()) },
                onSuccess = { appLock.clearFailedAttempts() },
                onUnlock = { pin ->
                    val startedAt = SystemClock.elapsedRealtime()
                    android.os.Trace.beginSection("AppLock.verify")
                    val ok = appLock.verify(pin)
                    android.os.Trace.endSection()
                    if (BuildConfig.DEBUG) {
                        Timber.d("PIN verification took %d ms (accepted=%s)", SystemClock.elapsedRealtime() - startedAt, ok)
                    }
                    if (ok) {
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Dashboard.route
    var fabExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        fabExpanded = false
        moreExpanded = false
    }
    val dockVisible = currentRoute in DOCK_ROUTES
    // Context-aware Add: on Accounts the central + opens account actions (bank account,
    // credit card, transfer) instead of the transaction menu, so there is exactly one
    // entry point for creating an account on this destination.
    val onAccountsRoute = currentRoute == Destination.Accounts.route
    val density = LocalDensity.current
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val measuredDockHeight = if (dockHeightPx > 0) {
        with(density) { dockHeightPx.toDp() }
    } else {
        68.dp + navigationInset + 8.dp
    }
    val dockContentReservation = measuredDockHeight + 16.dp

    StatusBarGlyphToneAndSystemBarTint(lightBackground = MaterialTheme.colors.isLight)

    fun openAddTransaction(entryPoint: String) {
        fabExpanded = false
        val isAccountEntry = entryPoint == "ACCOUNT_INCOME" || entryPoint == "ACCOUNT_SPEND"
        if (isAccountEntry) {
            navController.navigate(Destination.AccountSelection.route(entryPoint))
        } else {
            navController.navigate(Destination.AddTransaction.route(entryPoint, null))
        }
    }

    androidx.activity.compose.BackHandler(enabled = fabExpanded || moreExpanded) {
        fabExpanded = false
        moreExpanded = false
    }

    Box(Modifier.fillMaxSize()) {
        ModernNavHost(
            navController = navController,
            onOpenMore = {
                fabExpanded = false
                moreExpanded = true
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (dockVisible) Modifier.padding(bottom = dockContentReservation)
                    else Modifier.navigationBarsPadding()
                )
        )

        if ((fabExpanded || moreExpanded) && dockVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        fabExpanded = false
                        moreExpanded = false
                    }
            )
        }

        if (moreExpanded && dockVisible) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MmSpacing.lg, bottom = measuredDockHeight + 16.dp),
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.scaleIn(initialScale = 0.98f, animationSpec = androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) +
                    androidx.compose.animation.scaleOut(targetScale = 0.98f, animationSpec = androidx.compose.animation.core.tween(100))
            ) {
                ModernMorePopover(onSelect = { destination ->
                    moreExpanded = false
                    navController.navigate(destination.route) { launchSingleTop = true }
                })
            }
        }

        if (fabExpanded && dockVisible) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = measuredDockHeight + 14.dp),
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                    androidx.compose.animation.scaleIn(initialScale = 0.98f, animationSpec = androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) +
                    androidx.compose.animation.scaleOut(targetScale = 0.98f, animationSpec = androidx.compose.animation.core.tween(100))
            ) {
                ModernAddActionSurface(
                title = if (onAccountsRoute) "Add account" else "Create",
                subtitle = if (onAccountsRoute) "Bank, card or transfer" else "Transaction or bill",
                actions = if (onAccountsRoute) {
                    listOf(
                        AddAction("Bank account", Icons.Filled.AccountBalance, MmColors.accent, "Add a bank account"),
                        AddAction("Credit card", Icons.Filled.CreditCard, Color(0xFF7C3AED), "Add a credit card"),
                        AddAction("Wallet", Icons.Filled.AccountBalanceWallet, Color(0xFF059669), "Add a wallet"),
                        AddAction("Loan", Icons.Filled.RequestQuote, Color(0xFFEA580C), "Add a loan"),
                        AddAction("Transfer", Icons.Filled.SwapHoriz, MmColors.accent, "Transfer between accounts"),
                        AddAction("Bill", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFFD97706), "Add a bill")
                    )
                } else {
                    listOf(
                        AddAction("Expense", Icons.AutoMirrored.Filled.TrendingDown, MmColors.expense, "Add expense"),
                        AddAction("Income", Icons.AutoMirrored.Filled.TrendingUp, MmColors.income, "Add income"),
                        AddAction("Cash expense", Icons.Filled.Payments, MmColors.expense, "Add cash expense"),
                        AddAction("Cash income", Icons.Filled.Savings, MmColors.income, "Add cash income"),
                        AddAction("Transfer", Icons.Filled.SwapHoriz, MmColors.accent, "Transfer between accounts"),
                        AddAction("Bill", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFFD97706), "Add a bill")
                    )
                },
                onSelect = { index ->
                    fabExpanded = false
                    if (onAccountsRoute) {
                        when (index) {
                            0 -> navController.navigate(Destination.BankSelection.route) { launchSingleTop = true }
                            1 -> navController.navigate(Destination.CreditCardForm.route(null)) { launchSingleTop = true }
                            2 -> navController.navigate(Destination.AddTransaction.route("CASH_INCOME", null))
                            3 -> navController.navigate(Destination.BillTypeSelection.route) { launchSingleTop = true }
                            4 -> navController.navigate(Destination.Transfer.route())
                            5 -> navController.navigate(Destination.BillTypeSelection.route) { launchSingleTop = true }
                        }
                    } else {
                        when (index) {
                            0 -> openAddTransaction("ACCOUNT_SPEND")
                            1 -> openAddTransaction("ACCOUNT_INCOME")
                            2 -> openAddTransaction("CASH_SPEND")
                            3 -> openAddTransaction("CASH_INCOME")
                            4 -> navController.navigate(Destination.Transfer.route())
                            5 -> navController.navigate(Destination.BillTypeSelection.route) { launchSingleTop = true }
                        }
                    }
                }
            )
            }
        }

        if (dockVisible) {
            ModernDock(
                currentRoute = currentRoute,
                fabExpanded = fabExpanded,
                onToggleFab = {
                    moreExpanded = false
                    fabExpanded = !fabExpanded
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { dockHeightPx = it.height },
                onNavigate = { route ->
                    moreExpanded = false
                    fabExpanded = false
                    navController.navigate(route) {
                        popUpTo(Destination.Dashboard.route)
                        launchSingleTop = true
                    }
                },
                onOpenMore = {
                    fabExpanded = false
                    moreExpanded = !moreExpanded
                }
            )
        }
    }
}

@Composable
private fun SystemLockRetry(
    onRetry: () -> Unit,
    title: String = "Screen lock unavailable",
    message: String = "No screen lock is enrolled on this device and no recovery PIN was set. Add a " +
        "screen lock in this device's settings, then come back to unlock. Setting a " +
        "recovery PIN beforehand avoids this.",
    actionLabel: String = "Try again"
) {
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
        Text(title, style = MmType.screenTitle, color = MmColors.textPrimary)
        Spacer(Modifier.height(MmSpacing.xs))
        Text(message, style = MmType.caption, color = MmColors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(MmSpacing.xl))
        ExtendedFloatingActionButton(
            onClick = onRetry,
            backgroundColor = MmColors.accentFill,
            contentColor = MmColors.onAccent,
            shape = RoundedCornerShape(999.dp),
            text = { Text(actionLabel, style = MmType.label, color = MmColors.onAccent) },
            icon = { Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MmColors.onAccent) }
        )
    }
}
