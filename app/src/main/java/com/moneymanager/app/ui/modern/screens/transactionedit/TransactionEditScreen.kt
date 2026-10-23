package com.moneymanager.app.ui.modern.screens.transactionedit

import androidx.compose.foundation.layout.Arrangement
import com.moneymanager.app.ui.modern.components.MmTopBarAction
import com.moneymanager.app.ui.modern.components.MoneyManagerTopBar
import com.moneymanager.app.ui.modern.components.modernTopInsets
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.ui.modern.components.MmAmountField
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmLoadingState
import com.moneymanager.app.ui.modern.components.MmDateField
import com.moneymanager.app.ui.modern.components.MmOption
import com.moneymanager.app.ui.modern.components.MmPickerField
import com.moneymanager.app.ui.modern.components.MmPickerSheet
import com.moneymanager.app.ui.modern.components.MmReceiptAttachSection
import com.moneymanager.app.ui.modern.components.MmSegmentedControl
import com.moneymanager.app.ui.modern.components.MmSwitchRow
import com.moneymanager.app.ui.modern.components.MmTextField
import com.moneymanager.app.ui.modern.components.MoneyFormat
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import kotlinx.coroutines.launch
import java.math.BigDecimal
import com.moneymanager.app.ui.modern.components.MmPrimaryButton
import com.moneymanager.app.ui.modern.components.revealOnAppear
import com.moneymanager.app.ui.transactionedit.TransactionEditViewModel
import com.moneymanager.app.ui.transactionedit.editablePaymentTypes

