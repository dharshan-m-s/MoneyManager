package com.moneymanager.app.ui.modern.screens.accountedit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.accountedit.AccountEditViewModel

@Composable
fun AccountEditScreen(
    accountId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: AccountEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId); viewModel.checkDelete() }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val supportsManualBalance = AccountTypeCatalog.supportsManualBalance(state.accountType)

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Edit account",
            onBack = onBack,
            actions = {
                MmTopBarAction(
                    icon = Icons.Filled.Check,
                    contentDescription = "Save account",
                    onClick = viewModel::save
                )
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl)
                .revealOnAppear(),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                Text("Account details", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.nickname,
                    onValueChange = viewModel::nickname,
                    label = "Account name",
                    placeholder = "Shown across the app"
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.institutionName,
                    onValueChange = viewModel::institutionName,
                    label = "Bank / institution",
                    placeholder = "e.g. HDFC Bank"
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.sourceAccountId,
                    onValueChange = viewModel::sourceAccountId,
                    label = "Account ID",
                    supportingText = "Must stay unique across your accounts."
                )
            }

            if (supportsManualBalance) {
                MmCard {
                    Text("Balance", style = MmType.label, color = MmColors.textSecondary)
                    MmSwitchRow(
                        title = "Use a manual balance",
                        subtitle = "Set the balance you see right now, without touching imported history",
                        checked = state.manualBalanceEnabled,
                        onCheckedChange = viewModel::manualBalanceEnabled
                    )
                    if (state.manualBalanceEnabled) {
                        MmAmountField(
                            value = state.currentBalance,
                            onValueChange = viewModel::currentBalance,
                            label = "Balance right now"
                        )
                        Spacer(Modifier.height(MmSpacing.sm))
                        Text(
                            "This is your current effective balance, including transactions after the " +
                                "last checkpoint. Editing it creates a new reconciliation checkpoint; " +
                                "imported history is untouched.",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                        if (state.balanceDirty) {
                            Spacer(Modifier.height(MmSpacing.xs))
                            Text(
                                "You changed this balance. Saving will apply it as a checkpoint now.",
                                style = MmType.caption,
                                color = MmColors.accent
                            )
                        }
                    } else {
                        Text(
                            "Your balance is calculated from your transactions" +
                                " (currently ${state.calculatedBalance}).",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                }
            }

            MmCard {
                Text("Type & classification", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                MmSegmentedControl(
                    options = BusinessPersonal.entries
                        .filter { it != BusinessPersonal.UNKNOWN }
                        .map { it.raw.replaceFirstChar { c -> c.uppercase() } },
                    selectedIndex = BusinessPersonal.entries
                        .filter { it != BusinessPersonal.UNKNOWN }
                        .indexOf(state.businessPersonal)
                        .coerceAtLeast(0),
                    onSelect = { index ->
                        viewModel.businessPersonal(BusinessPersonal.entries.filter { it != BusinessPersonal.UNKNOWN }[index])
                    }
                )
                Spacer(Modifier.height(MmSpacing.md))
                Text("Account type", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    AccountType.entries.filter { it != AccountType.UNKNOWN }.forEach { type ->
                        val selected = type == state.accountType
                        OutlinedButton(
                            onClick = { viewModel.accountType(type) },
                            shape = RoundedCornerShape(MmSpacing.radiusRow),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (selected) MmColors.accent.copy(alpha = 0.14f) else Color.Transparent,
                                contentColor = if (selected) MmColors.accent else MmColors.textSecondary
                            )
                        ) {
                            Text(type.raw.replace('-', ' ').replaceFirstChar { it.uppercase() }, style = MmType.caption)
                        }
                    }
                }
            }

            MmCard {
                Text("Account status", style = MmType.label, color = MmColors.textSecondary)
                MmSwitchRow(
                    title = "Active",
                    subtitle = "Include this account in your totals",
                    checked = state.active,
                    onCheckedChange = viewModel::active
                )
                MmSwitchRow(
                    title = "Visible in lists",
                    subtitle = "Hidden accounts stay out of the accounts overview",
                    checked = !state.hide,
                    onCheckedChange = { viewModel.hide(!it) }
                )
                MmSwitchRow(
                    title = "Show its transactions",
                    subtitle = "Turn off to exclude its transactions and balance from combined totals",
                    checked = !state.hideTransactions,
                    onCheckedChange = { viewModel.hideTransactions(!it) }
                )
            }

            MmCard {
                Text("Danger zone", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MmColors.expense)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MmColors.expense)
                    Spacer(Modifier.padding(horizontal = MmSpacing.xs))
                    Text("Delete account")
                }
                state.deleteBlockedMessage?.let {
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(it, style = MmType.caption, color = MmColors.textSecondary)
                }
            }

            state.error?.let {
                Text(it, color = MmColors.expense, style = MmType.caption)
            }

            MmPrimaryButton(
                text = if (state.saving) "Saving…" else "Save changes",
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this account?") },
            text = {
                val accountLabel = AccountTypeCatalog.label(state.accountType)
                val accountName = state.nickname.ifBlank { state.institutionName.ifBlank { "this $accountLabel" } }
                val msg = state.deleteBlockedMessage
                Text(
                    text = if (msg != null) msg
                    else
                        "\"$accountName\" ($accountLabel) has no financial history, so it can be permanently deleted. This cannot be undone.",
                    style = MmType.body,
                    color = MmColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MmColors.expense, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
