package com.moneymanager.app.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmLegendDot
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmQuickAction
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.mmCategoryTint
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dashboardZone: ZoneId = ZoneId.of("Asia/Kolkata")

private enum class RecentTypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSeeAllSpendAreas: () -> Unit,
    onSeeAllBills: () -> Unit,
    onImportPrompt: () -> Unit,
    onOpenBudget: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    onOpenCash: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(RecentTypeFilter.ALL.name) }
    var fromEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var toEpoch by rememberSaveable { mutableStateOf<Long?>(null) }

    val activeType = RecentTypeFilter.valueOf(typeFilter)
    val filteredRecent = remember(state.recentTransactions, query, typeFilter, fromEpoch, toEpoch) {
        state.recentTransactions.filter { txn ->
            val haystack = listOfNotNull(
                txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType
            ).joinToString(" ")
            val matchesQuery = query.isBlank() || haystack.contains(query.trim(), ignoreCase = true)
            val matchesType = when (activeType) {
                RecentTypeFilter.ALL -> true
                RecentTypeFilter.EXPENSE -> txn.txnSubType == TxnSubType.EXPENSE
                RecentTypeFilter.INCOME -> txn.txnSubType == TxnSubType.INCOME
                RecentTypeFilter.TRANSFER ->
                    txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
            }
            val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(dashboardZone).toLocalDate()
            val matchesFrom = fromEpoch?.let {
                !date.isBefore(Instant.ofEpochMilli(it).atZone(dashboardZone).toLocalDate())
            } ?: true
            val matchesTo = toEpoch?.let {
                !date.isAfter(Instant.ofEpochMilli(it).atZone(dashboardZone).toLocalDate())
            } ?: true
            matchesQuery && matchesType && matchesFrom && matchesTo
        }
    }
    val anyFilterActive = query.isNotBlank() || activeType != RecentTypeFilter.ALL || fromEpoch != null || toEpoch != null

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        DashboardTopBar(
            onOpenDrawer = onOpenDrawer,
            onOpenSearch = onOpenSearch,
            onOpenFilter = { filterOpen = true },
            filterActive = anyFilterActive
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp)
        ) {
            item {
                MonthSummaryCard(
                    state = state,
                    onOpenBudget = onOpenBudget
                )
            }

            item {
                QuickActions(
                    onAddExpense = onAddAccountSpend,
                    onAddIncome = onAddAccountIncome,
                    onCash = onOpenCash,
                    onImport = onImportPrompt
                )
            }

            if (!state.hasAnyData) {
                item {
                    MmCard(modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
                        MmEmptyState(
                            icon = Icons.Filled.Upload,
                            title = "Your finances start here",
                            message = "Import a statement or add your first transaction to see real numbers.",
                            actionLabel = "Import statement",
                            onAction = onImportPrompt
                        )
                    }
                }
            }

            item {
                SectionBlock(
                    title = "Latest transactions",
                    subtitle = if (anyFilterActive) "${filteredRecent.size} match your filter" else null,
                    actionLabel = "See all",
                    onAction = onSeeAllTransactions
                ) {
                    if (filteredRecent.isEmpty()) {
                        MmEmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = if (state.recentTransactions.isEmpty()) "No transactions yet" else "Nothing matches",
                            message = if (state.recentTransactions.isEmpty())
                                "Add a transaction to start tracking your spending."
                            else
                                "Try clearing the filter to see more.",
                            actionLabel = if (anyFilterActive) "Clear filter" else null,
                            onAction = if (anyFilterActive) {
                                {
                                    query = ""; typeFilter = RecentTypeFilter.ALL.name
                                    fromEpoch = null; toEpoch = null
                                }
                            } else null
                        )
                    } else {
                        filteredRecent.forEachIndexed { index, txn ->
                            MmTransactionRow(
                                txn = txn,
                                onClick = { onTransactionClick(txn.id) },
                                showDivider = index != filteredRecent.lastIndex
                            )
                        }
                    }
                }
            }

            item {
                SectionBlock(
                    title = "Top spend areas",
                    actionLabel = "See all",
                    onAction = onSeeAllSpendAreas
                ) {
                    if (state.topCategoryTotals.isEmpty()) {
                        MmEmptyState(
                            icon = Icons.Filled.PieChart,
                            title = "No spending this month",
                            message = "Your category breakdown appears here once you spend."
                        )
                    } else {
                        SpendBreakdown(
                            rows = state.topCategoryTotals.map { it.categoryId to it.total },
                            monthSpend = state.monthSpend
                        )
                    }
                }
            }

            item {
                SectionBlock(
                    title = "Bills",
                    actionLabel = "Manage",
                    onAction = onSeeAllBills
                ) {
                    if (state.upcomingBillsCount == 0) {
                        MmEmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "No bills due",
                            message = "Add a biller to get reminded before the due date."
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MmIconBadge(icon = Icons.AutoMirrored.Filled.ReceiptLong, tint = MmColors.warning, size = 44.dp)
                            Spacer(Modifier.width(MmSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${state.upcomingBillsCount} bill${if (state.upcomingBillsCount == 1) "" else "s"} coming up",
                                    style = MmType.body,
                                    color = MmColors.textPrimary
                                )
                                Text("Tap to review or pay", style = MmType.caption, color = MmColors.textSecondary)
                            }
                            Text(
                                MoneyFormat.rupeesNoDecimals(state.upcomingBillsTotal),
                                style = MmType.amountRow,
                                color = MmColors.textPrimary
                            )
                        }
                    }
                }
            }

            item {
                CashWalletCard(
                    balance = state.cashBalance,
                    spend = state.cashSpend,
                    withdrawn = state.cashWithdrawn,
                    onClick = onOpenCash
                )
            }
        }
    }

    if (filterOpen) {
        MmBottomSheet(title = "Filter transactions", onDismiss = { filterOpen = false }) {
            MmSearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Merchant, category or notes"
            )
            Spacer(Modifier.height(MmSpacing.md))
            Text("Type", style = MmType.label, color = MmColors.textSecondary)
            Spacer(Modifier.height(MmSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                RecentTypeFilter.entries.forEach { option ->
                    MmChip(
                        label = option.label,
                        selected = activeType == option,
                        onClick = { typeFilter = option.name }
                    )
                }
            }
            Spacer(Modifier.height(MmSpacing.lg))
            MmDateField(
                label = "From",
                epochMillis = fromEpoch,
                onChange = { fromEpoch = it }
            )
            Spacer(Modifier.height(MmSpacing.md))
            MmDateField(
                label = "To",
                epochMillis = toEpoch,
                onChange = { toEpoch = it }
            )
            Spacer(Modifier.height(MmSpacing.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                TextButton(
                    onClick = {
                        query = ""; typeFilter = RecentTypeFilter.ALL.name
                        fromEpoch = null; toEpoch = null
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear all") }
                androidx.compose.material.Button(
                    onClick = { filterOpen = false },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                        backgroundColor = MmColors.accent
                    )
                ) { Text("Show ${filteredRecent.size}", color = MmColors.onAccent) }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    filterActive: Boolean
) {
    Surface(color = MMGreenDark, elevation = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = MMWhite)
            }
            Column(Modifier.weight(1f).padding(start = MmSpacing.xs)) {
                Text(
                    greeting(),
                    color = MMWhite.copy(alpha = 0.75f),
                    style = MmType.caption
                )
                Text(
                    DateTimeFormatter.ofPattern("MMMM yyyy").format(LocalDate.now()),
                    color = MMWhite,
                    style = MmType.screenTitle
                )
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = MMWhite)
            }
            Box {
                IconButton(onClick = onOpenFilter) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter transactions", tint = MMWhite)
                }
                if (filterActive) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(9.dp)
                            .background(MmColors.warning, CircleShape)
                    )
                }
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now(dashboardZone).hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun MonthSummaryCard(state: DashboardUiState, onOpenBudget: () -> Unit) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        onClick = onOpenBudget
    ) {
        Text("Net worth", style = MmType.caption, color = MmColors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            MoneyFormat.rupeesNoDecimals(state.netWorth),
            style = MmType.amountLarge,
            color = if (state.netWorth.isNegative) MmColors.expense else MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MmSpacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
            SummaryTile("Balance", state.bankBalance, MmColors.accent, Modifier.weight(1f))
            SummaryTile("Spent", state.monthSpend, MmColors.expense, Modifier.weight(1f))
            SummaryTile("Income", state.monthIncome, MmColors.income, Modifier.weight(1f))
        }
        if (state.creditCardOutstanding.minorUnits != 0L) {
            Spacer(Modifier.height(MmSpacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MmColors.textSecondary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(MmSpacing.sm))
                Text("Credit card outstanding", style = MmType.caption, color = MmColors.textSecondary, modifier = Modifier.weight(1f))
                Text(
                    MoneyFormat.rupeesNoDecimals(state.creditCardOutstanding),
                    style = MmType.label,
                    color = MmColors.textPrimary
                )
            }
        }
        Spacer(Modifier.height(MmSpacing.md))
        BudgetStrip(state = state, onOpenBudget = onOpenBudget)
    }
}

