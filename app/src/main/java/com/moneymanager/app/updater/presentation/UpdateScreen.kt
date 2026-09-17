package com.moneymanager.app.updater.presentation

import com.moneymanager.app.ui.theme.MmColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.domain.ApkInstaller
import com.moneymanager.app.updater.model.UpdateError
import com.moneymanager.app.updater.model.UpdateState
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.settings.SimpleTopBar
import com.moneymanager.app.ui.theme.MMGreenDark

@Composable
fun AppUpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var installNote by remember { mutableStateOf<String?>(null) }
    val installer = remember { ApkInstaller(context.applicationContext) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.verifyPostInstall()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("App Updates", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MmCard {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (icon, tint) = when (state) {
                        is UpdateState.UpToDate -> Icons.Filled.CheckCircle to MmColors.income
                        is UpdateState.UpdateAvailable, is UpdateState.Downloaded -> Icons.Filled.SystemUpdate to MmColors.accent
                        is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> Icons.Filled.CloudDownload to MmColors.accent
                        UpdateState.Offline, is UpdateState.Error -> Icons.Filled.CloudOff to MmColors.expense
                        UpdateState.Idle -> Icons.Filled.Verified to MmColors.accent
                    }
                    MmIconBadge(icon = icon, tint = tint, size = 68.dp)
                    Spacer(Modifier.height(14.dp))
                    when (val current = state) {
                        UpdateState.Idle -> {
                            Text("App Updates", style = MaterialTheme.typography.h6)
                            Text("Current version ${BuildConfig.VERSION_NAME}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        UpdateState.Checking -> {
                            Text("Checking for updates", style = MaterialTheme.typography.h6)
                            Text("Looking for the latest Money Manager release…", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            CircularProgressIndicator(color = MmColors.accent, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                        is UpdateState.UpToDate -> {
                            Text("You're up to date", style = MaterialTheme.typography.h6)
                            Text("Money Manager ${current.currentVersionName}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = viewModel::checkForUpdates, colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)) { Text("Check again", color = MmColors.onAccent) }
                        }
                        is UpdateState.UpdateAvailable -> {
                            Text("Update available", style = MaterialTheme.typography.h6)
                            Text("${current.currentVersionName}  →  ${current.update.versionName}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            if (current.update.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Column(Modifier.fillMaxWidth().background(MmColors.surfaceMuted, MaterialTheme.shapes.medium).padding(12.dp)) {
                                    Text("What's new", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(current.update.releaseNotes.take(1400), color = MmColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { viewModel.download(current.update) }, colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)) { Text("Download update", color = MmColors.onAccent) }
                        }
                        is UpdateState.Downloading -> {
                            Text("Downloading update", style = MaterialTheme.typography.h6)
                            Text(current.update.versionName, color = MmColors.textSecondary)
                            Spacer(Modifier.height(14.dp))
                            if (current.progress >= 0) LinearProgressIndicator(progress = current.progress / 100f, color = MmColors.accent, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(color = MmColors.accent, modifier = Modifier.fillMaxWidth())
                            Text(if (current.progress >= 0) "${current.progress}%" else "Downloading…", color = MmColors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                        }
                        is UpdateState.Downloaded -> {
                            Text("Update ready", style = MaterialTheme.typography.h6)
                            Text("${current.update.versionName} is ready to install.", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (!installer.canInstallPackages()) {
                                        installer.openInstallPermissionSettings()
                                        installNote = "Allow Money Manager to install updates, then return here and tap Install again."
                                    } else {
                                        val file = viewModel.pendingFile()
                                        if (file == null) {
                                            installNote = "The downloaded update is no longer available. Download it again."
                                        } else {
                                            viewModel.markInstalling()
                                            installer.install(file).onFailure {
                                                installNote = it.message ?: "Could not start the installer"
                                                viewModel.checkForUpdates()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                            ) { Text("Install update", color = MmColors.onAccent) }
                        }
                        UpdateState.Installing -> {
                            Text("Installing update", style = MaterialTheme.typography.h6)
                            Text("Complete the Android installation prompt. Money Manager will verify the new version when you return.", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(color = MmColors.accent, modifier = Modifier.size(26.dp))
                        }
                        UpdateState.Offline -> {
                            Text("No internet connection", style = MaterialTheme.typography.h6)
                            Text("Connect to the internet to check for updates.", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = viewModel::checkForUpdates, colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)) { Text("Try again", color = MmColors.onAccent) }
                        }
                        is UpdateState.Error -> {
                            Text(errorTitle(current.error), style = MaterialTheme.typography.h6)
                            Text(errorMessage(current.error), color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = viewModel::checkForUpdates, colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)) { Text("Try again", color = MmColors.onAccent) }
                        }
                    }
                    installNote?.let { Text(it, color = MmColors.warning, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
                }
            }

            MmCard {
                MmSwitchRow(
                    title = "Automatic update checks",
                    subtitle = "Check GitHub for new stable releases in the background.",
                    checked = viewModel.autoUpdateEnabled(),
                    onCheckedChange = viewModel::setAutoUpdateEnabled
                )
            }

            if (BuildConfig.DEBUG) {
                MmCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Developer diagnostics", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        DiagnosticRow("Repository", diagnostics.repository)
                        DiagnosticRow("Endpoint", diagnostics.endpoint)
                        DiagnosticRow("Stage", diagnostics.stage ?: "—")
                        DiagnosticRow("HTTP", diagnostics.httpStatus?.toString() ?: "—")
                        DiagnosticRow("Release", diagnostics.releaseTag ?: "—")
                        DiagnosticRow("APK", diagnostics.detectedApk ?: "—")
                        DiagnosticRow("Exception", diagnostics.exceptionType ?: "—")
                        DiagnosticRow("Message", diagnostics.exceptionMessage ?: "—")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, fontSize = 11.sp, color = MmColors.textSecondary, modifier = Modifier.width(92.dp))
        Text(value, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

private fun errorTitle(error: UpdateError): String = when (error) {
    UpdateError.NoInternet -> "No internet connection"
    UpdateError.DnsFailure -> "Couldn't reach GitHub"
    UpdateError.ConnectionTimeout, UpdateError.ReadTimeout -> "GitHub took too long to respond"
    UpdateError.TlsFailure -> "Secure connection failed"
    UpdateError.HttpUnauthorized -> "Update access is not authorized"
    UpdateError.HttpForbidden -> "GitHub refused the request"
    UpdateError.HttpNotFound -> "Update source not found"
    UpdateError.HttpRateLimited -> "GitHub temporarily limited checks"
    is UpdateError.HttpServerError -> "GitHub is temporarily unavailable"
    UpdateError.InvalidJson -> "Invalid update information"
    UpdateError.NoRelease -> "No published update is available"
    UpdateError.NoApk -> "No compatible APK was found"
    UpdateError.InvalidVersion -> "Invalid release version"
    UpdateError.DownloadFailed -> "Update download failed"
    UpdateError.VerificationFailed -> "Update verification failed"
    UpdateError.InstallationFailed -> "Update installation failed"
    is UpdateError.Unknown -> "Couldn't check for updates"
}

private fun errorMessage(error: UpdateError): String = when (error) {
    UpdateError.NoInternet -> "Connect to the internet and try again."
    UpdateError.DnsFailure -> "The update server could not be resolved."
    UpdateError.ConnectionTimeout, UpdateError.ReadTimeout -> "GitHub did not respond in time. Check your connection and try again."
    UpdateError.TlsFailure -> "A secure connection to GitHub could not be established."
    UpdateError.HttpUnauthorized -> "The configured update source requires authorization."
    UpdateError.HttpForbidden -> "GitHub rejected the request."
    UpdateError.HttpNotFound -> "The configured repository or release endpoint was not found."
    UpdateError.HttpRateLimited -> "Wait a while and try the update check again."
    is UpdateError.HttpServerError -> "GitHub returned server error ${error.code}. Try again later."
    UpdateError.InvalidJson -> "GitHub returned data the app could not understand."
    UpdateError.NoRelease -> "There is no published stable release to install yet."
    UpdateError.NoApk -> "The latest stable release does not contain the expected Money Manager APK."
    UpdateError.InvalidVersion -> "The published release has an invalid version tag."
    UpdateError.DownloadFailed -> "The APK could not be downloaded completely."
    UpdateError.VerificationFailed -> "The downloaded APK did not pass package, version, signature, or checksum checks."
    UpdateError.InstallationFailed -> "Android could not start or complete the APK installation."
    is UpdateError.Unknown -> error.detail ?: "Try again in a moment."
}
