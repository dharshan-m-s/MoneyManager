package com.moneymanager.app.ui.modern.screens.accountdetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.modern.components.MmAnimatedAmount
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmEmptyState
import com.moneymanager.app.ui.modern.components.MmIconBadge
import com.moneymanager.app.ui.modern.components.MmPill
import com.moneymanager.app.ui.modern.components.MmProgressBar
import com.moneymanager.app.ui.modern.components.MmSearchField
import com.moneymanager.app.ui.modern.components.MmTopBarAction
import com.moneymanager.app.ui.modern.components.MoneyManagerTopBar
import com.moneymanager.app.ui.modern.components.MmTransactionRow
import com.moneymanager.app.ui.modern.components.MoneyFormat
import com.moneymanager.app.ui.modern.components.formatFullDate
import com.moneymanager.app.ui.modern.components.pressableClick
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import com.moneymanager.app.ui.accountdetail.AccountDetailUiState
import com.moneymanager.app.ui.accountdetail.AccountDetailViewModel

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onEditAccount: (Long) -> Unit = {},
    onEditCreditCard: (Long) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    onPayCard: () -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by remember(accountId) { viewModel.stateFor(accountId) }.collectAsState()
    val account = state.account
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // One add control with an explicit choice, matching the dashboard and cash screens, so the
    // same gesture means the same thing everywhere and no white-on-light-green icon appears in
    // dark mode.
    var addExpanded by remember { mutableStateOf(false) }

    Scaffold(
        backgroundColor = MmColors.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = MmSpacing.sm)
            ) {
                if (addExpanded) {
                    // On a credit card the same two gestures mean different things: a purchase
                    // grows the balance, and money coming back is a refund that clears it. The
                    // labels say so, so nobody records a card refund expecting income.
                    if (state.isCreditCard) {
                        AccountAddOption("Pay this card", Icons.Filled.Payments, MmColors.accent, accent = true) {
                            addExpanded = false
                            onPayCard()
                        }
                        AccountAddOption("Record a refund", Icons.AutoMirrored.Filled.CallReceived, MmColors.income, accent = false) {
                            addExpanded = false
                            onAddAccountIncome()
                        }
                        AccountAddOption("Add a purchase", Icons.Filled.Add, MmColors.expense, accent = false) {
                            addExpanded = false
                            onAddAccountSpend()
                        }
                    } else {
                        AccountAddOption("Add spend", Icons.Filled.Add, MmColors.expense, accent = false) {
                            addExpanded = false
                            onAddAccountSpend()
                        }
                        AccountAddOption("Add income", Icons.AutoMirrored.Filled.CallReceived, MmColors.income, accent = true) {
                            addExpanded = false
                            onAddAccountIncome()
                        }
                    }
                    Spacer(Modifier.height(MmSpacing.sm))
                }
                FloatingActionButton(
                    onClick = { addExpanded = !addExpanded },
                    backgroundColor = MmColors.accentFill,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        if (addExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (addExpanded) "Close add menu" else "Add transaction",
                        tint = MmColors.onAccent
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AccountHeader(
                state = state,
                onBack = onBack,
                onEdit = {
                    account?.let { if (state.isCreditCard) onEditCreditCard(it.id) else onEditAccount(it.id) }
                },
                onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) query = "" }
            )

            if (state.isCreditCard) {
                CreditCardTabs(selected = tab, onSelect = { tab = it })
            }

            if (searchOpen) {
                Box(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
                    MmSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Search this account"
                    )
                }
            }

            val matches: (List<TransactionEntity>) -> List<TransactionEntity> = { list ->
                if (query.isBlank()) list else list.filter { txn ->
                    val haystack = listOfNotNull(
                        txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType
                    ).joinToString(" ")
                    haystack.contains(query.trim(), ignoreCase = true)
                }
            }

            when {
                account == null -> MmEmptyState(
                    icon = Icons.Filled.AccountBalance,
                    title = "Account unavailable",
                    message = "This account could not be loaded."
                )
                state.isCreditCard && tab == 0 -> CurrentCycleList(
                    state = state,
                    onTransactionClick = onTransactionClick,
                    matches = matches
                )
                state.isCreditCard && tab == 1 -> GroupedList(
                    groups = state.monthGroups.map { group ->
                        PeriodView(
                            label = group.label,
                            income = Money(group.income),
                            expense = Money(group.expense),
                            transactions = matches(group.transactions)
                        )
                    },
                    incomeLabel = "Payments",
                    expenseLabel = "Spending",
                    onTransactionClick = onTransactionClick,
                    canLoadMore = state.canLoadMore,
                    onLoadMore = viewModel::loadMore,
                    emptyMessage = "No transactions recorded for this card yet."
                )
                state.isCreditCard -> GroupedList(
                    groups = state.cycleGroups.map { cycle ->
                        PeriodView(
                            label = cycle.label,
                            badge = if (cycle.isCurrent) "Current" else null,
                            income = Money(cycle.income),
                            expense = Money(cycle.expense),
                            transactions = matches(cycle.transactions)
                        )
                    },
                    incomeLabel = "Payments",
                    expenseLabel = "Spending",
                    onTransactionClick = onTransactionClick,
                    canLoadMore = state.canLoadMore,
                    onLoadMore = viewModel::loadMore,
                    emptyMessage = "No billing cycles recorded for this card yet."
                )
                else -> GroupedList(
                    groups = state.monthGroups.map { group ->
                        PeriodView(
                            label = group.label,
                            income = Money(group.income),
                            expense = Money(group.expense),
                            transactions = matches(group.transactions)
                        )
                    },
                    incomeLabel = "In",
                    expenseLabel = "Out",
                    onTransactionClick = onTransactionClick,
                    canLoadMore = state.canLoadMore,
                    onLoadMore = viewModel::loadMore,
                    emptyMessage = "No transactions for this account yet."
                )
            }
        }
    }
}