@Composable
fun TransactionEditScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransactionEditViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val originalInactiveAccountName by viewModel.originalAccountName.collectAsState()
    val attachments by remember(transactionId) { viewModel.attachmentsFor(transactionId) }
        .collectAsState(initial = emptyList())
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var categorySheet by remember { mutableStateOf(false) }
    var accountSheet by remember { mutableStateOf(false) }
    var paymentSheet by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            MoneyManagerTopBar(
                title = "Edit",
                subtitle = if (state.isTransfer) "Transfer" else if (state.isIncome) "Income" else "Expense",
                onBack = onBack,
                actions = {
                    MmTopBarAction(
                        icon = if (state.saving) Icons.Filled.Done else Icons.Filled.Check,
                        contentDescription = "Save changes",
                        onClick = { if (!state.saving) viewModel.save() }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        backgroundColor = MmColors.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loaded) {
                MmLoadingState("Loading transaction…")
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(MmSpacing.screen)
                        .padding(bottom = MmSpacing.xxl)
                        .revealOnAppear(),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                ) {
                    if (state.isTransfer) {
                        MmCard {
                            Text("Transfer", style = MmType.sectionTitle, color = MmColors.textPrimary)
                            Spacer(Modifier.height(MmSpacing.xs))
                            Text(
                                if (state.isBillPaymentPair) {
                                    "Both sides of this credit-card payment stay linked. The amount " +
                                        "belongs to the bill payment, so it is shown read-only here - " +
                                        "edit the bill payment to change what was paid. The date applies " +
                                        "to both sides; merchant, payment method and notes stay per side."
                                } else {
                                    "Both sides of this transfer stay linked. Amount and date apply to " +
                                        "both sides together. Category, direction and account are managed " +
                                        "from the transfer itself; merchant, payment method and notes " +
                                        "stay per side."
                                },
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        }
                    }

                    // --- Amount + direction ---
                    MmCard {
                        if (!state.isTransfer) {
                            MmSegmentedControl(
                                options = listOf("Expense", "Income"),
                                selectedIndex = if (state.isIncome) 1 else 0,
                                onSelect = { index ->
                                    viewModel.onField { copy(isIncome = index == 1) }
                                }
                            )
                            Spacer(Modifier.height(MmSpacing.md))
                        }
                        if (state.isBillPaymentPair) {
                            // Read-only: the amount belongs to the bill-payment model (section 3).
                            Text("Amount", style = MmType.label, color = MmColors.textSecondary)
                            Spacer(Modifier.height(MmSpacing.xs))
                            Text(
                                runCatching { MoneyFormat.rupeesNoDecimals(Money.fromRupees(BigDecimal(state.amount))) }
                                    .getOrDefault("₹ ${state.amount}"),
                                style = MmType.sectionTitle,
                                color = MmColors.textPrimary
                            )
                            Spacer(Modifier.height(MmSpacing.xs))
                            Text(
                                "Set by the bill payment - edit the bill to change it",
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        } else {
                            MmAmountField(
                                value = state.amount,
                                onValueChange = { viewModel.onField { copy(amount = it) } },
                                label = "Amount",
                                isError = state.amountError != null,
                                supportingText = state.amountError
                                    ?: if (state.isTransfer) "Applies to both sides of the transfer" else null
                            )
                        }
                        Spacer(Modifier.height(MmSpacing.md))
                        MmDateField(
                            label = "Date",
                            epochMillis = state.epochMillis,
                            onChange = { picked -> viewModel.onField { copy(epochMillis = picked) } },
                            includeTime = true
                        )
                        if (state.dateError != null) {
                            Text(state.dateError!!, fontSize = 11.sp, color = MmColors.expense, modifier = Modifier.padding(top = 3.dp))
                        } else if (state.isTransfer) {
                            Text(
                                "Applies to both sides of the transfer",
                                style = MmType.caption,
                                color = MmColors.textSecondary,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }

                    // --- What was it ---
                    MmCard {
                        Text("Details", style = MmType.label, color = MmColors.textSecondary)
                        Spacer(Modifier.height(MmSpacing.md))
                        MmTextField(
                            value = state.merchant,
                            onValueChange = { viewModel.onField { copy(merchant = it) } },
                            label = if (state.isIncome) "Received from" else "Paid to",
                            placeholder = "Merchant, person or description"
                        )
                        if (!state.isTransfer) {
                            Spacer(Modifier.height(MmSpacing.md))
                            MmPickerField(
                                label = "Category",
                                value = categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty(),
                                placeholder = "Choose a category",
                                onClick = { categorySheet = true }
                            )
                            Spacer(Modifier.height(MmSpacing.md))
                            MmPickerField(
                                label = "Account",
                                value = accounts.firstOrNull { it.id == state.accountId }
                                    ?.let { it.nickname.ifBlank { it.institutionName } }
                                    // Editing an old transaction on an inactive account shows the real
                                    // account, clearly marked, instead of an empty field.
                                    ?: originalInactiveAccountName?.takeIf { state.accountId > 0 }
                                    .orEmpty(),
                                placeholder = "Choose an account",
                                onClick = { accountSheet = true }
                            )
                            if (state.accountError != null) {
                                Text(state.accountError!!, fontSize = 11.sp, color = MmColors.expense, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        Spacer(Modifier.height(MmSpacing.md))
                        MmPickerField(
                            label = "Payment method",
                            value = paymentTypeLabel(state.paymentType),
                            onClick = { paymentSheet = true }
                        )
                    }

                    // --- Options (Split/Business/Loan features are intentionally not exposed) ---
                    MmCard {
                        Text("Options", style = MmType.label, color = MmColors.textSecondary)
                        MmSwitchRow(
                            title = "Reimbursable",
                            subtitle = "Track this until someone pays you back",
                            checked = state.reimbursable,
                            onCheckedChange = { value -> viewModel.onField { copy(reimbursable = value) } }
                        )
                        MmSwitchRow(
                            title = if (state.isIncome) "Include in income" else "Include in spending",
                            subtitle = "Turn off to keep it out of totals and reports",
                            checked = state.includeInStatistics,
                            onCheckedChange = { value -> viewModel.onField { copy(includeInStatistics = value) } }
                        )
                    }

                    MmCard {
                        MmTextField(
                            value = state.notes,
                            onValueChange = { viewModel.onField { copy(notes = it) } },
                            label = "Notes",
                            placeholder = "Optional",
                            singleLine = false,
                            minLines = 3
                        )
                    }

                    MmCard {
                        MmReceiptAttachSection(
                            transactionId = transactionId,
                            attachments = attachments,
                            repository = viewModel.attachmentRepository,
                            onMessage = { message -> scope.launch { snackbarState.showSnackbar(message) } }
                        )
                    }

                    state.error?.let {
                        Text(it, style = MmType.caption, color = MmColors.expense)
                    }

                    MmPrimaryButton(
                        text = if (state.saving) "Saving…" else "Save changes",
                        onClick = { viewModel.save() },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = onBack) { Text("Cancel") }
                    }

                    Text(
                        "Changes update this transaction in place — no duplicate entry is created.",
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (categorySheet) {
        MmPickerSheet(
            title = "Choose category",
            options = categories.map { MmOption(it.id, it.name, it.kind.name.lowercase()) },
            selectedId = state.categoryId,
            onSelect = { option ->
                viewModel.onField { copy(categoryId = option.id.takeIf { it > 0 }) }
                categorySheet = false
            },
            onDismiss = { categorySheet = false },
            allowNone = true,
            noneLabel = "Uncategorised",
            searchHint = "Search categories"
        )
    }

    if (accountSheet) {
        MmPickerSheet(
            title = "Choose account",
            options = accounts
                .filter { !it.deleted }
                .map {
                    MmOption(
                        id = it.id,
                        label = it.nickname.ifBlank { it.institutionName },
                        subtitle = it.accountType.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { c -> c.uppercase() }
                    )
                },
            selectedId = state.accountId,
            onSelect = { option ->
                viewModel.onField { copy(accountId = option.id) }
                accountSheet = false
            },
            onDismiss = { accountSheet = false },
            searchHint = "Search accounts"
        )
    }

    if (paymentSheet) {
        MmPickerSheet(
            title = "Payment method",
            options = editablePaymentTypes.map { MmOption(it.ordinal.toLong(), paymentTypeLabel(it)) },
            selectedId = state.paymentType.ordinal.toLong(),
            onSelect = { option ->
                viewModel.onField { copy(paymentType = editablePaymentTypes[option.id.toInt()]) }
                paymentSheet = false
            },
            onDismiss = { paymentSheet = false }
        )
    }
}

internal fun paymentTypeLabel(type: PaymentType): String = when (type) {
    PaymentType.CASH -> "Cash"
    PaymentType.CREDIT_CARD -> "Credit card"
    PaymentType.DEBIT_CARD -> "Debit card"
    PaymentType.NETBANKING -> "Netbanking"
    PaymentType.UPI -> "UPI"
    PaymentType.ONLINE_TRANSFER -> "Online transfer"
    PaymentType.PREPAID_CARD -> "Prepaid card"
    PaymentType.IMPS -> "IMPS"
    PaymentType.CHEQUE -> "Cheque"
    PaymentType.NULL_PAYMENT -> "Unspecified"
    PaymentType.NONE -> "Unspecified"
    PaymentType.UNKNOWN -> "Unspecified"
}
