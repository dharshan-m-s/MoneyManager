package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.reporting.FinancialSummary
import com.moneymanager.app.ui.components.AccountsFab
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.formatDayMonth
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    initialTab: AccountsTab = AccountsTab.BANK,
    onAccountClick: (Long) -> Unit = {},
    onAddCreditCard: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onAccountSettings: () -> Unit = {},
    onEditAccount: (Long) -> Unit = {},
    onEditCreditCard: (Long) -> Unit = onEditAccount,
    showInlineAdd: Boolean = true,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var selectedTab by remember { mutableIntStateOf(AccountsTab.entries.indexOf(initialTab).coerceAtLeast(0)) }
    var editMode by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }
    val tab = AccountsTab.entries[selectedTab]
    val filtered = accounts.filter { it.accountType in tab.types && !it.deleted }
    val visible = filtered.filter { !it.hide }
    // A disabled account is kept, not deleted - so it stays reachable. But it is kept on its
    // OWN screen, reached through the Active/Inactive switch: mixing disabled accounts into
    // the live list is what makes finding "my card" impossible once a few old cards pile up.
    val activeAccounts = visible.filter { it.active }
    val inactiveAccounts = visible.filter { !it.active }
    // With nothing inactive to show, the inactive view can never be the one on screen.
    val inactiveView = showInactive && inactiveAccounts.isNotEmpty()
    val shown = if (inactiveView) inactiveAccounts else activeAccounts
    // Total uses the same inclusion rule as every other combined total (FinancialSummary),
    // so the Accounts tab header reconciles with the dashboard: exclude accounts hidden from
    // statistics and child accounts (their money is represented by the parent). Inactive
    // accounts are outside the statistics by definition, so their view sums them directly.
    val total = if (inactiveView) {
        inactiveAccounts.sumOf { account ->
            if (account.accountType == AccountType.CREDIT_CARD) {
                account.outstandingMinorUnits ?: 0L
            } else {
                account.currentBalanceMinorUnits
            }
        }
    } else {
        filtered
            .filter { FinancialSummary.isIncluded(it) }
            .sumOf { account ->
                if (account.accountType == AccountType.CREDIT_CARD) {
                    account.outstandingMinorUnits ?: 0L
                } else {
                    account.currentBalanceMinorUnits
                }
            }
    }

    Scaffold(
        backgroundColor = MmColors.background,
        floatingActionButton = if (showInlineAdd) {
            {
                Box(Modifier.navigationBarsPadding()) {
                    if (tab == AccountsTab.CREDIT_CARDS) {
                        AccountsFab(onAddAccount = onAddCreditCard)
                    } else {
                        AccountsFab(onAddAccount = onAddAccount)
                    }
                }
            }
        } else {
            {}
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AccountsHeader(
                tabLabel = tab.label,
                isCreditCard = tab == AccountsTab.CREDIT_CARDS,
                count = shown.size,
                total = Money(total),
                showingInactive = inactiveView,
                editMode = editMode,
                onBack = onBack,
                onSettings = onAccountSettings,
                onToggleEdit = { editMode = !editMode }
            )

            // Every tab opens on the live accounts; the inactive view is a deliberate detour,
            // never a state you can land in by accident after switching tab.
            AccountsTabs(selected = selectedTab, onSelect = {
                selectedTab = it
                showInactive = false
            })

            // The switch between the live accounts and the ones the user has switched off.
            // It only appears when there is something inactive to switch to.
            if (inactiveAccounts.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    MmChip(
                        label = "Active (${activeAccounts.size})",
                        selected = !inactiveView,
                        onClick = { showInactive = false }
                    )
                    MmChip(
                        label = "Inactive (${inactiveAccounts.size})",
                        selected = inactiveView,
                        onClick = { showInactive = true }
                    )
                }
            }

            if (tab == AccountsTab.ALL && !inactiveView) {
                AllAccountTypesBrowser(
                    accounts = accounts,
                    onSelectType = { type ->
                        selectedTab = AccountsTab.entries.indexOf(AccountsTab.forType(type))
                        showInactive = false
                    },
                    onAddType = { type ->
                        when (type) {
                            AccountType.CREDIT_CARD -> onAddCreditCard()
                            else -> onAddAccount()
                        }
                    }
                )
            } else if (shown.isEmpty() && tab != AccountsTab.ALL) {
                val hasInactive = inactiveAccounts.isNotEmpty()
                val emptyTitle: String
                val emptyMessage: String
                val emptyActionLabel: String?
                val emptyAction: (() -> Unit)?
                if (inactiveView) {
                    emptyTitle = "No inactive accounts"
                    emptyMessage = "Accounts you switch off are kept here, out of your live list."
                    emptyActionLabel = null
                    emptyAction = null
                } else if (hasInactive) {
                    emptyTitle = "No active accounts"
                    emptyMessage = "Your ${inactiveAccounts.size} switched-off " +
                            "${if (inactiveAccounts.size == 1) "account is" else "accounts are"} " +
                            "in the Inactive view."
                    emptyActionLabel = "Show inactive"
                    emptyAction = { showInactive = true }
                } else {
                    emptyTitle = "No ${tab.label.lowercase()} yet"
                    emptyMessage = when (tab) {
                        AccountsTab.CREDIT_CARDS -> "Add a card to track outstanding, limits and billing cycles."
                        AccountsTab.DEBIT_CARDS -> "Add a debit card to track spending from your bank account."
                        AccountsTab.LOANS -> "Add a loan to keep track of what is left to pay."
                        AccountsTab.WALLETS -> "Add a wallet to track balances."
                        AccountsTab.PREPAID_CARDS -> "Add a prepaid card to track its balance."
                        AccountsTab.BANK -> "Add a bank account to track your balance and transactions."
                        AccountsTab.CASH -> "Add or manage your cash account."
                        AccountsTab.INVESTMENTS -> "Add an investment account to track it here."
                        AccountsTab.ALL -> "No accounts found for this selection."
                    }
                    emptyActionLabel = when (tab) {
                        AccountsTab.CREDIT_CARDS -> "Add credit card"
                        else -> "Add account"
                    }
                    emptyAction = when (tab) {
                        AccountsTab.CREDIT_CARDS -> onAddCreditCard
                        else -> onAddAccount
                    }
                }
                MmEmptyState(
                    icon = tab.icon,
                    title = emptyTitle,
                    message = emptyMessage,
                    actionLabel = emptyActionLabel,
                    onAction = emptyAction
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
                    itemsIndexed(shown, key = { _, account -> account.id }) { index, account ->
                        AccountCard(
                            account = account,
                            isCreditCard = account.accountType == AccountType.CREDIT_CARD,
                            editMode = editMode,
                            modifier = Modifier.revealOnAppear(index = index),
                            onClick = { onAccountClick(account.id) },
                            onEdit = {
                                if (account.accountType == AccountType.CREDIT_CARD) onEditCreditCard(account.id)
                                else onEditAccount(account.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

private val AccountsTab.icon: ImageVector
    get() = when (this) {
        AccountsTab.BANK -> Icons.Filled.AccountBalance
        AccountsTab.DEBIT_CARDS -> Icons.Filled.CreditCard
        AccountsTab.CREDIT_CARDS -> Icons.Filled.CreditCard
        AccountsTab.WALLETS -> Icons.Filled.AccountBalanceWallet
        AccountsTab.PREPAID_CARDS -> Icons.Filled.CreditCard
        AccountsTab.CASH -> Icons.Filled.Payments
        AccountsTab.INVESTMENTS -> Icons.Filled.AccountBalance
        AccountsTab.LOANS -> Icons.Filled.RequestQuote
        AccountsTab.ALL -> Icons.Filled.AccountBalance
    }

@Composable
private fun AccountsHeader(
    tabLabel: String,
    isCreditCard: Boolean,
    count: Int,
    total: Money,
    showingInactive: Boolean,
    editMode: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleEdit: () -> Unit
) {
    Surface(color = Color.Transparent, elevation = 0.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(MmColors.brandGradient))
        ) {
            // Soft corner light so the header reads as a lit surface, not a flat colour block.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(760f, 30f),
                            radius = 560f
                        )
                    )
            )
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Text(
                    "Accounts",
                    color = MMWhite,
                    style = MmType.screenTitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Account settings", tint = MMWhite)
                }
                IconButton(onClick = onToggleEdit) {
                    Icon(
                        if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                        contentDescription = if (editMode) "Done editing" else "Edit accounts",
                        tint = MMWhite
                    )
                }
            }
            Column(Modifier.padding(horizontal = MmSpacing.screen, vertical = MmSpacing.sm)) {
                Text(
                    when {
                        showingInactive && isCreditCard -> "Inactive outstanding"
                        showingInactive -> "Inactive balance"
                        isCreditCard -> "Total outstanding"
                        else -> "Total balance"
                    },
                    color = MMWhite.copy(alpha = 0.8f),
                    style = MmType.caption
                )
                MmAnimatedAmount(
                    value = total.minorUnits,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountHero,
                    color = MMWhite
                )
                Spacer(Modifier.size(MmSpacing.xs))
                Text(
                    "$count ${if (count == 1) "account" else "accounts"}" +
                            if (showingInactive) " • switched off" else "",
                    color = MMWhite.copy(alpha = 0.75f),
                    style = MmType.caption
                )
            }
            Spacer(Modifier.size(MmSpacing.sm))
        }
        }
    }
}

