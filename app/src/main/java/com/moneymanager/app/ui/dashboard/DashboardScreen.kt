package com.moneymanager.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmQuickAction
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmLegendDot
import com.moneymanager.app.ui.components.MmLoadingState
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.mmCategoryTint
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.home.HomePreferences
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
    onOpenBreakdown: (DashboardBreakdownKind) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Render the hero + a lightweight loader until the first real snapshot arrives, so a
    // returning user never sees a frame of the "start here" empty state (or ₹0 balances) right
    // after unlocking. Once loaded, the state streams in without a second flash.
    val state = uiState
    if (state == null) {
        Column(Modifier.fillMaxSize().background(MmColors.background)) {
            DashboardHero(onOpenDrawer = onOpenDrawer, onOpenSearch = onOpenSearch, onOpenFilter = {}, filterActive = false, state = null, hideAmounts = false)
            MmLoadingState(text = "Opening your ledger…", modifier = Modifier.fillMaxSize())
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val homePreferences = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.moneymanager.app.ui.settings.SettingsEntryPoint::class.java
        ).homePreferences()
    }
    val hideAmounts by homePreferences.hideAmounts.collectAsState()
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
        DashboardHero(
            onOpenDrawer = onOpenDrawer,
            onOpenSearch = onOpenSearch,
            onOpenFilter = { filterOpen = true },
            filterActive = anyFilterActive,
            state = state,
            hideAmounts = hideAmounts,
            onOpenBreakdown = onOpenBreakdown
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 128.dp)
        ) {
            item {
                QuickActions(
                    onAddExpense = onAddAccountSpend,
                    onAddIncome = onAddAccountIncome,
                    onCash = onOpenCash,
                    onBills = onSeeAllBills,
                    modifier = Modifier.revealOnAppear(index = 0)
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
                BudgetCard(
                    state = state,
                    onOpenBudget = onOpenBudget,
                    hideAmounts = hideAmounts,
                    modifier = Modifier.revealOnAppear(index = 1)
                )
            }

            item {
                SafeToSpendCard(
                    state = state,
                    hideAmounts = hideAmounts,
                    onOpenBudget = onOpenBudget,
                    modifier = Modifier.revealOnAppear(index = 2)
                )
            }

            item {
                SectionBlock(
                    title = "Spending breakdown",
                    subtitle = if (state.monthSpend.minorUnits != 0L)
                        "This month · ${MoneyFormat.rupeesNoDecimals(state.monthSpend)}" else null,
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
                        SpendingBreakdown(
                            rows = state.topCategoryTotals,
                            categoryNames = state.categoryNames,
                            monthSpend = state.monthSpend
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
                                showDivider = index != filteredRecent.lastIndex,
                                hideAmounts = hideAmounts
                            )
                        }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MmSpacing.radiusRow))
                                .background(MmColors.surfaceMuted)
                                .pressableClick { onOpenBreakdown(DashboardBreakdownKind.BILLS) }
                                .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${state.upcomingBillsCount} bill${if (state.upcomingBillsCount == 1) "" else "s"} coming up",
                                    style = MmType.body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MmColors.textPrimary
                                )
                                Text("Tap for the included occurrences", style = MmType.caption, color = MmColors.textSecondary)
                            }
                            Text(
                                MoneyFormat.rupeesNoDecimals(state.upcomingBillsTotal),
                                style = MmType.amountRow,
                                color = MmColors.warning
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
                    hidden = hideAmounts,
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
                        backgroundColor = MmColors.accent,
                        contentColor = MmColors.onAccent
                    )
                ) { Text("Show ${filteredRecent.size}") }
            }
        }
    }
}

/**
 * The dashboard hero: brand-green gradient carrying the month, the net worth and the three
 * headline numbers on translucent tiles. Rounded bottom corners let it sit over the scrolling
 * content below, and white status-bar icons always have contrast on the green.
 */
