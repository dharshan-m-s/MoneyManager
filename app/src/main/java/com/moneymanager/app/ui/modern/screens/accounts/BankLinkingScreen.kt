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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmBankLogo
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.ui.accounts.matchesBankIssuer
import com.moneymanager.app.ui.accounts.maskedBankAccountNumber
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.accounts.BankLinkingViewModel

@Composable
fun BankLinkingScreen(
    bankName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    accountType: AccountType = AccountType.BANK,
    viewModel: BankLinkingViewModel = hiltViewModel()
) {
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var debitCardLast4 by rememberSaveable { mutableStateOf("") }
    var linkedBankAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBankAccountPicker by rememberSaveable { mutableStateOf(false) }
    var isBusiness by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val issuerAccounts = state.existingBankAccounts.filter { it.matchesBankIssuer(bankName) }

    fun save() {
        if (state.saving) return
        if (accountType == AccountType.DEBIT_CARD) {
            viewModel.save(bankName, "", debitCardLast4, isBusiness, AccountType.DEBIT_CARD, linkedBankAccountId)
            return
        }
        if (accountNumber.isNotBlank()) {
            val lower = bankName.lowercase()
            val type = when {
                lower.contains("loan") -> com.moneymanager.app.domain.model.AccountType.LOAN
                lower.contains("wallet") -> com.moneymanager.app.domain.model.AccountType.WALLET
                lower.contains("prepaid") -> com.moneymanager.app.domain.model.AccountType.PREPAID_CARD
                lower.contains("invest") -> com.moneymanager.app.domain.model.AccountType.INVESTMENT
                lower.contains("cash") -> com.moneymanager.app.domain.model.AccountType.CASH
                else -> com.moneymanager.app.domain.model.AccountType.BANK
            }
            if (type == AccountType.BANK) {
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
                .padding(horizontal = MmSpacing.screen)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            MmCard(backgroundColor = MmColors.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmBankLogo(bankName, Modifier.size(54.dp))
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ACCOUNT IDENTITY", style = MmType.caption.copy(letterSpacing = 0.9.sp), color = MmColors.accent)
                        Text(bankName, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Ready to be added to your accounts", style = MmType.caption, color = MmColors.textSecondary)
                    }
                }
            }

            MmCard {
                Text(
                    if (accountType == AccountType.DEBIT_CARD) "Debit card details" else "Account reference",
                    style = MmType.label,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.md))
                if (accountType == AccountType.DEBIT_CARD) {
                    MmTextField(
                        value = debitCardLast4,
                        onValueChange = { debitCardLast4 = it.filter(Char::isDigit).take(4) },
                        label = "Last 4 digits of debit card",
                        placeholder = "1234",
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        "A bank account is optional. You can link one now or keep this card standalone.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                } else {
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
            }

            if (accountType == AccountType.DEBIT_CARD) {
                MmCard {
                    Text("Bank account (optional)", style = MmType.label, color = MmColors.textSecondary)
                    Spacer(Modifier.height(MmSpacing.sm))
                    androidx.compose.material.OutlinedButton(
                        onClick = { showBankAccountPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MmSpacing.radiusRow)
                    ) {
                        Column(Modifier.weight(1f)) {
                            val linked = issuerAccounts.firstOrNull { it.id == linkedBankAccountId }
                            Text(
                                linked?.let { it.nickname.ifBlank { it.institutionName } + "  " + it.maskedBankAccountNumber() } ?: "Continue without linking",
                                style = MmType.body,
                                color = MmColors.textPrimary
                            )
                            Text(
                                linked?.let { it.institutionName + " · " + MoneyFormat.rupeesNoDecimals(Money(it.currentBalanceMinorUnits)) } ?: "You can link an account later",
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        }
                        Text(
                            if (linkedBankAccountId == null) "Select" else "Change",
                            color = MmColors.accent
                        )
                    }
                    if (issuerAccounts.isEmpty()) {
                        Text(
                            "No existing $bankName account found. You can still create this card independently.",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                }
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
                text = if (state.saving) "Adding…" else if (accountType == AccountType.DEBIT_CARD) "Add debit card" else "Add this account",
                onClick = { save() },
                enabled = !state.saving && (
                    if (accountType == AccountType.DEBIT_CARD) debitCardLast4.length == 4
                    else accountNumber.isNotBlank()
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showBankAccountPicker) {
        MmPickerSheet(
            title = "Choose existing bank account",
            options = issuerAccounts.map { account ->
                MmOption(
                    id = account.id,
                    label = account.nickname.ifBlank { account.institutionName } + " · " + account.maskedBankAccountNumber(),
                    subtitle = account.institutionName + " · " + MoneyFormat.rupeesNoDecimals(Money(account.currentBalanceMinorUnits))
                )
            },
            selectedId = linkedBankAccountId,
            onSelect = { option ->
                linkedBankAccountId = option.id.takeIf { it >= 0L }
                showBankAccountPicker = false
            },
            onDismiss = { showBankAccountPicker = false },
            allowNone = true,
            noneLabel = "Continue without linking",
            searchHint = "Search bank accounts"
        )
    }
}
