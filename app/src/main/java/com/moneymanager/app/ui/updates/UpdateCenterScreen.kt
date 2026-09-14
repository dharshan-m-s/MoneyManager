package com.moneymanager.app.ui.updates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.filled.PriorityHigh
import com.moneymanager.app.data.UpdaterAccess
import com.moneymanager.app.data.updater.UpdateInfo
import com.moneymanager.app.data.updater.UpdateUiState
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMBlue
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMRedExpense
import java.util.concurrent.TimeUnit

/**
 * App Updates: the in-app Update Center. Follows the existing Settings aesthetic,
 * honors Light + Dark mode and accessibility (large touch targets, readable text).
 * No browser/GitHub navigation from here.
 */
@Composable
fun UpdateCenterScreen(
    onBack: () -> Unit,
    viewModel: UpdateCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lastChecked by viewModel.lastChecked.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.onScreenOpen() }

    // Reconcile state on every resume (notably right after the Android installer).
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onScreenResume()
        }
    }

    // ---- Install-permission flow ----
    var pendingInstall by remember { mutableStateOf<Pair<UpdateInfo, String>?>(null) }
    var askPermission by remember { mutableStateOf(false) }
    val allowLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Returning from "Install unknown apps" settings — retry automatically if granted.
        pendingInstall?.let { (info, path) ->
            if (UpdaterAccess.canRequestInstalls(appContext)) {
                pendingInstall = null
                viewModel.installUpdate(info, path)
            } else {
                askPermission = true
            }
        }
    }

    fun requestInstall(info: UpdateInfo, path: String) {
        if (UpdaterAccess.canRequestInstalls(appContext)) {
            viewModel.installUpdate(info, path)
        } else {
            pendingInstall = info to path
            askPermission = true
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(title = "App Updates", onBack = onBack)

        when (val s = state) {
            is UpdateUiState.Idle -> UpdateCenterHero(
                icon = Icons.Filled.CloudQueue,
                iconTint = MaterialTheme.colors.primary,
                title = "Preparing…",
                subtitle = "Getting ready to look for updates"
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }

            is UpdateUiState.Checking -> UpdateCenterHero(
                icon = Icons.Filled.CloudQueue,
                iconTint = MaterialTheme.colors.primary,
                title = "Checking for updates…",
                subtitle = lastChecked?.let { "Last checked ${relativeTime(it)}" } ?: "Looking for the latest release"
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }

            is UpdateUiState.UpToDate -> UpToDateContent(
                installedVersionName = viewModel.installedVersionName,
                lastChecked = lastChecked,
                onCheckAgain = viewModel::checkForUpdate
            )

            is UpdateUiState.UpdateAvailable -> UpdateAvailableContent(
                info = s.info,
                installedVersionName = s.installedVersionName,
                lastChecked = lastChecked,
                onUpdate = { viewModel.downloadAndInstall(s.info) },
                onCheckAgain = viewModel::checkForUpdate
            )

            is UpdateUiState.Downloading -> DownloadingContent(info = s.info, state = s, onCancel = {
                viewModel.cancelDownload()
            })

            is UpdateUiState.Verifying -> UpdateCenterHero(
                icon = Icons.Filled.Refresh,
                iconTint = MaterialTheme.colors.primary,
                title = "Verifying update",
                subtitle = "Checking checksum and signature…"
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }

            is UpdateUiState.ReadyToInstall -> ReadyToInstallContent(
                info = s.info,
                installedVersionName = viewModel.installedVersionName,
                onInstall = { requestInstall(s.info, s.localFilePath) }
            )

            is UpdateUiState.Installing -> UpdateCenterHero(
                icon = Icons.Filled.CheckCircle,
                iconTint = MaterialTheme.colors.primary,
                title = "Installing…",
                subtitle = "Android will confirm the installation"
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }

            is UpdateUiState.DownloadFailed -> ErrorContent(
                icon = Icons.Filled.CloudOff,
                title = "Download failed",
                message = s.message,
                retryLabel = "Try again",
                onRetry = { viewModel.downloadAndInstall(s.info) }
            )

            is UpdateUiState.VerificationFailed -> ErrorContent(
                icon = Icons.Filled.ErrorOutline,
                title = "Verification failed",
                message = s.message,
                retryLabel = "Try again",
                onRetry = { viewModel.downloadAndInstall(s.info) }
            )

            is UpdateUiState.Cancelled -> ErrorContent(
                icon = Icons.Filled.Warning,
                title = "Installation cancelled",
                message = "The update was not installed. You can try again at any time.",
                retryLabel = "Try again",
                onRetry = { viewModel.downloadAndInstall(s.info) }
            )

            is UpdateUiState.UpdateFailed -> ErrorContent(
                icon = Icons.Filled.CloudOff,
                title = "Update unavailable",
                message = s.message,
                retryLabel = "Check again",
                onRetry = viewModel::checkForUpdate
            )
        }
    }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text("Installation permission required", fontWeight = FontWeight.SemiBold) },
            text = { Text("Android requires permission to install this update.", fontSize = 14.sp) },
            confirmButton = {
                Button(onClick = {
                    askPermission = false
                    allowLauncher.launch(UpdaterAccess.installSettingsIntent(appContext))
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askPermission = false
                    pendingInstall = null
                }) { Text("Cancel") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Content blocks
// ---------------------------------------------------------------------------

@Composable
private fun UpdateCenterHero(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 14.sp, color = MMGrayText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        trailing()
    }
}

@Composable
private fun UpToDateContent(
    installedVersionName: String,
    lastChecked: Long?,
    onCheckAgain: () -> Unit
) {
    UpdateCenterHero(
        icon = Icons.Filled.CheckCircle,
        iconTint = MMGreen,
        title = "You're up to date",
        subtitle = "Current version  v$installedVersionName\nLast checked  ${relativeTime(lastChecked)}"
    ) {
        OutlinedButton(onClick = onCheckAgain) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Check again")
        }
    }
}

