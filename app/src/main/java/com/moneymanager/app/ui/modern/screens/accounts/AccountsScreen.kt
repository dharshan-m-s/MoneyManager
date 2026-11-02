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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.moneymanager.app.ui.components.LocalMmGlassEnabled
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.scrollCollapse
import com.moneymanager.app.ui.components.scrollCompactField
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
    var sortByBalance by rememberSaveable { mutableStateOf(false) }
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
    val unsortedShown = if (inactiveView) inactiveAccounts else activeAccounts
    val shown = if (sortByBalance) {
        unsortedShown.sortedByDescending { account ->
            if (account.accountType == AccountType.CREDIT_CARD) {
                account.outstandingMinorUnits ?: 0L
            } else account.currentBalanceMinorUnits
        }
    } else unsortedShown
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

    Column(Modifier.fillMaxSize().modernTopInsets().mmPageBackdrop()) {
        AccountsHeader(
            tabLabel = tab.label,
            isCreditCard = tab == AccountsTab.CREDIT_CARDS,
            count = shown.size,
            total = Money(total),
            visualValues = shown.map { account ->
                if (account.accountType == AccountType.CREDIT_CARD) {
                    account.outstandingMinorUnits ?: 0L
                } else {
                    account.currentBalanceMinorUnits
                }
            }.sorted(),
            showingInactive = inactiveView,
            editMode = editMode,
            collapseProgress = scrollChrome.progress,
            onBack = onBack,
            onSettings = onAccountSettings,
            onToggleEdit = { editMode = !editMode },
            showBack = false
        )

        CompositionLocalProvider(LocalMmGlassEnabled provides true) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs)
                    .mmLiquidGlass(cornerRadius = 22.dp, intensity = 0.42f)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    MmSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Search accounts",
                        glassField = true,
                        modifier = Modifier.scrollCompactField(scrollChrome.progress)
                    )
                }
                Spacer(Modifier.width(MmSpacing.xs))
                AccountsCircleAction(
                    icon = Icons.Filled.SwapVert,
                    contentDescription = if (sortByBalance) "Restore original account order" else "Sort accounts by balance",
                    onClick = { sortByBalance = !sortByBalance },
                    active = sortByBalance
                )
            }
        }

        CompositionLocalProvider(LocalMmGlassEnabled provides true) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs)
                    .mmLiquidGlass(cornerRadius = 22.dp, intensity = 0.34f)
            ) {
                AccountsTabs(selected = selectedTab, onSelect = {
                    selectedTab = it
                    showInactive = false
                })
            }
        }

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
                modifier = Modifier.weight(1f).nestedScroll(scrollChrome.connection),
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
                modifier = Modifier.weight(1f).nestedScroll(scrollChrome.connection),
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
    visualValues: List<Long>,
    showingInactive: Boolean,
    editMode: Boolean,
    collapseProgress: State<Float>,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggleEdit: () -> Unit,
    showBack: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalMmGlassEnabled provides true) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = MmSpacing.md, vertical = MmSpacing.xs)
                .mmLiquidGlass(cornerRadius = 24.dp, intensity = 0.62f)
                .scrollCollapse(collapseProgress)
                .padding(start = if (showBack) MmSpacing.sm else MmSpacing.lg, end = MmSpacing.lg, top = MmSpacing.sm, bottom = MmSpacing.sm),
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
        }

        MmBalancePanel(
            label = when {
                showingInactive && isCreditCard -> "Inactive outstanding"
                showingInactive -> "Inactive balance"
                isCreditCard -> "Total outstanding"
                else -> "Total balance"
            },
            value = MoneyFormat.rupeesNoDecimals(total),
            animatedValueMinorUnits = total.minorUnits,
            visualValues = visualValues,
            visualLabel = "Account mix",
            caption = "$count ${if (count == 1) "account" else "accounts"} · $tabLabel" +
                if (showingInactive) " · switched off" else "",
            artworkKind = "account_${tabLabel.lowercase()}",
            modifier = Modifier
                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.md)
                .scrollCollapse(collapseProgress)
        )
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
            .mmLiquidGlass(cornerRadius = 999.dp, intensity = 0.48f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) MmColors.accentFill.copy(alpha = 0.88f) else Color.Transparent,
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
    val selectedIndexInPrimary = primaryIndices.indexOf(selected)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        primary.forEachIndexed { index, tab ->
            val active = selectedIndexInPrimary == index
            val tint = if (active) MmColors.accent else MmColors.textSecondary
            Row(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (active) MmColors.accent.copy(alpha = 0.16f) else MmColors.surface)
                    .border(1.dp, if (active) MmColors.accent.copy(alpha = 0.58f) else MmColors.outline.copy(alpha = 0.62f), RoundedCornerShape(18.dp))
                    .pressableClick { onSelect(primaryIndices[index]) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(modernTypeIcon(tab.types.first()), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(tab.label, style = MmType.label, color = tint, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
        val allSelected = selected == allIndex || selectedIndexInPrimary < 0
        Row(
            Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (allSelected) MmColors.accent.copy(alpha = 0.16f) else MmColors.surface)
                .border(1.dp, if (allSelected) MmColors.accent.copy(alpha = 0.58f) else MmColors.outline.copy(alpha = 0.62f), RoundedCornerShape(18.dp))
                .pressableClick { onSelect(allIndex) }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = if (allSelected) MmColors.accent else MmColors.textSecondary, modifier = Modifier.size(16.dp))
            Text("All types", style = MmType.label, color = if (allSelected) MmColors.accent else MmColors.textSecondary, fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
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
        account.balanceSource == AccountBalanceSource.REPORTED && account.lastReportedAtEpochMillis != null ->
            "Reported ${formatDayMonth(account.lastReportedAtEpochMillis)}"
        isCreditCard -> "Outstanding"
        else -> "Available balance"
    }
    val institution = account.institutionName.takeIf { it.isNotBlank() && it != account.nickname }
    val isCard = AccountTypeCatalog.info(account.accountType).isCard
    val detail = if (isCard) institution.orEmpty() else {
        listOfNotNull(
            institution,
            com.moneymanager.app.ui.components.maskedCardNumberFromSourceId(account.sourceAccountId)
        ).joinToString(" • ")
    }
    val effectiveLimit = account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits
    val available = (effectiveLimit ?: 0L) - amount
    val usageFraction = if (isCreditCard && (effectiveLimit ?: 0L) > 0L) {
        (amount.toFloat() / effectiveLimit!!.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val animatedUsage by animateFloatAsState(
        targetValue = usageFraction ?: 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 260f),
        label = "accountCreditUsage"
    )
    val usageTint = when {
        (usageFraction ?: 0f) >= 0.90f -> MmColors.expense
        (usageFraction ?: 0f) >= 0.75f -> MmColors.warning
        else -> MmColors.accent
    }
    val tint = when (account.accountType) {
        AccountType.CREDIT_CARD -> MmColors.categoryPalette[5 % MmColors.categoryPalette.size]
        AccountType.CASH -> MmColors.income
        AccountType.LOAN -> MmColors.warning
        else -> MmColors.accent
    }

    Column(modifier.fillMaxWidth()) {
        if (showTopDivider && !isCard && account.accountType != AccountType.BANK) {
            Divider(
                modifier = Modifier.padding(start = 60.dp, end = MmSpacing.screen),
                color = MmColors.outline.copy(alpha = 0.55f),
                thickness = 0.5.dp
            )
        }

        if (isCard) {
            // Payment cards stay compact in the list. The full animated card is reserved for
            // the opened account detail screen, where it has room to breathe.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(19.dp))
                    .background(tint.copy(alpha = if (MaterialTheme.colors.isLight) 0.105f else 0.14f))
                    .border(0.85.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(19.dp))
                    .mmLiquidGlass(cornerRadius = 19.dp, intensity = 0.78f)
                    .mmSharedElement("account-${account.id}")
                    .clickable(onClick = if (editMode) onEdit else onClick)
                    .padding(horizontal = MmSpacing.md, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MmIconBadge(icon = modernTypeIcon(account.accountType), tint = tint, size = 44.dp)
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.nickname.ifBlank { account.institutionName.ifBlank { "Payment card" } },
                        style = MmType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(detail.takeIf { it.isNotBlank() }, subtitle).joinToString(" · "),
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(MmSpacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    com.moneymanager.app.ui.components.MmAnimatedAmount(
                        value = amount,
                        formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                        style = MmType.amountRow,
                        color = MmColors.textPrimary
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
                Spacer(Modifier.width(MmSpacing.xs))
                if (editMode) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit card", tint = MmColors.accent)
                    }
                } else {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Open card", tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            if (usageFraction != null) {
                Spacer(Modifier.height(MmSpacing.xs))
                Row(
                    Modifier.fillMaxWidth().padding(start = 60.dp, end = MmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CREDIT UTILISATION",
                        style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                        color = MmColors.textTertiary
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${(usageFraction * 100).toInt()}%", style = MmType.caption, color = usageTint)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.padding(start = 60.dp, end = MmSpacing.sm)
                        .fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(MmColors.surfaceContainerHigh)
                ) {
                    Box(
                        Modifier.fillMaxWidth(animatedUsage.coerceIn(0f, 1f))
                            .height(4.dp).clip(CircleShape).background(usageTint)
                    )
                }
            }
        } else if (account.accountType == AccountType.BANK) {
            // Bank accounts are ledger entries, not oversized payment cards. Keep the familiar
            // row density, give the institution mark a clear place, and let the balance lead.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(19.dp))
                    .background(MmColors.surface.copy(alpha = if (MaterialTheme.colors.isLight) 0.16f else 0.12f))
                    .border(0.75.dp, MmColors.outline.copy(alpha = 0.48f), RoundedCornerShape(19.dp))
                    .mmLiquidGlass(cornerRadius = 19.dp, intensity = 0.68f)
                    .mmSharedElement("account-${account.id}")
                    .clickable(onClick = if (editMode) onEdit else onClick)
                    .padding(horizontal = MmSpacing.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MmBankLogo(
                    account.institutionName.ifBlank { account.nickname },
                    Modifier.size(44.dp)
                )
                Spacer(Modifier.width(MmSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.nickname.ifBlank { account.institutionName.ifBlank { "Bank account" } },
                        style = MmType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(detail.takeIf { it.isNotBlank() }, subtitle).joinToString(" • "),
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(MmSpacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    com.moneymanager.app.ui.components.MmAnimatedAmount(
                        value = amount,
                        formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                        style = MmType.amountRow,
                        color = MmColors.textPrimary
                    )
                    Text(
                        if (!account.active) "INACTIVE" else "AVAILABLE",
                        style = MmType.caption.copy(fontSize = 8.sp, letterSpacing = 0.6.sp),
                        color = MmColors.textTertiary
                    )
                }
                Spacer(Modifier.width(MmSpacing.xs))
                if (editMode) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit bank account", tint = MmColors.accent)
                    }
                } else {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open bank account",
                        tint = MmColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(tint.copy(alpha = if (MaterialTheme.colors.isLight) 0.095f else 0.13f))
                    .border(0.85.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .mmLiquidGlass(cornerRadius = 20.dp, intensity = 0.78f)
                    .mmSharedElement("account-${account.id}")
                    .clickable(onClick = if (editMode) onEdit else onClick)
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MmIconBadge(icon = modernTypeIcon(account.accountType), tint = tint, size = 44.dp)
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
                }
                Spacer(Modifier.width(MmSpacing.sm))
                com.moneymanager.app.ui.components.MmAnimatedAmount(
                    value = amount,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountRow,
                    color = MmColors.textPrimary
                )
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
            .border(1.dp, MmColors.accentFill.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .mmLiquidGlass(cornerRadius = 18.dp, intensity = 0.62f)
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
                    .background(tint.copy(alpha = if (MaterialTheme.colors.isLight) 0.085f else 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .mmLiquidGlass(cornerRadius = 18.dp, intensity = 0.68f)
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
