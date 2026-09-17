package com.moneymanager.app.ui.accountedit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun AccountEditScreen(
    accountId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val supportsManualBalance = state.accountType == AccountType.BANK ||
        state.accountType == AccountType.CASH ||
        state.accountType == AccountType.WALLET ||
        state.accountType == AccountType.DEBIT_CARD ||
        state.accountType == AccountType.PREPAID_CARD

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        androidx.compose.material.Surface(color = MMGreenDark, elevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Text(
                    "Edit account",
                    color = MMWhite,
                    style = MmType.screenTitle,
                    modifier = Modifier.weight(1f).padding(start = MmSpacing.xs)
                )
                IconButton(onClick = viewModel::save) {
                    Icon(Icons.Filled.Check, contentDescription = "Save account", tint = MMWhite)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl),
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
                            "This creates a live checkpoint. Historical transactions are untouched.",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    } else {
                        Text(
                            "Your balance is calculated from your transactions.",
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
                    subtitle = "Turn off to exclude them from combined transaction views",
                    checked = !state.hideTransactions,
                    onCheckedChange = { viewModel.hideTransactions(!it) }
                )
            }

            state.error?.let {
                Text(it, color = MmColors.expense, style = MmType.caption)
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text("Save changes", color = MmColors.onAccent, style = MmType.label)
            }
        }
    }
}