@Composable
private fun DashboardHero(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    filterActive: Boolean,
    state: DashboardUiState?,
    hideAmounts: Boolean,
    onOpenBreakdown: (DashboardBreakdownKind) -> Unit = {}
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.verticalGradient(MmColors.brandGradient)
            )
    ) {
        // A single soft light source in the top-right corner gives the brand band depth
        // without adding a second colour or any hard edges.
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(760f, 40f),
                        radius = 620f
                    )
                )
        )
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = MMWhite)
                }
                Column(Modifier.weight(1f).padding(start = MmSpacing.xs)) {
                    Text(greeting(), color = MMWhite.copy(alpha = 0.75f), style = MmType.caption)
                    Text(
                        DateTimeFormatter.ofPattern("MMMM yyyy").format(LocalDate.now(dashboardZone)),
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

            if (state != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pressableClick { onOpenBreakdown(DashboardBreakdownKind.NET_WORTH) }
                        .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)
                ) {
                    Text("Net worth", color = MMWhite.copy(alpha = 0.72f), style = MmType.caption)
                    if (hideAmounts) {
                        Text(
                            "••••••",
                            style = MmType.amountHero,
                            color = MMWhite,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        MmAnimatedAmount(
                            value = state.netWorth.minorUnits,
                            formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                            style = MmType.amountHero,
                            color = MMWhite
                        )
                    }
                    Spacer(Modifier.height(MmSpacing.lg))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                    ) {
                        HeroTile("Available", state.bankBalance, hideAmounts, Modifier.weight(1f)) {
                            onOpenBreakdown(DashboardBreakdownKind.BALANCE)
                        }
                        HeroTile("Spent", state.monthSpend, hideAmounts, Modifier.weight(1f)) {
                            onOpenBreakdown(DashboardBreakdownKind.SPENT)
                        }
                        HeroTile("Income", state.monthIncome, hideAmounts, Modifier.weight(1f)) {
                            onOpenBreakdown(DashboardBreakdownKind.INCOME)
                        }
                    }
                    if (state.creditCardOutstanding.minorUnits != 0L) {
                        Spacer(Modifier.height(MmSpacing.sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(MmSpacing.radiusRow))
                                .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Credit card outstanding", style = MmType.caption, color = MMWhite.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                            Text(
                                if (hideAmounts) "••••" else MoneyFormat.rupeesNoDecimals(state.creditCardOutstanding),
                                style = MmType.label,
                                color = MMWhite
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(MmSpacing.xl))
            }
        }
    }
}

/** One headline number on the hero: caption + tabular amount on a translucent white tile. */
@Composable
private fun HeroTile(
    label: String,
    amount: Money,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .background(Color.White.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.pressableClick(onClick = onClick) else Modifier)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm)
    ) {
        Text(label, color = MMWhite.copy(alpha = 0.72f), style = MmType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (hidden) {
            Text(
                "••••",
                style = MmType.amountTile,
                color = MMWhite,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            MmAnimatedAmount(
                value = amount.minorUnits,
                formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                style = MmType.amountTile,
                color = MMWhite
            )
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

/** Four quick actions with their icon badges — the original dashboard controls. */
@Composable
private fun QuickActions(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onCash: () -> Unit,
    onBills: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MmQuickAction(Icons.AutoMirrored.Filled.CallMade, "Add expense", MmColors.expense, onAddExpense, Modifier.weight(1f))
        MmQuickAction(Icons.AutoMirrored.Filled.CallReceived, "Add income", MmColors.income, onAddIncome, Modifier.weight(1f))
        MmQuickAction(Icons.Filled.AccountBalanceWallet, "Cash", MmColors.accent, onCash, Modifier.weight(1f))
        // Recurring destination. The one-time CSV import lives in Settings → Data.
        MmQuickAction(Icons.Filled.Description, "Bills", MmColors.warning, onBills, Modifier.weight(1f))
    }
}

/** Budget ring + remaining summary, or the set-up call to action when no budget exists. */
@Composable
private fun BudgetCard(
    state: DashboardUiState,
    onOpenBudget: () -> Unit,
    hideAmounts: Boolean = false,
    modifier: Modifier = Modifier
) {
    MmCard(modifier = modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
        val hasBudget = state.budget.minorUnits > 0L
        val progress = state.budgetProgress
        val ringColor = when {
            !hasBudget -> MmColors.accent
            progress < 0.75f -> MmColors.income
            progress < 1f -> MmColors.warning
            else -> MmColors.expense
        }
        if (!hasBudget) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MmSpacing.radiusRow))
                    .background(MmColors.surfaceMuted)
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Set a monthly budget", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text("Track how much is left to spend", style = MmType.caption, color = MmColors.textSecondary)
                }
                TextButton(onClick = onOpenBudget) { Text("Set up", color = MmColors.accent) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress.coerceIn(0f, 1f),
                        animationSpec = tween(durationMillis = 700),
                        label = "budgetRing"
                    )
                    val trackColor = MmColors.divider
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
                            sweepAngle = animatedProgress * 360f,
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
                        style = MmType.sectionTitle,
                        color = MmColors.textPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    val remaining = state.budget.minorUnits - state.monthSpend.minorUnits
                    Text(
                        if (hideAmounts) "Amounts hidden"
                        else if (remaining >= 0) "${MoneyFormat.rupeesNoDecimals(Money(remaining))} left of ${MoneyFormat.rupeesNoDecimals(state.budget)}"
                        else "${MoneyFormat.rupeesNoDecimals(Money(-remaining))} over ${MoneyFormat.rupeesNoDecimals(state.budget)}",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                    Spacer(Modifier.height(MmSpacing.sm))
                    MmProgressBar(fraction = progress, color = ringColor)
                }
                IconButton(onClick = onOpenBudget) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Open budget", tint = MmColors.textSecondary)
                }
            }
        }
    }
}

