package com.moneymanager.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymanager.app.security.PinHasher
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.mmBrandWash
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sets (or changes) the app-lock PIN. The PIN is never stored in plain text - [PinHasher] turns it
 * into a salted PBKDF2 digest before it is persisted.
 */
@Composable
fun PinSetupDialog(
    isChange: Boolean,
    onDismiss: () -> Unit,
    // Runs off the main thread (PBKDF2 is deliberately slow). Returns false if the PIN was
    // rejected by the store; the dialog stays open and shows an error.
    onSubmit: suspend (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isChange) "Change PIN" else "Set a PIN") },
        text = {
            Column {
                Text(
                    "Choose 4 to 8 digits. You'll enter this PIN whenever the app has been in the " +
                        "background for more than 30 seconds.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.md))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    visualTransformation =
                        if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (reveal) "Hide PIN" else "Show PIN",
                                tint = MmColors.textSecondary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MmSpacing.sm))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { value ->
                        confirm = value.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    visualTransformation =
                        if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(it, style = MmType.caption, color = MmColors.expense)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    when {
                        !PinHasher.isValidPin(pin) -> error = "Use 4 to 8 digits"
                        pin != confirm -> error = "The two PINs don't match"
                        else -> {
                            saving = true
                            val value = pin
                            scope.launch {
                                // onSubmit owns the off-thread hop for the (slow) PBKDF2 hash and
                                // any UI-safe messaging, so this stays on the main dispatcher.
                                val ok = onSubmit(value)
                                saving = false
                                if (!ok) error = "Could not save the PIN"
                            }
                        }
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Full-screen unlock prompt shown instead of the app content until the correct PIN is entered.
 *
 * The layout follows the platform lock screen: identity header near the top, the length-matched
 * indicator directly above the keypad, and the keypad anchored to the bottom of the screen where
 * thumbs rest. Keycap digits are always readable; the reveal toggle switches the entered code
 * between dots and real digits. The indicator shows exactly as many slots as the configured PIN
 * has digits, so a 4-digit code never looks like it demands 8. Verification runs off the main
 * thread with a visible "Checking…" state, since PBKDF2 is deliberately slow.
 */
@Composable
fun PinUnlockScreen(
    // Invoked on Dispatchers.Default and may block (PBKDF2): implementations must not touch UI.
    onUnlock: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
    subtitle: String = "Enter your PIN to continue",
    expectedLength: Int = 4,
    // Failed-attempt policy, owned by the caller so it can persist across process restarts.
    isLockedOut: () -> Boolean = { false },
    lockoutRemainingMillis: () -> Long = { 0L },
    onFailure: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }
    var showPin by remember { mutableStateOf(false) }
    // True while the (deliberately slow, PBKDF2-backed) verification runs off the main thread.
    var verifying by remember { mutableStateOf(false) }
    // Cooldown countdown, mirrored into Compose state so the keypad can disable itself.
    var lockoutRemaining by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val targetLength = expectedLength.coerceIn(4, 8)

    // Tick the countdown while a cooldown is armed, and re-check on first composition.
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = lockoutRemainingMillis()
            lockoutRemaining = remaining
            if (remaining <= 0L) {
                if (isLockedOut()) {
                    // Still within the window computed elsewhere; keep polling briefly.
                } else {
                    // Cool-down over: clear the stale message so the keypad is usable again.
                    if (error?.startsWith("Too many") == true) error = null
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val lockedOut = lockoutRemaining > 0L || isLockedOut()

    fun submit() {
        if (verifying) return
        if (isLockedOut() || lockoutRemaining > 0L) {
            error = "Too many attempts"
            return
        }
        if (pin.length < 4) {
            error = "Enter your PIN"
            return
        }
        verifying = true
        val entered = pin
        scope.launch {
            // PBKDF2 is CPU-bound: run it on the default pool so the UI never freezes while
            // the digest is being computed and compared.
            val ok = withContext(Dispatchers.Default) { onUnlock(entered) }
            verifying = false
            if (ok) {
                error = null
                onSuccess()
            } else {
                attempts += 1
                pin = ""
                onFailure()
                val remaining = lockoutRemainingMillis()
                lockoutRemaining = remaining
                error = if (remaining > 0L) "Too many attempts" else "Incorrect PIN"
            }
        }
    }

    fun onDigit(digit: Char) {
        if (verifying || pin.length >= targetLength) return
        error = null
        pin += digit
        // The PIN's exact length is known, so the last digit submits on its own - no confirm
        // button needed, same as the system lock screen.
        if (pin.length == targetLength) submit()
    }

    fun onBackspace() {
        if (verifying) return
        error = null
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .mmBrandWash()
            .statusBarsPadding()
            .padding(horizontal = MmSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Identity block: comfortable distance from the top, not dead-centre.
        Spacer(Modifier.weight(0.75f))
        MmIconBadge(icon = Icons.Filled.Lock, tint = MmColors.accent, size = 64.dp)
        Spacer(Modifier.height(MmSpacing.lg))
        Text(
            "Money Manager is locked",
            style = MmType.screenTitle,
            color = MmColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MmSpacing.xs))
        Text(
            subtitle,
            style = MmType.caption,
            color = MmColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MmSpacing.xl))

        // The entered code: dots by default, real digits when revealed via the eye key. One slot
        // per configured digit, red after a failed try, in a fixed-height slot so nothing jumps.
        val errorTint = MmColors.expense
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            if (showPin) {
                Text(
                    pin.ifEmpty { " " },
                    style = MmType.screenTitle,
                    color = if (error != null) errorTint else MmColors.textPrimary
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                    repeat(targetLength) { index ->
                        val filled = index < pin.length
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        filled && error != null -> errorTint
                                        filled -> MmColors.accent
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (filled) Modifier
                                    else Modifier.background(MmColors.outline, CircleShape)
                                )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(MmSpacing.sm))
        // Fixed-height status line so nothing shifts when the message changes.
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            when {
                verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = MmColors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text("Checking…", style = MmType.caption, color = MmColors.textSecondary)
                }
                lockedOut -> Text(
                    "Too many attempts · retry in ${(lockoutRemaining / 1000) + 1}s",
                    style = MmType.caption,
                    color = errorTint
                )
                error != null && attempts > 1 -> Text(
                    "$error · attempt $attempts",
                    style = MmType.caption,
                    color = errorTint
                )
                error != null -> Text(error!!, style = MmType.caption, color = errorTint)
                attempts >= 3 -> Text(
                    "Forgot it? Unlock with the recovery path, or reset in Settings.",
                    style = MmType.caption,
                    color = MmColors.textTertiary
                )
            }
        }

        Spacer(Modifier.weight(0.9f))

        // Keypad anchored to the bottom of the screen (lock-screen placement), width-capped and
        // centred so it never hugs one side on wide screens or tablets.
        PinKeypad(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(bottom = MmSpacing.xxl),
            showPin = showPin,
            pinLength = pin.length,
            enabled = !verifying && !lockedOut,
            onDigit = ::onDigit,
            onBackspace = ::onBackspace,
            onToggleReveal = { showPin = !showPin }
        )
    }
}

@Composable
private fun PinKeypad(
    showPin: Boolean,
    pinLength: Int,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(REVEAL_KEY, '0', BACKSPACE_KEY)
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
            ) {
                row.forEach { key ->
                    when (key) {
                        REVEAL_KEY -> KeyCap(onClick = onToggleReveal, enabled = enabled) {
                            Icon(
                                if (showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPin) "Hide PIN" else "Show PIN",
                                tint = MmColors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        BACKSPACE_KEY ->
                            KeyCap(onClick = onBackspace, enabled = enabled && pinLength > 0) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete last digit",
                                    tint = MmColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        else -> KeyCap(onClick = { onDigit(key) }, enabled = enabled) {
                            // Keycap digits are always readable; the eye key switches the
                            // entered-code display between dots and digits.
                            Text(
                                key.toString(),
                                style = MmType.screenTitle,
                                color = MmColors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One keypad key. Cells flex to equal widths across the keypad, giving a full-width grid that
 * stays centred on any screen size.
 */
@Composable
private fun RowScope.KeyCap(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .background(if (enabled) MmColors.surface else MmColors.surfaceMuted)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private const val REVEAL_KEY = 'R'
private const val BACKSPACE_KEY = 'B'
