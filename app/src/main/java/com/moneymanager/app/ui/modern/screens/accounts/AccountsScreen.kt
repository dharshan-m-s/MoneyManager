package com.moneymanager.app.ui.modern.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.reporting.FinancialSummary
import com.moneymanager.app.ui.accounts.AccountsTab
import com.moneymanager.app.ui.accounts.AccountsViewModel
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.formatDayMonth
import com.moneymanager.app.ui.components.modernTopInsets
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.pressScale
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import androidx.compose.material.TextButton
import androidx.compose.material.icons.filled.MoreHoriz

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
    // The dock Add is transaction-only, so the Accounts list keeps its own add row for
    // accounts and credit cards. Hidden only for embedded/read-only presentations.
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
    val activeAccounts = visible.filter { it.active }
    val inactiveAccounts = visible.filter { !it.active }
    val inactiveView = showInactive && inactiveAccounts.isNotEmpty()
    val shown = if (inactiveView) inactiveAccounts else activeAccounts
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

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        AccountsHeader(
            tabLabel = tab.label,
            isCreditCard = tab == AccountsTab.CREDIT_CARDS,
            count = shown.size,
            total = Money(total),
            showingInactive = inactiveView,
            editMode = editMode,
            onBack = onBack,
            onSettings = onAccountSettings,
            onToggleEdit = { editMode = !editMode },
            showBack = false
        )

        AccountsTabs(selected = selectedTab, onSelect = {
            selectedTab = it
            showInactive = false
        })

        if (inactiveAccounts.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MmSpacing.screen, vertical = MmSpacing.sm),
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
            ModernAllAccountTypesBrowser(
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
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MmSpacing.screen,
                    end = MmSpacing.screen,
                    top = MmSpacing.sm,
                    bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(shown, key = { _, account -> account.id }) { index, account ->
                    AccountRow(
                account = account,
                isCreditCard = account.accountType == AccountType.CREDIT_CARD,
                editMode = editMode,
                showTopDivider = index > 0,
                onClick = { onAccountClick(account.id) },
                        onEdit = {
                            if (account.accountType == AccountType.CREDIT_CARD) onEditCreditCard(account.id)
                            else onEditAccount(account.id)
                        }
                    )
                }
                if (showInlineAdd) {
                if (!editMode) {
                    item {
                        AddAccountRow(
                            label = if (tab == AccountsTab.CREDIT_CARDS) "Add credit card" else "Add account",
                            onClick = if (tab == AccountsTab.CREDIT_CARDS) onAddCreditCard else onAddAccount
                        )
                    }
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
    onToggleEdit: () -> Unit,
    showBack: Boolean = true
) {
    Column(Modifier.fillMaxWidth().background(MmColors.background).modernTopInsets()) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (showBack) MmSpacing.sm else MmSpacing.lg, end = MmSpacing.lg, top = MmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MmColors.textPrimary)
                }
            }
            Column(Modifier.weight(1f).padding(start = if (showBack) MmSpacing.xs else 0.dp)) {
                Text("Accounts", style = MmType.screenTitle, color = MmColors.textPrimary)
                Text(
                    if (showingInactive) "Switched-off accounts" else "Manage all your accounts",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            AccountsCircleAction(
                icon = Icons.Filled.Settings,
                contentDescription = "Account settings",
                onClick = onSettings
            )
            Spacer(Modifier.width(MmSpacing.sm))
            AccountsCircleAction(
                icon = if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                contentDescription = if (editMode) "Done editing" else "Edit accounts",
                onClick = onToggleEdit,
                active = editMode
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md),
            shape = MmShape.card,
            color = MmColors.accentFill
        ) {
            Column(Modifier.fillMaxWidth().padding(MmSpacing.xl)) {
                Text(
                    when {
                        showingInactive && isCreditCard -> "Inactive outstanding"
                        showingInactive -> "Inactive balance"
                        isCreditCard -> "Total outstanding"
                        else -> "Total balance"
                    },
                    style = MmType.label,
                    color = MmColors.onAccent.copy(alpha = 0.78f)
                )
                Spacer(Modifier.height(6.dp))
                MmAnimatedAmount(
                    value = total.minorUnits,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountHero,
                    color = MmColors.onAccent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$count ${if (count == 1) "account" else "accounts"} · $tabLabel" +
                        if (showingInactive) " · switched off" else "",
                    style = MmType.caption,
                    color = MmColors.onAccent.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun AccountsCircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) MmColors.accentFill else MmColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MmColors.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) MmColors.onAccent else MmColors.textPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun AccountsTabs(selected: Int, onSelect: (Int) -> Unit) {
    val primary = AccountsTab.primary
    val primaryIndices = primary.map { AccountsTab.entries.indexOf(it) }
    val allIndex = AccountsTab.entries.indexOf(AccountsTab.ALL)
    val selectedIndexInPrimary = primaryIndices.indexOf(selected).coerceAtLeast(-1)
    Column {
        MmSegmentedControl(
            options = primary.map { it.label },
            selectedIndex = if (selected == allIndex) -1 else selectedIndexInPrimary,
            onSelect = { onSelect(primaryIndices[it]) },
            modifier = Modifier.padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            val selAll = selected == allIndex
            val selectedHiddenLabel = primaryIndices.none { it == selected } && !selAll
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selAll) MmColors.accent.copy(alpha = 0.16f) else MmColors.surfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (selAll) MmColors.accent.copy(alpha = 0.6f) else MmColors.outline,
                        shape = RoundedCornerShape(50)
                    )
                    .pressableClick { onSelect(allIndex) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = if (selAll) MmColors.accent else MmColors.textSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        when {
                            selAll -> "All account types · selected"
                            selectedHiddenLabel -> "${AccountsTab.entries[selected].label} · Browse all account types"
                            else -> "Browse all account types"
                        },
                        style = MmType.body,
                        color = if (selAll) MmColors.accent else MmColors.textSecondary,
                        fontWeight = if (selAll) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    isCreditCard: Boolean,
    editMode: Boolean,
    showTopDivider: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amount = if (isCreditCard) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
    val subtitle = when {
        !account.active -> "Inactive"
        account.hide -> "Hidden from totals"
        account.manualBalanceOverrideMinorUnits != null -> "Manual balance"
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null ->
            "Reported ${formatDayMonth(account.lastReportedAtEpochMillis)}"
        isCreditCard -> "Outstanding"
        else -> "Available balance"
    }
    val institution = account.institutionName.takeIf { it.isNotBlank() && it != account.nickname }
    val detail = listOfNotNull(institution, account.sourceAccountId.takeIf { it.isNotBlank() })
        .joinToString(" • ")
    val effectiveLimit = account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits
    val available = (effectiveLimit ?: 0L) - amount
    val tint = when (account.accountType) {
        AccountType.CREDIT_CARD -> MmColors.categoryPalette[5 % MmColors.categoryPalette.size]
        AccountType.CASH -> MmColors.income
        AccountType.LOAN -> MmColors.warning
        else -> MmColors.accent
    }

    Column(modifier.fillMaxWidth()) {
        if (showTopDivider) {
            Divider(
                modifier = Modifier.padding(start = 60.dp, end = MmSpacing.screen),
                color = MmColors.outline.copy(alpha = 0.55f),
                thickness = 0.5.dp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MmIconBadge(
                icon = modernTypeIcon(account.accountType),
                tint = tint,
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
                    if (detail.isNotBlank()) "$detail · $subtitle" else subtitle,
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCreditCard && (effectiveLimit ?: 0L) > 0L) {
                    Text(
                        "Available ${MoneyFormat.rupeesNoDecimals(Money(available.coerceAtLeast(0L)))}",
                        style = MmType.caption,
                        color = MmColors.textTertiary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(MmSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(amount)),
                    style = MmType.amountRow,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                if (isCreditCard) {
                    Text("Outstanding", style = MmType.caption, color = MmColors.textTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.width(MmSpacing.xs))
            if (editMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = MmColors.accent)
                }
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddAccountRow(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(MmColors.accentFill.copy(alpha = 0.16f), MmColors.surface)))
            .border(1.dp, MmColors.accentFill.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MmColors.accentFill),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MmColors.onAccent)
            }
            Text(label, style = MmType.body, fontWeight = FontWeight.SemiBold, color = MmColors.textPrimary)
        }
    }
}

@Composable
private fun ModernAllAccountTypesBrowser(
    accounts: List<AccountEntity>,
    onSelectType: (AccountType) -> Unit,
    onAddType: (AccountType) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = MmSpacing.screen,
            top = MmSpacing.sm,
            end = MmSpacing.screen,
            bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        items(AccountTypeCatalog.all) { info ->
            val type = info.type
            val list = accounts.filter { it.accountType == type && !it.deleted && !it.hide }
            val count = list.size
            val total = list.filter { FinancialSummary.isIncluded(it) }.sumOf { account ->
                if (type == AccountType.CREDIT_CARD) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
            }
            val tint = when (type) {
                AccountType.CREDIT_CARD -> MmColors.categoryPalette[5 % MmColors.categoryPalette.size]
                AccountType.CASH -> MmColors.income
                AccountType.LOAN -> MmColors.warning
                else -> MmColors.accent
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.12f), MmColors.surface)))
                    .border(1.dp, tint.copy(alpha = 0.26f), RoundedCornerShape(18.dp))
            ) {
                Column(Modifier.fillMaxWidth().padding(MmSpacing.md)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
                  ) {
                    MmIconBadge(
                        icon = modernTypeIcon(type),
                        tint = tint,
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
                  androidx.compose.material.TextButton(onClick = { onAddType(type) }, modifier = Modifier.fillMaxWidth()) { Text("Add ${info.singularLabel}") }
                }
            }
        }
    }
}

private fun modernTypeIcon(type: AccountType): ImageVector = when (type) {
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