@Composable
private fun UpdateAvailableContent(
    info: UpdateInfo,
    installedVersionName: String,
    lastChecked: Long?,
    onUpdate: () -> Unit,
    onCheckAgain: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Update available", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Version  v${info.versionName}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Current version  v$installedVersionName",
            fontSize = 13.sp,
            color = MMGrayText,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (info.mandatory) {
            MandatoryBanner()
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = MaterialTheme.shapes.large,
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MMGrayDivider.copy(alpha = 0.72f))
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("What's new", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(8.dp))
                BulletList(info.whatIsNew)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DownloadSizeCard(modifier = Modifier.weight(1f), icon = Icons.Filled.CloudDownload, label = "Download size", value = formatBytes(info.downloadSizeBytes))
            DownloadSizeCard(modifier = Modifier.weight(1f), icon = Icons.Filled.Refresh, label = "Last checked", value = relativeTime(lastChecked))
        }

        Button(
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
        ) {
            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Update now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        TextButton(onClick = onCheckAgain, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
            Text("Check again", color = MaterialTheme.colors.primary)
        }
    }
}

@Composable
private fun MandatoryBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        backgroundColor = MMAmberDue.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MMAmberDue.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PriorityHigh, null, tint = MMAmberDue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "This is a recommended update. Install it when convenient – the app keeps working meanwhile.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DownloadingContent(info: UpdateInfo, state: UpdateUiState.Downloading, onCancel: () -> Unit) {
    val fraction = state.fraction.coerceIn(0f, 1f)
    val pct = (fraction * 100).toInt().coerceIn(0, 100)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(84.dp).background(MaterialTheme.colors.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$pct%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text("Downloading update", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("v${info.versionName}", fontSize = 14.sp, color = MMGrayText)
        Spacer(Modifier.height(22.dp))

        // Progress visual: block bar ██████████░░░░░ style
        val blocks = (fraction * 20).toInt().coerceIn(0, 20)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(20) { i ->
                Box(
                    Modifier
                        .size(width = 10.dp, height = 14.dp)
                        .background(
                            if (i < blocks) MaterialTheme.colors.primary else MMGrayDivider.copy(alpha = 0.7f),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.primary,
            backgroundColor = MMGrayDivider.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${formatBytes(state.bytesRead)} / ${formatBytes(state.bytesTotal)}",
            fontSize = 13.sp,
            color = MMGrayText
        )
        Spacer(Modifier.height(22.dp))
        TextButton(onClick = onCancel) { Text("Cancel", color = MMGrayText) }
    }
}

@Composable
private fun ReadyToInstallContent(
    info: UpdateInfo,
    installedVersionName: String,
    onInstall: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(84.dp).background(MMGreen.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = MMGreen, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Ready to install", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("v${info.versionName} • verified", fontSize = 14.sp, color = MMGrayText)
        Spacer(Modifier.height(6.dp))
        Text("Checksum and signature matched the official release.", fontSize = 13.sp, color = MMGrayText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
        ) {
            Text("Install", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErrorContent(
    icon: ImageVector,
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit
) {
    UpdateCenterHero(
        icon = icon,
        iconTint = MMRedExpense,
        title = title,
        subtitle = message
    ) {
        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MMGreenDark)
        ) {
            Text(retryLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BulletList(text: String) {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (lines.isEmpty()) {
            Text("Update released.", fontSize = 13.sp, color = MMGrayText)
        } else {
            lines.forEach { line ->
                Row {
                    Text("•  ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Text(line, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DownloadSizeCard(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MMGrayDivider.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = MMGrayText)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}

fun relativeTime(timestamp: Long?): String {
    if (timestamp == null) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
    }
}
