package com.moneymanager.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.security.PinHasher
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Sets (or changes) the app-lock PIN. The PIN is never stored in plain text - [PinHasher] turns it
 * into a salted PBKDF2 digest before it is persisted.
 */
@Composable
fun PinSetupDialog(
    isChange: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
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
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(it, style = MmType.caption, color = MmColors.expense)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !PinHasher.isValidPin(pin) -> error = "Use 4 to 8 digits"
                    pin != confirm -> error = "The two PINs don't match"
                    else -> onSubmit(pin)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Full-screen unlock prompt. Shown instead of the app content until the correct PIN is entered.
 * Every failed attempt is counted and the failure is reported inline - no silent no-op.
 */
@Composable
fun PinUnlockScreen(
    onUnlock: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableStateOf(0) }

    fun submit() {
        if (pin.length < 4) {
            error = "Enter your PIN"
            return
        }
        if (onUnlock(pin)) {
            error = null
        } else {
            attempts += 1
            pin = ""
            error = "Incorrect PIN"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MmColors.background)
            .imePadding()
            .padding(MmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MmIconBadge(icon = Icons.Filled.Lock, tint = MmColors.accent, size = 64.dp)
        Spacer(Modifier.height(MmSpacing.lg))
        Text("Money Manager is locked", style = MmType.screenTitle, color = MmColors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(MmSpacing.xs))
        Text(
            "Enter your PIN to continue",
            style = MmType.caption,
            color = MmColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MmSpacing.xl))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { c -> c.isDigit() }.take(8)
                error = null
            },
            label = { Text("PIN") },
            singleLine = true,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(MmSpacing.xs))
            Text(
                if (attempts > 1) "$error (attempt $attempts)" else error!!,
                style = MmType.caption,
                color = MmColors.expense
            )
        }
        Spacer(Modifier.height(MmSpacing.lg))
        Button(
            onClick = { submit() },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(MmSpacing.radiusRow),
            colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MmColors.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(MmSpacing.sm))
            Text("Unlock", color = MmColors.onAccent, style = MmType.label)
        }
    }
}
