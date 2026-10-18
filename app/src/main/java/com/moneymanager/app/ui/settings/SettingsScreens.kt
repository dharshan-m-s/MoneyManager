package com.moneymanager.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.security.AppLock
import com.moneymanager.app.security.LockMethod
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.home.HomePreferences
import com.moneymanager.app.ui.security.PinSetupDialog
import com.moneymanager.app.ui.security.SystemLockPrompt
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Kept for the updater screen, which shares this chrome. */
@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    MoneyManagerTopBar(title = title, onBack = onBack, actions = actions)
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MmType.label,
        fontWeight = FontWeight.SemiBold,
        color = MmColors.accent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MmSpacing.screen, end = MmSpacing.screen, top = MmSpacing.lg, bottom = MmSpacing.sm)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        onClick = onClick,
        contentPadding = PaddingValues(MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                MmIconBadge(icon = icon, tint = MmColors.accent, size = 40.dp)
                Spacer(Modifier.width(MmSpacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MmType.body, color = MmColors.textPrimary)
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: (@Composable () -> Unit)? = null
) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        contentPadding = PaddingValues(horizontal = MmSpacing.md, vertical = MmSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = icon, tint = MmColors.accent, size = 40.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Box(Modifier.weight(1f)) {
                MmSwitchRow(
                    title = title,
                    subtitle = subtitle,
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    badge = badge
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportStatement: () -> Unit,
    onBudget: () -> Unit = {},
    onAppUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val backupManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackupEntryPoint::class.java
        ).backupManager()
    }
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
    }
    val homePreferences: HomePreferences = remember { entryPoint.homePreferences() }
    val appLock: AppLock = remember { entryPoint.appLock() }

    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    var backupFolder by remember { mutableStateOf(backupManager.configuredTreeUri()) }
    var backupBusy by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf(backupManager.lastSuccessfulBackup.value) }

    val showIncome by homePreferences.showIncome.collectAsState()
    val showCashSummary by homePreferences.showCashSummary.collectAsState()
    val hideAmounts by homePreferences.hideAmounts.collectAsState()
    var darkMode by remember { mutableStateOf(homePreferences.isDarkMode()) }
    var autoBackup by remember { mutableStateOf(backupManager.autoBackupEnabled()) }
    var lockEnabled by remember { mutableStateOf(appLock.isEnabled()) }
    var lockMethod by remember { mutableStateOf(appLock.method()) }
    var systemLockAvailable by remember { mutableStateOf(appLock.canUseSystemLock()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showMethodPicker by remember { mutableStateOf(false) }
    // Set while the system screen-lock prompt is deciding whether the system method may be used.
    var pendingSystemVerify by remember { mutableStateOf(false) }
    // Non-null when the open PIN dialog is part of switching to the in-app PIN method, rather
    // than just changing or adding the recovery code.
    var pendingPinMethod by remember { mutableStateOf<LockMethod?>(null) }
    // True while the "turn app lock off?" confirmation is up, so the switch stays visually on
    // until the user confirms.
    var switchArmed by remember { mutableStateOf(false) }
    // Non-null while the "change how you unlock?" confirmation is up.
    var confirmSwitchTo by remember { mutableStateOf<LockMethod?>(null) }

    fun message(text: String) {
        scope.launch { snackbarState.showSnackbar(text) }
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching { backupManager.setTreeUri(uri) }
                .onFailure { message(it.message ?: "Could not use that folder") }
                .onSuccess {
                    backupFolder = uri
                    backupBusy = true
                    scope.launch {
                        backupManager.backupNow()
                            .onFailure { message(it.message ?: "Backup failed") }
                            .onSuccess {
                                lastBackup = backupManager.lastSuccessfulBackup.value
                                message("Backup created in the selected folder")
                            }
                        backupBusy = false
                    }
                }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupManager.exportCsvToUri(uri)
                    .onFailure { message(it.message ?: "CSV export failed") }
                    .onSuccess { message("CSV exported successfully") }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MmColors.background)) {
        Column(Modifier.fillMaxSize()) {
            MoneyManagerTopBar(title = "Settings", onBack = onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = MmSpacing.xxl)
            ) {
                SettingsSectionHeader("Money & planning")
                SettingsRow(
                    title = "Monthly budget",
                    subtitle = "Set your personal spending limit for the month",
                    icon = Icons.Filled.MonetizationOn,
                    onClick = onBudget
                )

                SettingsSectionHeader("Home screen")
                SettingsSwitchRow(
                    title = "Show income",
                    subtitle = "Include this month's income in the dashboard summary",
                    icon = Icons.Filled.Share,
                    checked = showIncome,
                    onCheckedChange = homePreferences::setShowIncome
                )
                SettingsSwitchRow(
                    title = "Show cash wallet",
                    subtitle = "Show the cash card and its monthly activity",
                    icon = Icons.Filled.MonetizationOn,
                    checked = showCashSummary,
                    onCheckedChange = homePreferences::setShowCashSummary
                )
                SettingsSwitchRow(
                    title = "Hide amounts by default",
                    subtitle = "Balances and totals stay masked until you tap to reveal them",
                    icon = Icons.Filled.VisibilityOff,
                    checked = hideAmounts,
                    onCheckedChange = homePreferences::setHideAmounts
                )

                SettingsSectionHeader("Security")
                SettingsSwitchRow(
                    title = "App lock",
                    subtitle = if (lockEnabled) {
                        when (lockMethod) {
                            LockMethod.IN_APP_PIN ->
                                "On · unlock with your in-app PIN after 30s in the background"
                            LockMethod.SYSTEM_BIOMETRIC ->
                                "On · unlock with your fingerprint, face or device PIN"
                        }
                    } else "Protect the app with a PIN or your device screen lock",
                    icon = Icons.Filled.Lock,
                    checked = lockEnabled || switchArmed,
                    badge = if (lockEnabled) {
                        {
                            MmPill(
                                text = if (lockMethod == LockMethod.IN_APP_PIN) "PIN" else "System",
                                tint = MmColors.income,
                                filled = true
                            )
                        }
                    } else null,
                    onCheckedChange = { enabled ->
                        when {
                            enabled -> {
                                // The method choice always comes first: the PIN dialog only follows
                                // when the in-app PIN method is picked, and the system method
                                // verifies through Android's own screen-lock prompt instead.
                                showMethodPicker = true
                            }
                            // Turning the lock off is confirmed so a stray tap can't strip
                            // protection from the whole app.
                            lockEnabled -> switchArmed = true
                            else -> Unit
                        }
                    }
                )
                if (lockEnabled) {
                    // Same row language as every other Settings panel: one MmCard row with a
                    // 40dp badge, title + subtitle, no radio buttons or standout cards.
                    SettingsRow(
                        title = "Unlock with",
                        subtitle = when (lockMethod) {
                            LockMethod.IN_APP_PIN -> "In-app PIN"
                            LockMethod.SYSTEM_BIOMETRIC ->
                                if (systemLockAvailable) "System screen lock (biometrics or device PIN)"
                                else "System screen lock (needs a device screen lock)"
                        },
                        icon = when (lockMethod) {
                            LockMethod.IN_APP_PIN -> Icons.Filled.Password
                            LockMethod.SYSTEM_BIOMETRIC -> Icons.Filled.Fingerprint
                        },
                        onClick = { showMethodPicker = true }
                    )
                    SettingsRow(
                        title = if (appLock.hasPin()) "Change PIN" else "Add recovery PIN",
                        subtitle = when {
                            lockMethod == LockMethod.SYSTEM_BIOMETRIC && !appLock.hasPin() ->
                                "Backup code in case biometrics fail or the screen lock is unavailable"
                            lockMethod == LockMethod.SYSTEM_BIOMETRIC ->
                                "Backup code in case the screen lock is unavailable"
                            else -> "Set a new PIN for this device"
                        },
                        icon = Icons.Filled.Security,
                        onClick = { showPinDialog = true }
                    )
                }

                SettingsSectionHeader("Backup & restore")
                MmCard(modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MmIconBadge(icon = Icons.Filled.CloudUpload, tint = MmColors.accent, size = 44.dp)
                        Spacer(Modifier.width(MmSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text("Backup & restore", style = MmType.sectionTitle, color = MmColors.textPrimary)
                            Text(
                                "Keeps a database snapshot and a CSV in the folder you choose",
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(MmSpacing.md))
                    Text("Location", style = MmType.caption, color = MmColors.textSecondary)
                    Text(
                        if (backupFolder == null) "Not connected yet"
                        else DocumentFile.fromTreeUri(context, backupFolder!!)?.name ?: "Selected folder",
                        style = MmType.body,
                        color = MmColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(MmSpacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lastBackup == null) {
                            MmPill("No backup yet", tint = MmColors.warning, filled = true)
                        } else {
                            MmPill("Last backup ${prettyStamp(lastBackup)}", tint = MmColors.income, filled = true)
                        }
                    }
                    Spacer(Modifier.height(MmSpacing.md))
                    Button(
                        onClick = {
                            if (backupFolder == null) {
                                backupFolderLauncher.launch(null)
                                return@Button
                            }
                            backupBusy = true
                            scope.launch {
                                backupManager.backupNow()
                                    .onFailure { message(it.message ?: "Backup failed") }
                                    .onSuccess {
                                        lastBackup = backupManager.lastSuccessfulBackup.value
                                        message("Backup created")
                                    }
                                backupBusy = false
                            }
                        },
                        enabled = !backupBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(MmSpacing.radiusRow),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                    ) {
                        if (backupBusy) {
                            CircularProgressIndicator(color = MmColors.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MmColors.onAccent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(MmSpacing.sm))
                        Text(
                            when {
                                backupBusy -> "Creating backup…"
                                backupFolder == null -> "Choose folder & back up"
                                else -> "Back up now"
                            },
                            color = MmColors.onAccent,
                            style = MmType.label
                        )
                    }
                    Spacer(Modifier.height(MmSpacing.sm))
                    OutlinedButton(
                        onClick = { backupFolderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MmSpacing.radiusRow)
                    ) {
                        Text(
                            if (backupFolder == null) "Choose backup folder" else "Change backup folder",
                            style = MmType.label
                        )
                    }
                    Box(Modifier.fillMaxWidth()) {
                        MmSwitchRow(
                            title = "Automatic backup",
                            subtitle = if (backupFolder == null)
                                "Connect a folder to keep it up to date automatically"
                            else
                                "Every change is written to the folder shortly after you make it",
                            checked = autoBackup,
                            onCheckedChange = {
                                autoBackup = it
                                backupManager.setAutoBackupEnabled(it)
                            }
                        )
                    }
                }

                SettingsSectionHeader("Data")
                SettingsRow(
                    title = "Export transactions as CSV",
                    subtitle = "Save your full history to a file you choose",
                    icon = Icons.Filled.Description,
                    onClick = { csvExportLauncher.launch("moneymanager_transactions.csv") }
                )
                SettingsRow(
                    title = "Import a statement",
                    subtitle = "Bring in a Moneyview consolidated CSV",
                    icon = Icons.Filled.Download,
                    onClick = onImportStatement
                )

                SettingsSectionHeader("Appearance")
                SettingsSwitchRow(
                    title = "Dark mode",
                    subtitle = "Use the dark theme throughout the app",
                    icon = Icons.Filled.DarkMode,
                    checked = darkMode,
                    onCheckedChange = {
                        darkMode = it
                        homePreferences.setDarkMode(it)
                    }
                )

                SettingsSectionHeader("About")
                SettingsRow(
                    title = "App updates",
                    subtitle = "Check GitHub for the latest stable release",
                    icon = Icons.Filled.SystemUpdate,
                    onClick = onAppUpdates
                )
                SettingsRow(
                    title = "Version ${BuildConfig.VERSION_NAME}",
                    subtitle = "Build ${BuildConfig.VERSION_CODE}",
                    icon = Icons.Filled.Info
                )
            }
        }

        SnackbarHost(
            hostState = snackbarState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )
    }

    // The method chooser reuses the app's standard bottom sheet, exactly like the account and
    // category pickers, instead of a standout block of radio cards.
    if (showMethodPicker) {
        LockMethodSheet(
            // Nothing counts as selected until the lock is actually enabled.
            selected = if (lockEnabled) lockMethod else null,
            systemLockAvailable = systemLockAvailable,
            onSelect = { method ->
                showMethodPicker = false
                when {
                    method == LockMethod.SYSTEM_BIOMETRIC && !appLock.canUseSystemLock() -> {
                        systemLockAvailable = false
                        message("Set a screen lock on this device first")
                    }
                    // Switching methods while the lock is live is confirmed, so the protection
                    // never changes on a stray tap.
                    lockEnabled && method != lockMethod -> confirmSwitchTo = method
                    method == LockMethod.SYSTEM_BIOMETRIC -> pendingSystemVerify = true
                    else -> {
                        pendingPinMethod = LockMethod.IN_APP_PIN
                        showPinDialog = true
                    }
                }
            },
            onDismiss = { showMethodPicker = false }
        )
    }

    // Switching methods while the lock is on is confirmed first; the active method keeps
    // protecting the app until the user finishes the new one's setup.
    confirmSwitchTo?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmSwitchTo = null },
            title = { Text("Change how you unlock?") },
            text = {
                Text(
                    if (target == LockMethod.SYSTEM_BIOMETRIC)
                        "App lock will verify with this device's screen lock. Your in-app PIN " +
                            "stays saved as a recovery code."
                    else
                        "App lock will verify with a 4-8 digit code that only works in this app. " +
                            "The device screen lock stops being used.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSwitchTo = null
                    if (target == LockMethod.SYSTEM_BIOMETRIC) {
                        pendingSystemVerify = true
                    } else {
                        pendingPinMethod = LockMethod.IN_APP_PIN
                        showPinDialog = true
                    }
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSwitchTo = null }) { Text("Cancel") }
            }
        )
    }

    // Turning the lock off is destructive (the saved code is forgotten), so it is confirmed.
    if (switchArmed) {
        AlertDialog(
            onDismissRequest = { switchArmed = false },
            title = { Text("Turn off app lock?") },
            text = {
                Text(
                    "Your ledger will open without asking for verification, and the saved " +
                        "recovery code is forgotten. You can turn the lock back on at any time.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    switchArmed = false
                    lockEnabled = false
                    lockMethod = LockMethod.IN_APP_PIN
                    appLock.disable()
                    message("App lock turned off")
                }) { Text("Turn off", color = MmColors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { switchArmed = false }) { Text("Keep it on") }
            }
        )
    }

    // The system method has no app-side secret to set up: the platform screen-lock prompt is the
    // verification. Success enables the lock directly; dismissing or failing keeps the lock off.
    if (pendingSystemVerify) {
        SystemLockPrompt(
            title = "Confirm to turn on app lock",
            subtitle = "Money Manager will use this device's screen lock",
            negativeButtonText = "Cancel",
            onUnlocked = {
                pendingSystemVerify = false
                appLock.enable(LockMethod.SYSTEM_BIOMETRIC)
                lockMethod = LockMethod.SYSTEM_BIOMETRIC
                lockEnabled = true
                systemLockAvailable = true
                message(
                    if (appLock.hasPin()) "App lock is on · unlock with your screen lock"
                    else "App lock is on · set a recovery PIN as a backup for biometric failures"
                )
            },
            onUnavailable = {
                pendingSystemVerify = false
                if (!lockEnabled) {
                    message("App lock stays off - the screen lock was not confirmed")
                } else {
                    message("Could not verify - still using the previous method")
                }
            }
        )
    }

    if (showPinDialog) {
        PinSetupDialog(
            isChange = pendingPinMethod == null && appLock.hasPin(),
            onDismiss = {
                showPinDialog = false
                pendingPinMethod = null
            },
            onSubmit = { pin ->
                if (appLock.setPin(pin)) {
                    pendingPinMethod?.let { method ->
                        appLock.setMethod(method)
                        pendingPinMethod = null
                    }
                    lockMethod = appLock.method()
                    lockEnabled = appLock.isEnabled()
                    showPinDialog = false
                    message(
                        if (lockMethod == LockMethod.SYSTEM_BIOMETRIC)
                            "Recovery PIN saved · app lock stays on your screen lock"
                        else "App lock is on with your PIN"
                    )
                } else {
                    message("Choose 4 to 8 digits")
                }
            }
        )
    }
}

