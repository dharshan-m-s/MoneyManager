package com.moneymanager.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Password
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.security.LockMethod
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Chooses how the app lock verifies: the in-app 4-8 digit PIN or Android's own screen lock
 * (fingerprint, face or device PIN) through the platform prompt.
 *
 * Rendered as the app's standard bottom sheet, with rows that reuse the exact [SettingsRow]
 * geometry - same MmCard padding, same 40dp badge, same title/subtitle column - so the lock
 * settings look like every other Settings panel instead of a standalone block of radio cards.
 * Selection is marked with the same check the other pickers use, not a radio button.
 */
@Composable
fun LockMethodSheet(
    selected: LockMethod?,
    systemLockAvailable: Boolean,
    onSelect: (LockMethod) -> Unit,
    onDismiss: () -> Unit
) {
    MmBottomSheet(title = "Unlock with", onDismiss = onDismiss) {
        MethodRow(
            title = "In-app PIN",
            subtitle = "A 4-8 digit code only for this app. Works even without a device screen lock.",
            icon = Icons.Filled.Password,
            selected = selected == LockMethod.IN_APP_PIN,
            enabled = true,
            onClick = { onSelect(LockMethod.IN_APP_PIN) }
        )
        MethodRow(
            title = "System screen lock",
            subtitle = if (systemLockAvailable)
                "Unlock with your fingerprint, face or device PIN. Nothing extra to remember."
            else
                "Set a screen lock (PIN, pattern or password) on this device first.",
            icon = Icons.Filled.Fingerprint,
            selected = selected == LockMethod.SYSTEM_BIOMETRIC,
            enabled = systemLockAvailable || selected == LockMethod.SYSTEM_BIOMETRIC,
            onClick = { onSelect(LockMethod.SYSTEM_BIOMETRIC) }
        )
    }
}

/** One option, drawn exactly like a [SettingsRow]: identical card, badge and text geometry. */
@Composable
private fun MethodRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        onClick = if (enabled) onClick else null,
        contentPadding = PaddingValues(MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = icon, tint = MmColors.accent, size = 40.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MmType.body,
                    color = if (enabled) MmColors.textPrimary else MmColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Reserve the trailing slot either way so titles stay aligned, and mark the
            // choice with the same accent check the other pickers use.
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MmColors.accent,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}
