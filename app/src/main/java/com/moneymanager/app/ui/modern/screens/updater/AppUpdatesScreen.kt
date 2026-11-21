package com.moneymanager.app.ui.modern.screens.updater

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.UpdateError
import com.moneymanager.app.updater.model.UpdateState
import com.moneymanager.app.updater.presentation.UpdateViewModel
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmType

@Composable
fun AppUpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: UpdateViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "App updates",
            subtitle = "Release channel & install status",
            onBack = onBack,
            glassChrome = false
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MmCard {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (icon, tint) = when (state) {
                        is UpdateState.UpToDate -> Icons.Filled.CheckCircle to MmColors.income
                        is UpdateState.UpdateAvailable -> Icons.Filled.SystemUpdate to MmColors.accent
                        is UpdateState.Checking -> Icons.Filled.CloudDownload to MmColors.accent
                        UpdateState.Offline, is UpdateState.Error -> Icons.Filled.CloudOff to MmColors.expense
                        UpdateState.Idle -> Icons.Filled.Verified to MmColors.accent
                    }
                    MmIconBadge(icon = icon, tint = tint, size = 68.dp)
                    Spacer(Modifier.height(14.dp))
                    when (val current = state) {
                        UpdateState.Idle -> {
                            Text("App Updates", style = MmType.sectionTitle)
                            Text("Current version ${BuildConfig.VERSION_NAME}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        UpdateState.Checking -> {
                            Text("Checking for updates", style = MmType.sectionTitle)
                            Text("Looking for the latest release…", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            CircularProgressIndicator(color = MmColors.accent, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                        is UpdateState.UpToDate -> {
                            Text("You're up to date", style = MmType.sectionTitle)
                            Text("Money Manager ${current.currentVersionName}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            MmPrimaryButton(text = "Check again", onClick = viewModel::checkForUpdates)
                        }
                        is UpdateState.UpdateAvailable -> {
                            Text("Update available", style = MmType.sectionTitle)
                            Text("${current.currentVersionName}  →  ${current.update.versionName}", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            if (current.update.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Column(Modifier.fillMaxWidth().background(MmColors.surfaceMuted, MaterialTheme.shapes.medium).padding(12.dp)) {
                                    Text("What's new", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(current.update.releaseNotes.take(1400), color = MmColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            MmPrimaryButton(
                                text = "Open release page",
                                onClick = {
                                    val url = com.moneymanager.app.updater.data.UpdateUrlPolicy.releasePageUrl(
                                        BuildConfig.GITHUB_OWNER,
                                        BuildConfig.GITHUB_REPOSITORY,
                                        current.update.tag,
                                    )
                                    if (url != null) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                }
                            )
                            Text(
                                "Download and install the APK from the release page.",
                                color = MmColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        UpdateState.Offline -> {
                            Text("No internet connection", style = MmType.sectionTitle)
                            Text("Connect to the internet to check for updates.", color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            MmPrimaryButton(text = "Try again", onClick = viewModel::checkForUpdates)
                        }
                        is UpdateState.Error -> {
                            Text(errorTitle(current.error), style = MmType.sectionTitle)
                            Text(errorMessage(current.error), color = MmColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(14.dp))
                            MmPrimaryButton(text = "Try again", onClick = viewModel::checkForUpdates)
                        }
                    }
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
        }
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
    UpdateError.UnsafeUrl -> "Unsafe update link"
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
    UpdateError.NoApk -> "The latest stable release does not contain the expected APK."
    UpdateError.UnsafeUrl -> "The release points to a download URL outside GitHub. The download was blocked."
    UpdateError.InvalidVersion -> "The published release has an invalid version tag."
    UpdateError.DownloadFailed -> "The APK could not be downloaded completely."
    UpdateError.VerificationFailed -> "The downloaded APK did not pass package, version, signature, or checksum checks."
    UpdateError.InstallationFailed -> "Android could not start or complete the APK installation."
    is UpdateError.Unknown -> error.detail ?: "Try again in a moment."
}