@Composable
private fun AccountsTabs(selected: Int, onSelect: (Int) -> Unit) {
    val primary = AccountsTab.primary
    val allIndex = AccountsTab.entries.indexOf(AccountsTab.ALL)
    val currentIndex = selected.coerceIn(0, AccountsTab.entries.size - 1)
    val isAll = currentIndex == allIndex
    Column(Modifier.fillMaxWidth().background(MmColors.surface).padding(horizontal = MmSpacing.sm)) {
        TabRow(
            selectedTabIndex = primary.indexOfFirst { AccountsTab.entries.indexOf(it) == currentIndex }.coerceAtLeast(0),
            backgroundColor = MmColors.surface,
            contentColor = MmColors.accent
        ) {
            primary.forEach { tab ->
                val actualIndex = AccountsTab.entries.indexOf(tab)
                Tab(selected = selected == actualIndex, onClick = { onSelect(actualIndex) }, text = {
                    Text(tab.label, fontSize = 12.sp, fontWeight = if (selected == actualIndex) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2)
                })
            }
        }
        TextButton(onClick = { onSelect(allIndex) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isAll) "All account types · selected" else if (currentIndex != allIndex && primary.none { AccountsTab.entries.indexOf(it) == currentIndex }) "${AccountsTab.entries[currentIndex].label} · Browse all account types" else "Browse all account types", fontSize = 14.sp)
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    isCreditCard: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amount = if (isCreditCard) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
    val subtitle = when {
        !account.active -> "Inactive"
        account.hide -> "Hidden"
        account.manualBalanceOverrideMinorUnits != null -> "Manual balance checkpoint"
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null ->
            "Reported ${formatDayMonth(account.lastReportedAtEpochMillis)}"
        // A card that already has a live statement shows the real outstanding; only a card
        // whose statement has not been computed yet still shows an estimate.
        isCreditCard && account.billAmountMinorUnits != null -> "Outstanding"
        isCreditCard -> "Estimated outstanding"
        else -> "Estimated balance"
    }

    Column(modifier.fillMaxWidth().background(MmColors.surface).padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(
                icon = typeIcon(account.accountType),
                tint = MmColors.accent,
                size = 44.dp
            )
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    account.nickname.ifBlank { account.institutionName },
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        account.institutionName.takeIf { it.isNotBlank() && it != account.nickname },
                        account.sourceAccountId.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (editMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = MmColors.accent)
                }
            }
        }
        Spacer(Modifier.size(MmSpacing.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MmAnimatedAmount(
                    value = amount,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountLarge,
                    color = MmColors.textPrimary
                )
                Text(subtitle, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
            }
            // A limit the user typed wins over the derived one, so the pill can never
            // contradict the limit shown on the card's own screen.
            val effectiveLimit = account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits
            if (isCreditCard && (effectiveLimit ?: 0L) > 0L) {
                MmPill(
                    "Limit ${MoneyFormat.rupeesNoDecimals(Money(effectiveLimit!!))}",
                    tint = MmColors.textSecondary
                )
            } else if (isCreditCard) {
                MmPill("Credit card", tint = MmColors.accent, filled = true)
            }
        }
    }
}