/**
 * Safe to Spend: a single, explainable number for the rest of the month. The label states which
 * definition is in effect so the value is never ambiguous - remaining budget when a budget is
 * set, otherwise liquid cash after scheduled bills.
 */
@Composable
private fun SafeToSpendCard(
    state: DashboardUiState,
    hideAmounts: Boolean,
    onOpenBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBudget = state.safeToSpendMode == SafeToSpendMode.BUDGET_REMAINING
    val caption = if (hasBudget) {
        "Remaining of your ${MoneyFormat.rupeesNoDecimals(state.budget)} budget"
    } else {
        "Cash left after ${MoneyFormat.rupeesNoDecimals(state.upcomingBillsTotal)} of scheduled bills"
    }
    MmCard(modifier = modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = Icons.Filled.Savings, tint = MmColors.accent)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Safe to spend", style = MmType.caption, color = MmColors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (hideAmounts) "••••••" else MoneyFormat.rupeesNoDecimals(state.safeToSpend),
                    style = MmType.sectionTitle,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(caption, style = MmType.caption, color = MmColors.textTertiary)
            }
            if (!hasBudget) {
                IconButton(onClick = onOpenBudget) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Set a budget",
                        tint = MmColors.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Donut + named legend for the month's category split. Categories keep a stable tint from their
 * name, and the "rest" ring segment uses the muted surface so the top rows pop.
 */
@Composable
private fun SpendingBreakdown(
    rows: List<CategoryTotal>,
    categoryNames: Map<Long, String>,
    monthSpend: Money
) {
    val total = rows.sumOf { it.total }.takeIf { it > 0L } ?: 1L
    val restTotal = (monthSpend.minorUnits - total).coerceAtLeast(0L)
    val segments = rows.map { row ->
        val name = categoryNames[row.categoryId] ?: "Uncategorised"
        Triple(mmCategoryTint(name), row.total, name)
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val restColor = MmColors.surfaceMuted
            Canvas(Modifier.size(104.dp)) {
                val stroke = 15f
                val gap = 3.5f
                var startAngle = -90f
                val ringTotal = (total + restTotal).toFloat()
                fun sweepOf(amount: Long): Float = (amount.toFloat() / ringTotal) * 360f
                segments.forEach { (tint, amount, _) ->
                    val sweep = sweepOf(amount)
                    if (sweep > 0.5f) {
                        drawArc(
                            color = tint,
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(1f),
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweep
                }
                if (restTotal > 0L) {
                    val sweep = sweepOf(restTotal)
                    drawArc(
                        color = restColor,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Spent", style = MmType.caption, color = MmColors.textSecondary)
                Text(
                    MoneyFormat.rupeesNoDecimals(monthSpend),
                    style = MmType.label,
                    fontWeight = FontWeight.Bold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Spacer(Modifier.width(MmSpacing.lg))
        Column(Modifier.weight(1f)) {
            segments.forEachIndexed { index, (tint, amount, name) ->
                if (index > 0) Spacer(Modifier.height(MmSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    MmLegendDot(tint)
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(
                        name,
                        style = MmType.body,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(
                        MoneyFormat.rupeesNoDecimals(Money(amount)),
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(MmSpacing.sm))
                    Text(
                        "${amount * 100 / total}%",
                        style = MmType.caption,
                        color = MmColors.textTertiary,
                        modifier = Modifier.width(34.dp),
                        maxLines = 1
                    )
                }
            }
        }
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
private fun CashWalletCard(
    balance: Money,
    spend: Money,
    withdrawn: Money,
    hidden: Boolean = false,
    onClick: () -> Unit
) {
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Cash wallet", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text("Your cash on hand · tap to open", style = MmType.caption, color = MmColors.textSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open cash", tint = MmColors.textSecondary)
        }
        Spacer(Modifier.height(MmSpacing.md))
        Text(
            if (hidden) "••••••" else MoneyFormat.rupeesNoDecimals(balance),
            style = MmType.amountLarge,
            color = MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MmSpacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            CashStatTile("Cash spent", spend, MmColors.expense, Modifier.weight(1f), hidden)
            CashStatTile("Cash in", withdrawn, MmColors.income, Modifier.weight(1f), hidden)
        }
    }
}

@Composable
private fun CashStatTile(
    label: String,
    amount: Money,
    tint: Color,
    modifier: Modifier = Modifier,
    hidden: Boolean = false
) {
    Column(
        modifier
            .clip(RoundedCornerShape(MmSpacing.radiusRow))
            .background(MmColors.surfaceMuted)
            .padding(horizontal = MmSpacing.md, vertical = MmSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmLegendDot(tint)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            if (hidden) "••••" else MoneyFormat.rupeesNoDecimals(amount),
            style = MmType.amountTile,
            color = MmColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