@Composable
private fun SummaryTile(label: String, amount: Money, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmLegendDot(tint)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            MoneyFormat.rupeesNoDecimals(amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BudgetStrip(state: DashboardUiState, onOpenBudget: () -> Unit) {
    val hasBudget = state.budget.minorUnits > 0L
    val progress = state.budgetProgress
    val ringColor = when {
        !hasBudget -> MmColors.accent
        progress < 0.75f -> MmColors.income
        progress < 1f -> MmColors.warning
        else -> MmColors.expense
    }
    val trackColor = MmColors.divider
    if (!hasBudget) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
                .padding(MmSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MmIconBadge(icon = Icons.Filled.MonetizationOn, tint = MmColors.accent, size = 40.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Set a monthly budget", style = MmType.label, color = MmColors.textPrimary)
                Text("Track how much is left to spend", style = MmType.caption, color = MmColors.textSecondary)
            }
            TextButton(onClick = onOpenBudget) { Text("Set up") }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(76.dp)) {
                    val stroke = 9f
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = (progress.coerceAtMost(1f) * 360f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MmType.label,
                    fontWeight = FontWeight.Bold,
                    color = ringColor
                )
            }
            Spacer(Modifier.width(MmSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        progress < 0.75f -> "On track"
                        progress < 1f -> "Nearing your budget"
                        progress < 1.01f -> "Budget reached"
                        else -> "Over budget"
                    },
                    style = MmType.label,
                    color = ringColor
                )
                Spacer(Modifier.height(2.dp))
                val remaining = state.budget.minorUnits - state.monthSpend.minorUnits
                Text(
                    if (remaining >= 0) "${MoneyFormat.rupeesNoDecimals(Money(remaining))} left of ${MoneyFormat.rupeesNoDecimals(state.budget)}"
                    else "${MoneyFormat.rupeesNoDecimals(Money(-remaining))} over ${MoneyFormat.rupeesNoDecimals(state.budget)}",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.sm))
                MmProgressBar(fraction = progress, color = ringColor)
            }
        }
    }
}