@Composable
private fun AllAccountTypesBrowser(
    accounts: List<AccountEntity>,
    onSelectType: (AccountType) -> Unit,
    onAddType: (AccountType) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        items(AccountTypeCatalog.all) { info ->
            val type = info.type
            val list = accounts.filter { it.accountType == type && !it.deleted && !it.hide }
            val count = list.size
            val total = list.filter { FinancialSummary.isIncluded(it) }.sumOf { account ->
                if (type == AccountType.CREDIT_CARD) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
            }
            MmCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(MmSpacing.md)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
                  ) {
                    MmIconBadge(
                        icon = typeIcon(type),
                        tint = MmColors.accent,
                        size = 44.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(info.pluralLabel, style = MmType.body, fontWeight = FontWeight.SemiBold, color = MmColors.textPrimary)
                        Text(
                            "$count ${if (count == 1) "account" else "accounts"} · ${MoneyFormat.rupeesNoDecimals(Money(total))}",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                    androidx.compose.material.TextButton(onClick = { onSelectType(type) }) { Text("View") }
                  }
                  androidx.compose.material.TextButton(onClick = { onAddType(type) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add ${info.singularLabel}")
                  }
                }
            }
        }
    }
}

private fun typeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.DEBIT_CARD -> Icons.Filled.CreditCard
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
    AccountType.WALLET -> Icons.Filled.AccountBalanceWallet
    AccountType.PREPAID_CARD -> Icons.Filled.CreditCard
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.INVESTMENT -> Icons.Filled.AccountBalance
    AccountType.LOAN -> Icons.Filled.RequestQuote
    AccountType.UNKNOWN -> Icons.Filled.AccountBalance
}
