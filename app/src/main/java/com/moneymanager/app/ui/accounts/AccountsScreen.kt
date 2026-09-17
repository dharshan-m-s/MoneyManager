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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.AccountsFab
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.formatDayMonth
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
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var selectedTab by remember { mutableIntStateOf(AccountsTab.entries.indexOf(initialTab).coerceAtLeast(0)) }
    var editMode by remember { mutableStateOf(false) }
    val tab = AccountsTab.entries[selectedTab]
    val filtered = accounts.filter { it.accountType in tab.types && !it.deleted }
    val visible = filtered.filter { !it.hide }
    val total = visible
        .filter { it.active }
        .sumOf { account ->
            if (account.accountType == AccountType.CREDIT_CARD) {
                account.outstandingMinorUnits ?: 0L
            } else {
                account.currentBalanceMinorUnits
            }
        }

    Scaffold(
        backgroundColor = MmColors.background,
        floatingActionButton = {
            Box(Modifier.navigationBarsPadding()) {
                if (tab == AccountsTab.CREDIT_CARDS) {
                    AccountsFab(onAddAccount = onAddCreditCard)
                } else {
                    AccountsFab(onAddAccount = onAddAccount)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AccountsHeader(
                tabLabel = tab.label,
                isCreditCard = tab == AccountsTab.CREDIT_CARDS,
                count = visible.size,
                total = Money(total),
                editMode = editMode,
                onBack = onBack,
                onSettings = onAccountSettings,
                onToggleEdit = { editMode = !editMode }
            )

            AccountsTabs(selected = selectedTab, onSelect = { selectedTab = it })

            if (visible.isEmpty()) {
                MmEmptyState(
                    icon = tab.icon,
                    title = "No ${tab.label.lowercase()} yet",
                    message = when (tab) {
                        AccountsTab.CREDIT_CARDS -> "Add a card to track outstanding, limits and billing cycles."
                        AccountsTab.LOANS -> "Add a loan to keep track of what is left to pay."
                        AccountsTab.WALLETS -> "Add a wallet to track prepaid balances."
                        AccountsTab.BANK -> "Add a bank account to track your balance and transactions."
                    },
                    actionLabel = if (tab == AccountsTab.CREDIT_CARDS) "Add credit card" else "Add account",
                    onAction = if (tab == AccountsTab.CREDIT_CARDS) onAddCreditCard else onAddAccount
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
                    items(visible, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            isCreditCard = account.accountType == AccountType.CREDIT_CARD,
                            editMode = editMode,
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
        AccountsTab.CREDIT_CARDS -> Icons.Filled.CreditCard
        AccountsTab.WALLETS -> Icons.Filled.AccountBalanceWallet
        AccountsTab.LOANS -> Icons.Filled.RequestQuote
    }

@Composable
private fun AccountsHeader(
    tabLabel: String,
    isCreditCard: Boolean,
    count: Int,
    total: Money,
    editMode: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleEdit: () -> Unit
) {
    Surface(color = MMGreenDark, elevation = 0.dp) {
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
                    if (isCreditCard) "Total outstanding" else "Total balance",
                    color = MMWhite.copy(alpha = 0.8f),
                    style = MmType.caption
                )
                Text(
                    MoneyFormat.rupeesNoDecimals(total),
                    color = MMWhite,
                    style = MmType.amountHero,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(MmSpacing.xs))
                Text(
                    "$count ${if (count == 1) "account" else "accounts"}",
                    color = MMWhite.copy(alpha = 0.75f),
                    style = MmType.caption
                )
            }
            Spacer(Modifier.size(MmSpacing.sm))
        }
    }
}

@Composable
private fun AccountsTabs(selected: Int, onSelect: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selected,
        backgroundColor = MmColors.surface,
        contentColor = MmColors.accent
    ) {
        AccountsTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    isCreditCard: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val amount = if (isCreditCard) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits
    val subtitle = when {
        !account.active -> "Inactive"
        account.hide -> "Hidden"
        account.manualBalanceOverrideMinorUnits != null -> "Manual balance checkpoint"
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null ->
            "Reported ${formatDayMonth(account.lastReportedAtEpochMillis)}"
        isCreditCard -> "Estimated outstanding"
        else -> "Estimated balance"
    }

    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(
                icon = if (isCreditCard) Icons.Filled.CreditCard else Icons.Filled.AccountBalance,
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
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(amount)),
                    style = MmType.amountLarge,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
            }
            if (isCreditCard && (account.creditLimitMinorUnits ?: 0L) > 0L) {
                MmPill(
                    "Limit ${MoneyFormat.rupeesNoDecimals(Money(account.creditLimitMinorUnits!!))}",
                    tint = MmColors.textSecondary
                )
            } else if (isCreditCard) {
                MmPill("Credit card", tint = MmColors.accent, filled = true)
            }
        }
    }
}
