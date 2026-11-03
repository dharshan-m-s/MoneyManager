package com.moneymanager.app.ui.modern.screens.creditcardform

import androidx.compose.foundation.layout.Arrangement
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.modernTopInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MmStepper
import com.moneymanager.app.ui.components.MmSwitchRow
import com.moneymanager.app.ui.components.MmTextField
import com.moneymanager.app.ui.components.MmWalletCard
import com.moneymanager.app.ui.components.mmFieldShape
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.creditcardform.CreditCardFormViewModel

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
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) {
        if (headerHeightPx > 0) headerHeightPx.toDp() else 100.dp
    }

    LaunchedEffect(editingAccountId) { editingAccountId?.let { viewModel.loadForEdit(it) } }
    // Navigate back to the actual caller immediately. Showing (and waiting for) a snackbar
    // here delayed the pop and could race with a manual Back, producing a double pop.
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            MoneyManagerTopBar(
                title = if (editingAccountId != null) "Credit Card Account" else "New Credit Card",
                onBack = onBack,
                actions = {
                    if (editingAccountId != null) {
                        MmTopBarAction(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete credit card",
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                    MmTopBarAction(
                        icon = Icons.Filled.Check,
                        contentDescription = "Save credit card",
                        onClick = { if (!state.saving) viewModel.save() }
                    )
                },
                modifier = Modifier.fillMaxWidth().zIndex(3f).overlayWithoutLayoutSpace()
                    .onSizeChanged { headerHeightPx = it.height }
            )
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
                .padding(top = headerInset)
                .padding(MmSpacing.screen)
                .padding(bottom = MmSpacing.xxl)
                .revealOnAppear(),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "CARD PREVIEW",
                        style = MmType.label.copy(letterSpacing = 0.9.sp, fontWeight = FontWeight.SemiBold),
                        color = MmColors.textPrimary
                    )
                    Text(
                        "Updates as you edit the details",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
                Text(
                    "LIVE",
                    style = MmType.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = MmColors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MmColors.accent.copy(alpha = 0.10f))
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }

            // Preview shows only the issuer, nickname and masked last four digits.
            MmWalletCard(
                issuer = state.billerName,
                title = state.nickname.ifBlank { state.billerName.ifBlank { "Your credit card" } },
                typeLabel = "Credit",
                maskedNumber = state.maskedNumber,
                active = !state.inactive,
                modifier = Modifier.fillMaxWidth()
            )

            MmCard {
                Text("01  ·  CARD IDENTITY", style = MmType.caption.copy(letterSpacing = 1.0.sp, fontWeight = FontWeight.Bold), color = MmColors.accent)
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
                    label = "Card number / reference",
                    placeholder = "Enter card number",
                    keyboardType = KeyboardType.NumberPassword,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.cardNumberError != null,
                    supportingText = state.cardNumberError
                        ?: "Hidden while typing. Only the last four digits appear on card previews."
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmTextField(
                    value = state.nickname,
                    onValueChange = { viewModel.onField { copy(nickname = it) } },
                    label = "Nickname",
                    placeholder = "Shown across the app"
                )
            }

            // --- Financial information: always vertical stack for consistent layout ---
            MmCard {
                Text("02  ·  FINANCIAL POSITION", style = MmType.caption.copy(letterSpacing = 1.0.sp, fontWeight = FontWeight.Bold), color = MmColors.accent)
                Spacer(Modifier.height(MmSpacing.md))
                MmAmountField(
                    value = state.outstanding,
                    onValueChange = viewModel::onOutstandingChange,
                    label = "Current outstanding",
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
                Spacer(Modifier.height(MmSpacing.md))
                // Available limit is derived (creditLimit - outstanding), shown as read-only
                val outstandingValue = remember(state.outstanding) {
                    runCatching {
                        com.moneymanager.app.domain.model.Money.fromRupees(
                            java.math.BigDecimal(state.outstanding.trim().ifEmpty { "0" })
                        ).minorUnits
                    }.getOrNull() ?: 0L
                }
                val limitValue = remember(state.creditLimit) {
                    runCatching {
                        com.moneymanager.app.domain.model.Money.fromRupees(
                            java.math.BigDecimal(state.creditLimit.trim().ifEmpty { "0" })
                        ).minorUnits
                    }.getOrNull()
                }
                val availableLimit = limitValue?.let { it - outstandingValue }
                val availableLimitText = availableLimit?.let { com.moneymanager.app.domain.model.Money(it).toBigDecimal().stripTrailingZeros().toPlainString() } ?: ""
                MmReadOnlyAmountRow(
                    label = "Available limit (calculated)",
                    value = availableLimitText.ifBlank { "0.00" },
                    supporting = null,
                    emphasiseNegative = availableLimit != null && availableLimit < 0
                )
            }

            // --- Billing settings ---
            MmCard {
                Text("03  ·  BILLING CYCLE", style = MmType.caption.copy(letterSpacing = 1.0.sp, fontWeight = FontWeight.Bold), color = MmColors.accent)
                Spacer(Modifier.height(MmSpacing.md))
                MmStepper(
                    label = "Billing cycle starts on",
                    value = if (state.billingCycleSet) state.billingCycleStartDay else null,
                    onChange = { day -> viewModel.onBillingCycleStartDayChange(day) },
                    supportingText = "Your statement runs from day ${state.billingCycleStartDay} of each month to the day before it next month."
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmDateField(
                    label = "Next due date",
                    epochMillis = state.dueDateEpochMillis,
                    onChange = viewModel::onDueDateChange
                )
                Spacer(Modifier.height(MmSpacing.md))
                // The statement of the cycle that is running right now is COMPUTED from the
                // card's own transactions - a purchase, a refund or a payment moves it
                // immediately. It can never be typed in, so it can never disagree with the
                // outstanding the card screen shows.
                MmReadOnlyAmountRow(
                    label = "This cycle's statement",
                    value = state.currentStatement.ifBlank { "0" },
                    supporting = state.currentCycleLabel.takeIf { it.isNotBlank() }
                )
                Spacer(Modifier.height(MmSpacing.md))
                MmAmountField(
                    value = state.billAmount,
                    onValueChange = viewModel::onBillAmountChange,
                    label = "Estimated statement",
                    isError = state.billAmountError != null,
                    supportingText = state.billAmountError
                        ?: "Only used for a cycle that has not closed yet - the real amount is computed from this card's transactions."
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
                Text("04  ·  PREFERENCES", style = MmType.caption.copy(letterSpacing = 1.0.sp, fontWeight = FontWeight.Bold), color = MmColors.accent)
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

            MmPrimaryButton(
                text = when {
                    state.saving -> "Saving…"
                    editingAccountId != null -> "Save changes"
                    else -> "Add credit card"
                },
                onClick = { viewModel.save() },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            )

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
            shape = RoundedCornerShape(28.dp),
            backgroundColor = MmColors.surface,
            contentColor = MmColors.textPrimary,
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

/**
 * A field that is calculated, not typed: it shows a derived amount the same way every other
 * read-only amount in the app does, with an optional supporting line underneath.
 */
@Composable
private fun MmReadOnlyAmountRow(
    label: String,
    value: String,
    supporting: String?,
    emphasiseNegative: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MmType.label,
            color = MmColors.textSecondary,
            modifier = Modifier.padding(start = 3.dp, bottom = 5.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(mmFieldShape)
                .background(MmColors.surfaceMuted)
                .padding(horizontal = MmSpacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("₹", fontWeight = FontWeight.SemiBold, color = MmColors.textSecondary)
            Spacer(Modifier.width(MmSpacing.md))
            Text(
                value,
                style = MmType.amountRow,
                color = if (emphasiseNegative) MmColors.expense else MmColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        supporting?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MmType.caption,
                color = MmColors.textSecondary,
                modifier = Modifier.padding(start = 3.dp, top = 5.dp)
            )
        }
    }
}
