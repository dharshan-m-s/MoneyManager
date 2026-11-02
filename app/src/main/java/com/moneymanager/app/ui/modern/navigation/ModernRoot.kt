package com.moneymanager.app.ui.modern.navigation

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.LocalMmGlassBackdrop
import com.moneymanager.app.ui.components.LocalMmGlassEnabled
import com.moneymanager.app.ui.components.LocalMmSharedTransitionScope
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.layerBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.rememberLayerBackdrop
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.StatusBarGlyphToneAndSystemBarTint
import com.moneymanager.app.ui.components.mmBrandWash
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.modern.screens.security.PinUnlockScreen
import com.moneymanager.app.ui.modern.screens.security.SystemLockPrompt
import com.moneymanager.app.ui.settings.SettingsEntryPoint
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import kotlinx.coroutines.launch

// Primary destinations keep the navigation dock available. Detail and editing
// destinations intentionally leave the dock so the task at hand has the full screen.
private val DOCK_ROUTES = setOf(
    Destination.Dashboard.route,
    Destination.Transactions.route,
    Destination.Accounts.route,
    Destination.Bills.route,
    Destination.Cash.route,
    Destination.Income.route,
    Destination.SpendSummary.route,
    Destination.Budget.route,
    Destination.Categories.route,
    Destination.Reimbursements.route,
    Destination.PersonalLoan.route,
    Destination.Settings.route
)