private fun prettyStamp(stamp: String?): String = stamp?.let {
    runCatching {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
            .format(SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(it)!!)
    }.getOrNull() ?: it
} ?: ""

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}"

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(title = "Help & feedback", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            MmCard {
                Text("Something wrong with your numbers?", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Spacer(Modifier.height(MmSpacing.xs))
                Text(
                    "Include the screen you were on, what you expected and what you saw. Never attach " +
                        "your exported CSV or backup file to a public issue - it contains your real " +
                        "financial data.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            SettingsRow(
                title = "Report a bug",
                subtitle = "Opens a new issue on the project's GitHub repository",
                icon = Icons.Filled.BugReport,
                onClick = { openUrl(context, "$repoUrl/issues/new") }
            )
            SettingsRow(
                title = "Browse existing issues",
                subtitle = "Check whether someone already reported it",
                icon = Icons.Filled.Description,
                onClick = { openUrl(context, "$repoUrl/issues") }
            )
            SettingsRow(
                title = "Project repository",
                subtitle = repoUrl,
                icon = Icons.Filled.Code,
                onClick = { openUrl(context, repoUrl) }
            )
        }
    }
}

private data class LicenseEntry(val name: String, val license: String)

private val LICENSES = listOf(
    LicenseEntry("AndroidX / Jetpack Compose", "Apache License 2.0"),
    LicenseEntry("Kotlin & kotlinx.coroutines", "Apache License 2.0"),
    LicenseEntry("Room", "Apache License 2.0"),
    LicenseEntry("Hilt / Dagger", "Apache License 2.0"),
    LicenseEntry("WorkManager", "Apache License 2.0"),
    LicenseEntry("Navigation Compose", "Apache License 2.0"),
    LicenseEntry("OpenCSV", "Apache License 2.0"),
    LicenseEntry("Gson", "Apache License 2.0"),
    LicenseEntry("Timber", "Apache License 2.0"),
    LicenseEntry("JUnit & Robolectric (test only)", "Eclipse Public License 1.0 / MIT")
)