private data class PeriodView(
    val label: String,
    val income: Money,
    val expense: Money,
    val transactions: List<TransactionEntity>,
    val badge: String? = null
)

@Composable
private fun AccountAddOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    accent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(bottom = MmSpacing.sm)
            .clip(RoundedCornerShape(MmSpacing.touchTarget / 2))
            .background(MmColors.surface)
            .pressableClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        Text(label, style = MmType.label, color = MmColors.textPrimary)
        MmIconBadge(icon = icon, tint = if (accent) MmColors.accent else tint, size = 36.dp)
    }
}

@Composable
private fun AccountHeader(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleSearch: () -> Unit
) {
    val isCard = state.isCreditCard
    val account = state.account

    Column(Modifier.fillMaxWidth()) {
        MoneyManagerTopBar(
            title = if (isCard) "Credit card" else (account?.nickname ?: "Account"),
            subtitle = if (isCard) account?.nickname?.ifBlank { account.institutionName } else account?.institutionName,
            onBack = onBack,
            actions = {
                MmTopBarAction(
                    icon = Icons.Filled.Search,
                    contentDescription = "Search this account",
                    onClick = onToggleSearch
                )
                MmTopBarAction(
                    icon = Icons.Filled.Edit,
                    contentDescription = if (isCard) "Edit credit card" else "Edit account",
                    onClick = onEdit
                )
            }
        )

        if (account == null) return@Column

        MmCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
            contentPadding = PaddingValues(MmSpacing.lg)
        ) {
            if (isCard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.CreditCard, tint = MmColors.accent, size = 42.dp)
                    Spacer(Modifier.width(MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.nickname.ifBlank { account.institutionName },
                            style = MmType.sectionTitle,
                            color = MmColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            account.sourceAccountId
                                .takeIf { state.maskedCardNumber.isBlank() }
                                ?: "•••• ${state.maskedCardNumber.filter { it.isDigit() }.takeLast(4)}",
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(MmSpacing.lg))
                Text("Outstanding", style = MmType.caption, color = MmColors.textSecondary)
                MmAnimatedAmount(
                    value = account.outstandingMinorUnits ?: 0L,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountHero,
                    color = MmColors.textPrimary
                )
                if (state.statementDueDateEpochMillis != null) {
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(
                        "Statement ${state.currentCycle?.label.orEmpty()} • " +
                            "Due ${formatFullDate(state.statementDueDateEpochMillis)}",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
                val limit = account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits
                val outstanding = account.outstandingMinorUnits ?: 0L
                if (limit != null && limit > 0L) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    val fraction = (outstanding.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                    MmProgressBar(
                        fraction = fraction,
                        color = if (fraction > 0.9f) MmColors.expense else MmColors.accent,
                        height = 6.dp
                    )
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(
                        "${(fraction * 100).toInt()}% of ${MoneyFormat.rupeesNoDecimals(Money(limit))} limit used",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
                if (account.lastReportedAtEpochMillis != null) {
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(
                        "Last reported ${formatFullDate(account.lastReportedAtEpochMillis)}" +
                            (account.lastReportedBalanceMinorUnits?.let { " • ${MoneyFormat.rupeesNoDecimals(Money(it))}" } ?: ""),
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            } else {
                Text("Available balance", style = MmType.caption, color = MmColors.textSecondary)
                MmAnimatedAmount(
                    value = account.currentBalanceMinorUnits,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountHero,
                    color = MmColors.textPrimary,
                    modifier = Modifier.padding(vertical = MmSpacing.xs)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                    if (!account.active) MmPill("Inactive", tint = MmColors.textSecondary, filled = true)
                    if (account.manualBalanceOverrideMinorUnits != null) {
                        MmPill("Manual balance", tint = MmColors.textSecondary, filled = true)
                    }
                    if (account.lastReportedAtEpochMillis != null) {
                        MmPill(
                            "Reported ${formatFullDate(account.lastReportedAtEpochMillis)}",
                            tint = MmColors.textSecondary,
                            filled = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditCardTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("View", "Monthly", "Billing cycle")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg)
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Column(
                Modifier
                    .weight(1f)
                    .pressableClick { onSelect(index) }
                    .padding(vertical = MmSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (active) MmColors.textPrimary else MmColors.textSecondary,
                    style = MmType.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .background(if (active) MmColors.accent else Color.Transparent, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CurrentCycleList(
    state: AccountDetailUiState,
    onTransactionClick: (Long) -> Unit,
    matches: (List<TransactionEntity>) -> List<TransactionEntity>
) {
    val cycle = state.currentCycle
    val visible = cycle?.let { matches(it.transactions) }.orEmpty()
    if (cycle == null || visible.isEmpty()) {
        MmEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Nothing this cycle yet",
            message = cycle?.let { "No transactions between ${it.label} so far." }
                ?: "This card has no billing cycle configured."
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            CycleSummaryCard(
                label = "${cycle.label} • Current",
                income = Money(cycle.income),
                expense = Money(cycle.expense)
            )
        }
        items(visible, key = { it.id }) { txn ->
            MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
        }
    }
}

@Composable
private fun GroupedList(
    groups: List<PeriodView>,
    incomeLabel: String,
    expenseLabel: String,
    onTransactionClick: (Long) -> Unit,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    emptyMessage: String
) {
    val nonEmpty = groups.filter { it.transactions.isNotEmpty() }
    if (nonEmpty.isEmpty()) {
        MmEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "No transactions yet",
            message = emptyMessage
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        nonEmpty.forEachIndexed { index, group ->
            item(key = "header-${group.label}-$index") {
                PeriodHeaderBlock(
                    label = group.label,
                    badge = group.badge,
                    income = group.income,
                    expense = group.expense,
                    incomeLabel = incomeLabel,
                    expenseLabel = expenseLabel
                )
            }
            items(group.transactions, key = { it.id }) { txn ->
                MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
            }
        }
        if (canLoadMore) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Load older transactions")
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderBlock(
    label: String,
    badge: String?,
    income: Money,
    expense: Money,
    incomeLabel: String,
    expenseLabel: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MmType.sectionTitle,
                color = MmColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) MmPill(badge, tint = MmColors.accent, filled = true)
        }
        Spacer(Modifier.height(MmSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.lg)) {
            Text(
                "$expenseLabel ${MoneyFormat.rupeesNoDecimals(expense)}",
                style = MmType.caption,
                color = if (expense.minorUnits != 0L) MmColors.expense else MmColors.textSecondary
            )
            Text(
                "$incomeLabel ${MoneyFormat.rupeesNoDecimals(income)}",
                style = MmType.caption,
                color = if (income.minorUnits != 0L) MmColors.income else MmColors.textSecondary
            )
        }
    }
    Spacer(Modifier.height(MmSpacing.xs))
}

@Composable
private fun CycleSummaryCard(
    label: String,
    income: Money,
    expense: Money
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
    ) {
        androidx.compose.material.Surface(
            shape = RoundedCornerShape(22.dp),
            color = MmColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MmColors.outline),
            elevation = 0.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(MmSpacing.lg)) {
                Text(label, style = MmType.sectionTitle, color = MmColors.textPrimary)
                Spacer(Modifier.height(MmSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                    CycleStat("Spending", MoneyFormat.rupeesNoDecimals(expense), MmColors.expense, Modifier.weight(1f))
                    CycleStat("Payments & credits", MoneyFormat.rupeesNoDecimals(income), MmColors.income, Modifier.weight(1f))
                    CycleStat(
                        "Net",
                        MoneyFormat.rupeesNoDecimals(Money(expense.minorUnits - income.minorUnits)),
                        MmColors.textPrimary,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
            .padding(MmSpacing.md)
    ) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
