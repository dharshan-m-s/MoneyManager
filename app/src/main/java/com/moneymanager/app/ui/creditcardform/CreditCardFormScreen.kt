package com.moneymanager.app.ui.creditcardform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmStepper
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun CreditCardFormScreen(
    editingAccountId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = onSaved,
    viewModel: CreditCardFormViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    val snackbarState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(editingAccountId) { editingAccountId?.let { viewModel.loadForEdit(it) } }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarState.showSnackbar("Credit card saved")
            onSaved()
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            Surface(color = MMGreenDark, elevation = 0.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                    }
                    Text(
                        if (editingAccountId != null) "Credit Card Account" else "New Credit Card",
                        color = MMWhite,
                        style = MmType.screenTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = MmSpacing.xs)
                    )
                    if (editingAccountId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete credit card", tint = MMWhite)
                        }
                    }
                    IconButton(onClick = { if (!state.saving) viewModel.save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save credit card", tint = MMWhite)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        backgroundColor = MmColors.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.screen)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            // --- Card identity preview ---
            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.CreditCard, tint = MmColors.accent, size = 46.dp)
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.nickname.ifBlank { state.billerName.ifBlank { "Your credit card" } },
                            style = MmType.sectionTitle,
                            color = MmColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.maskedNumber,
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            MmCard {
                Text("Card details", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.billerName,
                    onValueChange = { viewModel.onField { copy(billerName = it) } },
                    label = "Biller name",
                    placeholder = "e.g. HDFC Bank",
                    isError = state.billerNameError != null,
                    supportingText = state.billerNameError
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.cardNumber,
                    onValueChange = { viewModel.onField { copy(cardNumber = it) } },
                    label = "Credit card number",
                    placeholder = "Last 4 digits or full number",
                    isError = state.cardNumberError != null,
                    supportingText = state.cardNumberError
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.nickname,
                    onValueChange = { viewModel.onField { copy(nickname = it) } },
                    label = "Nickname",
                    placeholder = "Shown across the app"
                )
            }

            // --- Financial information: two columns only when the width genuinely allows it ---
            MmCard {
                Text("Financial information", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val sideBySide = maxWidth >= 340.dp
                    if (sideBySide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                            Box(Modifier.weight(1f)) {
                                MmAmountField(
                                    value = state.outstanding,
                                    onValueChange = viewModel::onOutstandingChange,
                                    label = "Amount outstanding",
                                    isError = state.outstandingError != null,
                                    supportingText = state.outstandingError
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                MmAmountField(
                                    value = state.creditLimit,
                                    onValueChange = viewModel::onCreditLimitChange,
                                    label = "Credit limit",
                                    isError = state.creditLimitError != null,
                                    supportingText = state.creditLimitError
                                )
                            }
                        }
                    } else {
                        MmAmountField(
                            value = state.outstanding,
                            onValueChange = viewModel::onOutstandingChange,
                            label = "Amount outstanding",
                            isError = state.outstandingError != null,
                            supportingText = state.outstandingError
                        )
                        Spacer(Modifier.height(MmSpacing.md))
                        MmAmountField(
                            value = state.creditLimit,
                            onValueChange = viewModel::onCreditLimitChange,
                            label = "Credit limit",
                            isError = state.creditLimitError != null,
                            supportingText = state.creditLimitError
                        )
                    }
                }
            }

            // --- Billing settings ---
            MmCard {
                Text("Billing settings", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmStepper(
                    label = "Billing cycle starts on",
                    value = if (state.billingCycleSet) state.billingCycleStartDay else null,
                    onChange = { day -> viewModel.onField { copy(billingCycleStartDay = day, billingCycleSet = true) } },
                    supportingText = "Your statement runs from day ${state.billingCycleStartDay} of each month to the day before it next month."
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmDateField(
                    label = "Next due date",
                    epochMillis = state.dueDateEpochMillis,
                    onChange = { picked -> viewModel.onField { copy(dueDateEpochMillis = picked) } }
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmAmountField(
                    value = state.billAmount,
                    onValueChange = viewModel::onBillAmountChange,
                    label = "Current bill amount",
                    isError = state.billAmountError != null,
                    supportingText = state.billAmountError ?: "Used until the actual statement is recorded."
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmSwitchRow(
                    title = "Auto-generate bills",
                    subtitle = "Create the bill 10 days before the due date",
                    checked = state.autoGenerateBills,
                    onCheckedChange = { value -> viewModel.onField { copy(autoGenerateBills = value) } }
                )
            }

            // --- Options ---
            MmCard {
                Text("Options", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                Text("Account type", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                MmSegmentedControl(
                    options = listOf("Personal", "Business"),
                    selectedIndex = if (state.isBusiness) 1 else 0,
                    onSelect = { index -> viewModel.onField { copy(isBusiness = index == 1) } }
                )
                Spacer(Modifier.height(MmSpacing.sm))
                MmSwitchRow(
                    title = "Auto-pay",
                    subtitle = "Marks the linked bill as paid automatically",
                    checked = state.autoPay,
                    onCheckedChange = { value -> viewModel.onField { copy(autoPay = value) } }
                )
                MmSwitchRow(
                    title = "Inactive card",
                    subtitle = "Use for a card you no longer actively use",
                    checked = state.inactive,
                    onCheckedChange = { value -> viewModel.onField { copy(inactive = value) } }
                )
                MmSwitchRow(
                    title = "Don't show this card",
                    subtitle = "Hidden from the accounts overview",
                    checked = state.hideAccount,
                    onCheckedChange = { value -> viewModel.onField { copy(hideAccount = value) } }
                )
            }

            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MmSpacing.radiusRow),
                colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
            ) {
                Text(
                    when {
                        state.saving -> "Saving…"
                        editingAccountId != null -> "Save changes"
                        else -> "Add credit card"
                    },
                    color = MmColors.onAccent,
                    style = MmType.label
                )
            }

            if (editingAccountId != null) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete this credit card", color = MmColors.expense, style = MmType.label)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this credit card?") },
            text = {
                Text(
                    "The card and its linked biller are removed. Transactions already recorded stay " +
                        "in your history.",
                    style = MmType.body,
                    color = MmColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MmColors.expense, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