@Composable
fun OpenSourceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}"

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(title = "Open source", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            MmCard {
                Text("Money Manager", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text(
                    "A local-first personal finance app. There is no account, no server and no " +
                        "analytics - your ledger lives in a database on this device.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.md))
                Button(
                    onClick = { openUrl(context, repoUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = MmColors.onAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text("Open the repository", color = MmColors.onAccent, style = MmType.label)
                }
            }

            MmCard {
                Text("Third-party libraries", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Spacer(Modifier.height(MmSpacing.sm))
                LICENSES.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.name, style = MmType.body, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
                        Text(entry.license, style = MmType.caption, color = MmColors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}"

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(title = "About", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            MmCard {
                Text("Money Manager", style = MmType.screenTitle, color = MmColors.textPrimary)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MmColors.income, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(
                        "Local-first: nothing leaves this device unless you export it",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }
            SettingsRow(
                title = "Project repository",
                subtitle = "Source code and documentation",
                icon = Icons.Filled.Code,
                onClick = { openUrl(context, repoUrl) }
            )
            SettingsRow(
                title = "Privacy",
                subtitle = "Transactions, accounts and receipts stay in the app's private storage",
                icon = Icons.Filled.Security
            )
            SettingsRow(
                title = "Licenses",
                subtitle = "Open-source dependencies used by this app",
                icon = Icons.Filled.Description
            )
        }
    }
}
