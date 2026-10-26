package com.moneymanager.app.ui.modern.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmIconBadge
import com.moneymanager.app.ui.modern.components.MmSwitchRow
import com.moneymanager.app.ui.modern.components.MmTextField
import com.moneymanager.app.ui.modern.components.MmTopBarAction
import com.moneymanager.app.ui.modern.components.MoneyManagerTopBar
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import com.moneymanager.app.ui.modern.components.MmPrimaryButton
import com.moneymanager.app.ui.accounts.BankLinkingViewModel

@Composable
fun BankLinkingScreen(
    bankName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BankLinkingViewModel = hiltViewModel()
) {
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var debitCardLast4 by rememberSaveable { mutableStateOf("") }
    var isBusiness by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    fun save() {
        if (!state.saving && accountNumber.isNotBlank()) {
            val lower = bankName.lowercase()
            val type = when {
                lower.contains("loan") -> com.moneymanager.app.domain.model.AccountType.LOAN
                lower.contains("wallet") -> com.moneymanager.app.domain.model.AccountType.WALLET
                lower.contains("prepaid") -> com.moneymanager.app.domain.model.AccountType.PREPAID_CARD
                lower.contains("invest") -> com.moneymanager.app.domain.model.AccountType.INVESTMENT
                lower.contains("cash") -> com.moneymanager.app.domain.model.AccountType.CASH
                else -> com.moneymanager.app.domain.model.AccountType.BANK
            }
            if (type == com.moneymanager.app.domain.model.AccountType.BANK) {
                viewModel.save(bankName, accountNumber, debitCardLast4, isBusiness)
            } else {
                viewModel.save(bankName, accountNumber, debitCardLast4, isBusiness, type)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Add account",
            subtitle = bankName,
            onBack = onBack,
            actions = {
                MmTopBarAction(
                    icon = Icons.Filled.Check,
                    contentDescription = "Save account",
                    onClick = { save() }
                )
            }
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.AccountBalance, tint = MmColors.accent, size = 44.dp)
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(bankName, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Linked bank account", style = MmType.caption, color = MmColors.textSecondary)
                    }
                }
            }

            MmCard {
                Text("Account reference", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { c -> c.isDigit() }.take(12) },
                    label = "Account number",
                    placeholder = "Full number or last 4 digits",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = debitCardLast4,
                    onValueChange = { debitCardLast4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = "Debit card (optional)",
                    placeholder = "Last 4 digits",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(MmSpacing.md))
                Text(
                    "The reference keeps accounts distinct and is never shown in full on list screens.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }

            MmCard {
                MmSwitchRow(
                    title = "Business account",
                    subtitle = "Classify this account as business rather than personal",
                    checked = isBusiness,
                    onCheckedChange = { isBusiness = it }
                )
            }

            state.error?.let { Text(it, color = MmColors.expense, style = MmType.caption) }

            MmPrimaryButton(
                text = if (state.saving) "Adding…" else "Add this account",
                onClick = { save() },
                enabled = !state.saving && accountNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