@OptIn(ExperimentalSharedTransitionApi::class)
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
    var sheetDragOffset by remember { mutableFloatStateOf(0f) }
    val sheetAnimationScope = rememberCoroutineScope()
    var navHostHeightPx by remember { mutableIntStateOf(0) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Dashboard.route
    var overlay by remember { mutableStateOf(DockOverlay.NONE) }
    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        overlay = DockOverlay.NONE
    }
    val dockVisible = currentRoute in DOCK_ROUTES
    val density = LocalDensity.current
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val measuredDockHeight = if (dockHeightPx > 0) {
        with(density) { dockHeightPx.toDp() }
    } else {
        68.dp + navigationInset + 16.dp
    }
    // Keep only a small trailing scroll allowance. The dock is a translucent overlay, not a
    // reserved opaque bottom bar; page content must continue moving visibly beneath it.
    val dockContentReservation = 36.dp

    StatusBarGlyphToneAndSystemBarTint(lightBackground = MaterialTheme.colors.isLight)

    fun openAddTransaction(entryPoint: String) {
        overlay = DockOverlay.NONE
        val isAccountEntry = entryPoint == "ACCOUNT_INCOME" || entryPoint == "ACCOUNT_SPEND"
        if (isAccountEntry) {
            navController.navigate(Destination.AccountSelection.route(entryPoint))
        } else {
            navController.navigate(Destination.AddTransaction.route(entryPoint, null))
        }
    }

    fun closeOverlay() {
        overlay = DockOverlay.NONE
    }

    androidx.activity.compose.BackHandler(enabled = overlay != DockOverlay.NONE) {
        closeOverlay()
    }
    androidx.activity.compose.BackHandler(
        enabled = currentRoute != Destination.Dashboard.route && overlay == DockOverlay.NONE
    ) {
        // One root-level policy covers both tab destinations and detail flows. A valid
        // previous entry is always preferred; orphaned/restored routes return to Dashboard.
        if (!navController.popBackStack()) {
            navController.navigate(Destination.Dashboard.route) { launchSingleTop = true }
        }
    }

    // Capture only the page. The NavHost subtree receives no backdrop reference, so
    // glass descendants cannot sample the layer that is currently recording them.
    // Floating controls are siblings of this capture and may safely sample its finished frame.
    val glassBackdrop = rememberLayerBackdrop()

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalMmSharedTransitionScope provides this
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
                        // Record a subtle, living colour wash beneath the page so the floating dock
                        // samples something richer than a flat black window background.
                        Box(Modifier.fillMaxSize().mmPageBackdrop())
                        CompositionLocalProvider(
                            LocalMmGlassBackdrop provides null,
                            LocalMmGlassEnabled provides false,
                            LocalDockContentPadding provides if (dockVisible) dockContentReservation else 0.dp
                        ) {
                            Box(
                                Modifier.fillMaxSize()
                                    .onSizeChanged { navHostHeightPx = it.height }
                                    .graphicsLayer {
                                        translationY = sheetDragOffset
                                        val travel = (navHostHeightPx * 0.9f).coerceAtLeast(1f)
                                        alpha = 1f - (sheetDragOffset / travel).coerceIn(0f, 0.18f)
                                    }
                            ) {
                                ModernNavHost(
                                    navController = navController,
                                    onOpenMore = {
                                        overlay = if (overlay == DockOverlay.MORE) DockOverlay.NONE else DockOverlay.MORE
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (dockVisible) Modifier else Modifier.navigationBarsPadding())
                                )
                            }
                        }
                    }

                    if (isSheetRoute(currentRoute)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = statusInset + 3.dp)
                                .width(64.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MmColors.surfaceContainer.copy(alpha = 0.82f))
                                .pointerInput(currentRoute, navHostHeightPx) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val tracker = VelocityTracker()
                                        tracker.addPosition(down.uptimeMillis, down.position)
                                        var pressed = true
                                        while (pressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            val delta = change.positionChange().y
                                            if (delta != 0f) {
                                                sheetDragOffset = (sheetDragOffset + delta).coerceAtLeast(0f)
                                                if (delta > 0f || sheetDragOffset > 0f) change.consume()
                                            }
                                            pressed = event.changes.any { it.pressed }
                                        }
                                        val velocity = tracker.calculateVelocity().y
                                        val distance = sheetDragOffset
                                        val dismissDistance = with(density) { 120.dp.toPx() }
                                        val flingDistance = with(density) { 28.dp.toPx() }
                                        val dismiss = distance >= dismissDistance ||
                                            (distance >= flingDistance && velocity >= 900f)
                                        sheetAnimationScope.launch {
                                            val animation = Animatable(distance)
                                            if (dismiss) {
                                                animation.animateTo(
                                                    targetValue = navHostHeightPx.toFloat().coerceAtLeast(1f),
                                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 285f),
                                                    initialVelocity = velocity.coerceAtLeast(0f)
                                                ) { sheetDragOffset = value }
                                                navController.popBackStack()
                                            } else {
                                                animation.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                                                    initialVelocity = velocity
                                                ) { sheetDragOffset = value }
                                            }
                                            sheetDragOffset = 0f
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.width(30.dp).height(4.dp).clip(CircleShape)
                                    .background(MmColors.textTertiary.copy(alpha = 0.62f))
                            )
                        }
                    }

        // Everything below is outside the page capture. These floating surfaces may
        // sample the completed page without creating a page -> glass -> page cycle.
        CompositionLocalProvider(
            LocalMmGlassBackdrop provides glassBackdrop,
            LocalMmGlassEnabled provides true
        ) {
        // The scrim dismisses on an outside tap. It is drawn before the dock and the menus, so
        // the trigger stays tappable (re-tap closes) and the open card is never blocked by a
        // stale full-screen layer.
        if (overlay != DockOverlay.NONE && dockVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { closeOverlay() }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = overlay == DockOverlay.MORE && dockVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = MmSpacing.lg, vertical = 0.dp)
                .padding(bottom = measuredDockHeight + 10.dp),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                androidx.compose.animation.scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 460f)
                ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) +
                androidx.compose.animation.scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 520f)
                )
        ) {
            ModernMorePopover(
                currentRoute = currentRoute,
                onSelect = { destination ->
                    closeOverlay()
                    if (destination.route == navController.currentDestination?.route) {
                        return@ModernMorePopover
                    }
                    navController.navigate(destination.route) {
                        popUpTo(Destination.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = overlay == DockOverlay.ADD && dockVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = MmSpacing.lg)
                .padding(bottom = measuredDockHeight + 8.dp),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) +
                androidx.compose.animation.scaleIn(
                    initialScale = 0.90f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = 440f)
                ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) +
                androidx.compose.animation.scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 520f)
                )
        ) {
            ModernAddActionSurface(
                actions = listOf(
                    AddAction(
                        "Expense",
                        Icons.AutoMirrored.Filled.TrendingDown,
                        MmColors.expense,
                        "Paid from an account"
                    ),
                    AddAction(
                        "Income",
                        Icons.AutoMirrored.Filled.TrendingUp,
                        MmColors.income,
                        "Received into an account"
                    ),
                    AddAction(
                        "Cash expense",
                        Icons.Filled.Payments,
                        MmColors.expense,
                        "Cash you handed over"
                    ),
                    AddAction(
                        "Cash income",
                        Icons.Filled.Savings,
                        MmColors.income,
                        "Cash you received"
                    ),
                    AddAction(
                        "Transfer",
                        Icons.Filled.SwapHoriz,
                        MmColors.transfer,
                        "Move money between accounts"
                    )
                ),
                onSelect = { index ->
                    when (index) {
                        0 -> openAddTransaction("ACCOUNT_SPEND")
                        1 -> openAddTransaction("ACCOUNT_INCOME")
                        2 -> openAddTransaction("CASH_SPEND")
                        3 -> openAddTransaction("CASH_INCOME")
                        4 -> {
                            closeOverlay()
                            navController.navigate(Destination.Transfer.route())
                        }
                    }
                }
            )
        }

        if (dockVisible) {
            ModernDock(
                currentRoute = currentRoute,
                overlay = overlay,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { dockHeightPx = it.height },
                onNavigate = { route ->
                    closeOverlay()
                    if (route == navController.currentDestination?.route) {
                        return@ModernDock
                    }
                    navController.navigate(route) {
                        popUpTo(Destination.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onToggleAdd = {
                    overlay = if (overlay == DockOverlay.ADD) DockOverlay.NONE else DockOverlay.ADD
                },
                onToggleMore = {
                    overlay = if (overlay == DockOverlay.MORE) DockOverlay.NONE else DockOverlay.MORE
                }
            )
        }
        }
        }
        }
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
        MmPrimaryButton(
            text = actionLabel,
            onClick = onRetry,
            icon = Icons.Filled.Fingerprint,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
