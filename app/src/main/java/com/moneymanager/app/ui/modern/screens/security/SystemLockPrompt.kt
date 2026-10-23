package com.moneymanager.app.ui.modern.screens.security

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Shows Android's own screen-lock prompt (biometrics or device PIN/pattern/password) exactly once
 * per composition. The system prompt handles enrolment, retries and error UI, so nothing here
 * stores or verifies a secret. Used both by the root lock gate and by Settings when the user
 * turns the system-lock method on (the platform prompt is the setup verification).
 *
 * BiometricPrompt needs a [FragmentActivity] host: MainActivity extends FragmentActivity for this
 * reason. If the host is ever something else, this prompt no-ops safely and reports
 * [onUnavailable] so the caller can fall back to the in-app PIN screen instead of crashing.
 */
@Composable
fun SystemLockPrompt(
    title: String,
    subtitle: String,
    negativeButtonText: String = "Use PIN",
    onUnlocked: () -> Unit,
    onUnavailable: () -> Unit,
    onCancelled: (() -> Unit)? = null
) {
    val activity = LocalActivity.current
    LaunchedEffect(activity) {
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity == null) {
            onUnavailable()
            return@LaunchedEffect
        }
        val manager = BiometricManager.from(fragmentActivity)
        val canAuth = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable()
            return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // A deliberate dismissal (swipe-away or the negative button) is not an
                    // availability problem: callers use onCancelled to fall back to another
                    // unlock path, while real failures (lockout, prompt unusable) report
                    // onUnavailable so the caller can show why.
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (cancelled && onCancelled != null) onCancelled() else onUnavailable()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