@Composable
private fun QuickActions(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onCash: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MmQuickAction(Icons.AutoMirrored.Filled.CallMade, "Add expense", MmColors.expense, onAddExpense, Modifier.weight(1f))
        MmQuickAction(Icons.AutoMirrored.Filled.CallReceived, "Add income", MmColors.income, onAddIncome, Modifier.weight(1f))
        MmQuickAction(Icons.Filled.AccountBalanceWallet, "Cash", MmColors.accent, onCash, Modifier.weight(1f))
        MmQuickAction(Icons.Filled.Upload, "Import", MmColors.warning, onImport, Modifier.weight(1f))
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    MmCard(modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
        MmSectionHeader(
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction
        )
        Spacer(Modifier.height(MmSpacing.sm))
        content()
    }
}

@Composable
private fun SpendBreakdown(rows: List<Pair<Long?, Long>>, monthSpend: Money) {
    val total = rows.sumOf { it.second }.takeIf { it > 0L } ?: 1L
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { (_, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                MmLegendDot(mmCategoryTint(amount.toString()))
                Spacer(Modifier.width(MmSpacing.sm))
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(amount)),
                    style = MmType.body,
                    color = MmColors.textPrimary,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1
                )
                Box(Modifier.weight(1f).padding(horizontal = MmSpacing.sm)) {
                    MmProgressBar(fraction = amount.toFloat() / total.toFloat(), color = MmColors.expense, height = 6.dp)
                }
                Text(
                    "${(amount * 100 / total)}%",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(MmSpacing.xs))
        Text(
            "Total this month ${MoneyFormat.rupeesNoDecimals(monthSpend)}",
            style = MmType.caption,
            color = MmColors.textSecondary
        )
    }
}

@Composable
private fun CashWalletCard(
    balance: Money,
    spend: Money,
    withdrawn: Money,
    onClick: () -> Unit
) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = Icons.Filled.AccountBalanceWallet, tint = MmColors.accent, size = 44.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Cash wallet", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text("Your cash on hand", style = MmType.caption, color = MmColors.textSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open cash", tint = MmColors.textSecondary)
        }
        Spacer(Modifier.height(MmSpacing.md))
        Text(
            MoneyFormat.rupeesNoDecimals(balance),
            style = MmType.amountLarge,
            color = MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MmSpacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
            SummaryTile("Cash spent", spend, MmColors.expense, Modifier.weight(1f))
            SummaryTile("Cash in", withdrawn, MmColors.income, Modifier.weight(1f))
        }
    }
}
