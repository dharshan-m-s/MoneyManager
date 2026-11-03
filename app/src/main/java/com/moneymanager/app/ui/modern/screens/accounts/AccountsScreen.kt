package com.moneymanager.app.ui.modern.screens.accounts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import com.moneymanager.app.ui.accounts.AccountsTab
import com.moneymanager.app.ui.accounts.AccountsViewModel
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.scrollCollapse
import com.moneymanager.app.ui.components.scrollCompactField
import com.moneymanager.app.ui.components.scrollCompactHeight
import com.moneymanager.app.ui.components.MmWalletCard
import com.moneymanager.app.ui.components.MmBankLogo
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.mmGlassHighlight
import com.moneymanager.app.ui.components.mmLiquidGlass
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSegmentedControl
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.formatDayMonth
import com.moneymanager.app.ui.components.modernTopInsets
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.mmSharedElement
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.mmTopBarSurface
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.pressScale
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import androidx.compose.material.TextButton

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
    var selectedTab by rememberSaveable { mutableIntStateOf(AccountsTab.entries.indexOf(initialTab).coerceAtLeast(0)) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scrollChrome = rememberScrollChromeState(collapseDistance = 132.dp)
    val tab = AccountsTab.entries[selectedTab]
    val filtered = accounts.filter { it.accountType in tab.types && !it.deleted }
    val visible = filtered.filter { account ->
        !account.hide && (
            query.isBlank() ||
                account.nickname.contains(query.trim(), ignoreCase = true) ||
                account.institutionName.contains(query.trim(), ignoreCase = true) ||
                account.sourceAccountId.contains(query.trim(), ignoreCase = true)
            )
    }
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

    Column(Modifier.fillMaxSize().mmPageBackdrop()) {
        AccountsHeader(
            showingInactive = inactiveView,
            editMode = editMode,
            collapseProgress = scrollChrome.progress,
            query = query,
            onQueryChange = { query = it },
            onBack = onBack,
            onSettings = onAccountSettings,
            onToggleEdit = { editMode = !editMode },
            showBack = false
        )

        val accountsStickyHeader: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth()) {
                MmBalancePanel(
                    label = when {
                        inactiveView && tab == AccountsTab.CREDIT_CARDS -> "Inactive outstanding"
                        inactiveView -> "Inactive balance"
                        tab == AccountsTab.CREDIT_CARDS -> "Total outstanding"
                        else -> "Total balance"
                    },
                    value = MoneyFormat.rupeesNoDecimals(Money(total)),
                    animatedValueMinorUnits = total,
                    visualValues = shown.map { account ->
                        if (account.accountType == AccountType.CREDIT_CARD) {
                            account.outstandingMinorUnits ?: 0L
                        } else account.currentBalanceMinorUnits
                    }.sorted(),
                    visualLabel = "Account mix",
                    caption = "${shown.size} ${if (shown.size == 1) "account" else "accounts"} · ${tab.label}" +
                        if (inactiveView) " · switched off" else "",
                    artworkKind = "account_${tab.label.lowercase()}",
                    glass = true,
                    compactProgress = scrollChrome.progress,
                    modifier = Modifier.padding(vertical = MmSpacing.md)
                )
                Box(
                    Modifier.fillMaxWidth()
                        .padding(vertical = MmSpacing.xs)
                        .scrollCollapse(scrollChrome.progress)
                ) {
                    AccountsTabs(
                        selected = selectedTab,
                        onSelect = {
                            selectedTab = it
                            showInactive = false
                        },
                        horizontalInset = 0.dp
                    )
                }
                if (inactiveAccounts.isNotEmpty()) {
                    AnimatedVisibility(visible = !scrollChrome.collapsed.value) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = MmSpacing.sm),
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
                }
            }
        }

        if (tab == AccountsTab.ALL && !inactiveView) {
            ModernAllAccountTypesBrowser(
                modifier = Modifier.weight(1f).nestedScroll(scrollChrome.connection),
                accounts = accounts,
                headerContent = accountsStickyHeader,
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
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).nestedScroll(scrollChrome.connection),
                contentPadding = PaddingValues(
                    start = MmSpacing.screen,
                    end = MmSpacing.screen,
                    bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                stickyHeader(key = "accounts-overview") {
                    accountsStickyHeader()
                }
                if (shown.isEmpty() && tab != AccountsTab.ALL) {
                    item(key = "accounts-empty") {
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
                                AccountsTab.DEBIT_CARDS -> "Add a debit card to track spending from a bank account."
                                AccountsTab.LOANS -> "Add a loan to keep track of what is left to pay."
                                AccountsTab.WALLETS -> "Add a wallet to track balances."
                                AccountsTab.PREPAID_CARDS -> "Add a prepaid card to track its balance."
                                AccountsTab.BANK -> "Add a bank account to track your balance and transactions."
                                AccountsTab.CASH -> "Add or manage your cash account."
                                AccountsTab.INVESTMENTS -> "Add an investment account to track it here."
                                AccountsTab.ALL -> "No accounts found for this selection."
                            }
                            emptyActionLabel = if (tab == AccountsTab.CREDIT_CARDS) "Add credit card" else "Add account"
                            emptyAction = if (tab == AccountsTab.CREDIT_CARDS) onAddCreditCard else onAddAccount
                        }
                        MmEmptyState(
                            icon = tab.icon,
                            title = emptyTitle,
                            message = emptyMessage,
                            actionLabel = emptyActionLabel,
                            onAction = emptyAction
                        )
                    }
                } else {
                    itemsIndexed(shown, key = { _, account -> account.id }) { index, account ->
                        AccountRow(
                            account = account,
                            modifier = Modifier.revealOnAppear(index = index),
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
                    if (showInlineAdd && !editMode) {
                        item(key = "add-account") {
                            Box(Modifier.padding(horizontal = MmSpacing.sm)) {
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
    showingInactive: Boolean,
    editMode: Boolean,
    collapseProgress: State<Float>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleEdit: () -> Unit,
    showBack: Boolean = true
) {
    CompositionLocalProvider(LocalMmGlassEnabled provides true) {
        Column(
            Modifier
                .fillMaxWidth()
                .mmLiquidGlass(cornerRadius = 24.dp, intensity = 1.08f, blurMultiplier = 1.4f)
                .modernTopInsets()
                .padding(horizontal = MmSpacing.sm)
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .scrollCompactHeight(
                    collapseProgress,
                    expandedHeight = 58.dp,
                    compactHeight = 48.dp
                )
                .padding(
                    start = if (showBack) MmSpacing.sm else MmSpacing.md,
                    end = MmSpacing.md,
                    top = MmSpacing.sm,
                    bottom = MmSpacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MmColors.textPrimary)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = if (showBack) MmSpacing.xs else 0.dp)
            ) {
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
            Spacer(Modifier.width(MmSpacing.xs))
            AccountsCircleAction(
                icon = if (editMode) Icons.Filled.Done else Icons.Filled.Edit,
                contentDescription = if (editMode) "Done editing" else "Edit accounts",
                onClick = onToggleEdit,
                active = editMode
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MmSearchField(
                value = query,
                onValueChange = onQueryChange,
                hint = "Search accounts",
                glassField = true,
                modifier = Modifier.fillMaxWidth()
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
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) MmColors.accent else MmColors.textPrimary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun AccountsTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    horizontalInset: androidx.compose.ui.unit.Dp = MmSpacing.screen
) {
    val primary = AccountsTab.primary
    val primaryIndices = primary.map { AccountsTab.entries.indexOf(it) }
    val allIndex = AccountsTab.entries.indexOf(AccountsTab.ALL)
    val selectedIndexInPrimary = primaryIndices.indexOf(selected)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalInset, vertical = MmSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        primary.forEachIndexed { index, tab ->
            val active = selectedIndexInPrimary == index
            Text(
                tab.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) MmColors.accent.copy(alpha = 0.13f) else Color.Transparent)
                    .pressableClick { onSelect(primaryIndices[index]) }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                style = MmType.label,
                color = if (active) MmColors.accent else MmColors.textSecondary,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
        val allSelected = selected == allIndex || selectedIndexInPrimary < 0
        Text(
            "All",
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (allSelected) MmColors.accent.copy(alpha = 0.13f) else Color.Transparent)
                .pressableClick { onSelect(allIndex) }
                .padding(horizontal = 13.dp, vertical = 9.dp),
            style = MmType.label,
            color = if (allSelected) MmColors.accent else MmColors.textSecondary,
            fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
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
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null ->
            "Reported ${formatDayMonth(account.lastReportedAtEpochMillis)}"
        isCreditCard -> "Outstanding"
        else -> "Available balance"
    }
    val issuer = account.institutionName.orEmpty().ifBlank { account.nickname.orEmpty() }
    val masked = com.moneymanager.app.ui.components.maskedCardNumberFromSourceId(account.sourceAccountId)
    val secondary = listOfNotNull(
        issuer.takeIf { it.isNotBlank() && it != account.nickname },
        masked?.takeIf { it.isNotBlank() },
        subtitle
    ).joinToString(" · ")
    val tint = when (account.accountType) {
        AccountType.CREDIT_CARD -> MmColors.categoryPalette[5 % MmColors.categoryPalette.size]
        AccountType.CASH -> MmColors.income
        AccountType.LOAN -> MmColors.warning
        else -> MmColors.accent
    }

    Column(modifier.fillMaxWidth()) {
        if (showTopDivider) {
            Divider(
                modifier = Modifier.padding(start = 58.dp, end = MmSpacing.screen),
                color = MmColors.outline.copy(alpha = 0.28f),
                thickness = 0.65.dp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = if (editMode) onEdit else onClick)
                .padding(horizontal = MmSpacing.sm, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.accountType == AccountType.BANK) {
                MmBankLogo(issuer, Modifier.size(42.dp))
            } else {
                MmIconBadge(icon = modernTypeIcon(account.accountType), tint = tint, size = 42.dp)
            }
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    account.nickname.ifBlank { issuer.ifBlank { "Account" } },
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    secondary,
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MmSpacing.sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                com.moneymanager.app.ui.components.MmAnimatedAmount(
                    value = amount,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountRow,
                    color = MmColors.textPrimary
                )
                Text(
                    if (isCreditCard) "OUTSTANDING" else "BALANCE",
                    style = MmType.caption.copy(fontSize = 8.sp, letterSpacing = 0.65.sp),
                    color = MmColors.textTertiary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(MmSpacing.xs))
            if (editMode) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = MmColors.accent)
                }
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open account", tint = MmColors.textTertiary, modifier = Modifier.size(18.dp))
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
            .background(MmColors.accentFill.copy(alpha = if (MaterialTheme.colors.isLight) 0.10f else 0.14f))
            .background(MmColors.surfaceContainer)
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
    modifier: Modifier = Modifier,
    accounts: List<AccountEntity>,
    headerContent: @Composable () -> Unit,
    onSelectType: (AccountType) -> Unit,
    onAddType: (AccountType) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = MmSpacing.screen,
            top = MmSpacing.sm,
            end = MmSpacing.screen,
            bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        stickyHeader(key = "all-account-types-header") {
            headerContent()
        }
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
                    .background(MmColors.surfaceContainer)
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
