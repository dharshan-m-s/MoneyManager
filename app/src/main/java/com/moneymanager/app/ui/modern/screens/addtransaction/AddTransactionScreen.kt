package com.moneymanager.app.ui.modern.screens.addtransaction

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.ui.modern.screens.categories.CategoryVisual
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmAmountHero
import com.moneymanager.app.ui.modern.components.MmDateField
import com.moneymanager.app.ui.modern.components.MmIconBadge
import com.moneymanager.app.ui.modern.components.MmTopBarAction
import com.moneymanager.app.ui.modern.components.MoneyManagerTopBar
import com.moneymanager.app.ui.modern.components.MmOption
import com.moneymanager.app.ui.modern.components.MmPickerSheet
import com.moneymanager.app.ui.modern.components.MmSwitchRow
import com.moneymanager.app.ui.modern.components.MmTextField
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import com.moneymanager.app.ui.modern.components.pressableClick
import com.moneymanager.app.ui.modern.components.MmPrimaryButton
import com.moneymanager.app.ui.addtransaction.AddTransactionEntryPoint
import com.moneymanager.app.ui.addtransaction.AddTransactionViewModel

@Composable
fun AddTransactionScreen(
    entryPoint: AddTransactionEntryPoint,
    preselectedAccountId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToTransfer: () -> Unit = {},
    onMoreCategories: () -> Unit = {},
    selectedCategoryIdFromPicker: Long? = null,
    onConsumeCategorySelection: () -> Unit = {},
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    val visibleCategories = remember(state.categories) { state.categories.filter { it.active }.take(16) }
    var accountPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(entryPoint, preselectedAccountId) { viewModel.initialize(entryPoint, preselectedAccountId) }
    LaunchedEffect(selectedCategoryIdFromPicker) {
        selectedCategoryIdFromPicker?.let {
            viewModel.onCategorySelected(it)
            onConsumeCategorySelection()
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val isCash = state.isCash
    // A credit card is not an income source: money coming back to it is a refund that clears what
    // the card owes. The whole screen (title, wording, helper copy) follows that meaning so the
    // user never records a refund and then wonders why it shows up as income.
    val isCard = selectedAccount?.accountType == AccountType.CREDIT_CARD
    val isCardRefund = isCard && state.isIncome
    val title = when {
        isCardRefund -> "Record a refund"
        isCard -> "Add a card purchase"
        entryPoint == AddTransactionEntryPoint.ACCOUNT_INCOME -> "Add income"
        entryPoint == AddTransactionEntryPoint.ACCOUNT_SPEND -> "Add expense"
        entryPoint == AddTransactionEntryPoint.CASH_INCOME -> "Add cash income"
        else -> "Add cash expense"
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = if (isCash) "Cash" else selectedAccount?.nickname?.ifBlank { selectedAccount.institutionName }
                ?: "Choose an account before saving",
            onBack = onBack,
            actions = {
                MmTopBarAction(
                    icon = Icons.Filled.Check,
                    contentDescription = "Save transaction",
                    onClick = { viewModel.save(entryPoint) }
                )
            }
        )

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
                MmAmountHero(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange
                )
            }

            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.Store, tint = MmColors.accent, size = 42.dp)
                    Spacer(Modifier.width(MmSpacing.md))
                    Box(Modifier.weight(1f)) {
                        MmTextField(
                            value = state.merchant,
                            onValueChange = viewModel::onMerchantChange,
                            label = when {
                                isCardRefund -> "Refunded by"
                                isCard -> "Spent on"
                                state.isIncome -> "Received from"
                                else -> "Paid to"
                            },
                            placeholder = when {
                                isCardRefund -> "Who refunded you?"
                                isCard -> "What did you buy?"
                                state.isIncome -> "Who paid you?"
                                else -> "Where did you spend?"
                            }
                        )
                    }
                }
            }

            MmCard(onClick = { if (!isCash) accountPickerOpen = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(
                        icon = if (isCash) Icons.Filled.Payments else Icons.Filled.AccountBalanceWallet,
                        tint = MmColors.accent,
                        size = 42.dp
                    )
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isCash) "Pay with" else "Using account · tap to change",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                        Text(
                            when {
                                isCash -> "Cash"
                                selectedAccount != null ->
                                    selectedAccount.nickname.ifBlank { selectedAccount.institutionName }
                                else -> "No account selected"
                            },
                            style = MmType.body,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCash || selectedAccount != null) MmColors.textPrimary else MmColors.expense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCash) {
                            Text(
                                "Cash is money in hand, not a bank account",
                                style = MmType.caption,
                                color = MmColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (selectedAccount != null) {
                            Text(
                                listOfNotNull(
                                    selectedAccount.institutionName.takeIf { it.isNotBlank() },
                                    selectedAccount.sourceAccountId.takeIf { it.isNotBlank() }
                                ).distinct().joinToString(" • "),
                                style = MmType.caption,
                                color = MmColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (!isCash) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Change account",
                            tint = MmColors.textSecondary
                        )
                    }
                }
            }

            if (isCard) {
                MmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MmIconBadge(
                            icon = Icons.Filled.CreditCard,
                            tint = if (isCardRefund) MmColors.income else MmColors.expense,
                            size = 42.dp
                        )
                        Spacer(Modifier.width(MmSpacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isCardRefund) "This clears what the card owes" else "This adds to what the card owes",
                                style = MmType.sectionTitle,
                                color = MmColors.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (isCardRefund) {
                                    "A refund reduces the card's outstanding balance. It is not app income, " +
                                        "so it never inflates your income totals."
                                } else {
                                    "The purchase is added to the card's outstanding balance and is settled " +
                                        "when you pay the card."
                                },
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        }
                    }
                }
            }

            MmCard {
                MmDateField(
                    label = "Transaction date & time",
                    epochMillis = state.occurredAtEpochMillis,
                    onChange = viewModel::onOccurredAtChange,
                    includeTime = true
                )
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    "This is when the money actually moved. Backdated entries still update the " +
                        "right balance and the correct monthly period.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }

            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Category", style = MmType.sectionTitle, color = MmColors.textPrimary)
                        Text(
                            "Tap one to classify this transaction",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                    if (state.selectedCategoryId != null) {
                        Text("Selected", color = MmColors.accent, style = MmType.caption, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(MmSpacing.md))
                val rows = visibleCategories.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { category ->
                                val selected = category.id == state.selectedCategoryId
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .pressableClick {
                                            if (category.name == "A/c to A/c") onNavigateToTransfer()
                                            else viewModel.onCategorySelected(category.id)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CategoryVisual(category = category, size = if (selected) 52.dp else 50.dp)
                                    Text(
                                        category.name,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (selected) MmColors.accent else MmColors.textSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(MmSpacing.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MmColors.accent.copy(alpha = .10f), RoundedCornerShape(MmSpacing.radiusRow))
                        .pressableClick(onClick = onMoreCategories)
                        .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("More categories", color = MmColors.accent, style = MmType.label)
                        Text(
                            "Browse everything or create a custom category",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = "More categories", tint = MmColors.accent)
                }
            }

            MmCard {
                MmSwitchRow(
                    title = when {
                        isCardRefund -> "Include in card totals"
                        state.isIncome -> "Count in income"
                        else -> "Count in spending"
                    },
                    subtitle = if (isCardRefund) {
                        "Turn off to hide this refund from the card's period totals"
                    } else {
                        "Turn off to keep this out of totals and reports"
                    },
                    checked = state.includeInStatistics,
                    onCheckedChange = viewModel::onIncludeInStatisticsToggle
                )
                MmSwitchRow(
                    title = "Reimbursable",
                    subtitle = "Track this until someone pays you back",
                    checked = state.reimbursable,
                    onCheckedChange = viewModel::onReimbursableToggle
                )
                Spacer(Modifier.height(MmSpacing.sm))
                MmTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = "Notes",
                    placeholder = "Optional",
                    singleLine = false,
                    minLines = 2
                )
            }

            state.error?.let {
                Text(it, color = MmColors.expense, style = MmType.caption)
            }

            MmPrimaryButton(
                text = "Save transaction",
                onClick = { viewModel.save(entryPoint) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MmColors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(MmSpacing.sm))
                Text(
                    "Tip: use a transfer to move money between your own accounts.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
        }
    }

    if (accountPickerOpen && !isCash) {
        MmPickerSheet(
            title = "Choose account",
            options = state.accounts.map { acct ->
                MmOption(
                    id = acct.id,
                    label = acct.nickname.ifBlank { acct.institutionName },
                    subtitle = acct.institutionName.takeIf { it.isNotBlank() && it != acct.nickname }
                )
            },
            selectedId = state.selectedAccountId,
            onSelect = { option ->
                viewModel.onAccountSelected(option.id)
                accountPickerOpen = false
            },
            onDismiss = { accountPickerOpen = false }
        )
    }
}
