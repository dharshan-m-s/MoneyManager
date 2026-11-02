package com.moneymanager.app.ui.modern.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.dao.CategoryTotal
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.reporting.TransactionDisplayClass
import com.moneymanager.app.domain.reporting.TransactionDisplayClassifier
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.mmGlassHighlight
import com.moneymanager.app.ui.components.MmBottomSheet
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmMetricCard
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
import com.moneymanager.app.ui.components.modernTopInsets
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.mmCategoryTint
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.home.HomePreferences
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmShape
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.moneymanager.app.ui.dashboard.DashboardBreakdownKind
import com.moneymanager.app.ui.dashboard.DashboardUiState
import com.moneymanager.app.ui.dashboard.DashboardViewModel
import com.moneymanager.app.ui.dashboard.SafeToSpendMode
import com.moneymanager.app.ui.settings.SettingsEntryPoint
import com.moneymanager.app.ui.spendsummary.MonthWindow
import com.moneymanager.app.ui.spendsummary.SpendSummaryViewModel

private val dashboardZone: ZoneId = ZoneId.of("Asia/Kolkata")

private enum class RecentTypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Spending"),
    INCOME("Income"),
    NOT_A_SPEND("Not a spend"),
    TRANSFER("Transfer")
}

@Composable
fun DashboardScreen(
    onOpenMore: () -> Unit,
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
    viewModel: DashboardViewModel = hiltViewModel(),
    spendViewModel: SpendSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Render the hero + a lightweight loader until the first real snapshot arrives, so a
    // returning user never sees a frame of the "start here" empty state (or ₹0 balances) right
    // after unlocking. Once loaded, the state streams in without a second flash.
    val state = uiState
    val monthlySpendTotals by spendViewModel.dashboardMonthlyTotals.collectAsState()
    var selectedSpendMonth by rememberSaveable { mutableStateOf(0) }
    var monthWindowStart by rememberSaveable { mutableStateOf(0) }
    if (state == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MmColors.background)
                .modernTopInsets()
        ) {
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
            val displayClass = TransactionDisplayClassifier.classify(txn)
            val matchesType = when (activeType) {
                RecentTypeFilter.ALL -> true
                RecentTypeFilter.EXPENSE -> TransactionDisplayClassifier.isSpending(displayClass)
                RecentTypeFilter.INCOME -> TransactionDisplayClassifier.isIncome(displayClass)
                RecentTypeFilter.NOT_A_SPEND -> displayClass == TransactionDisplayClass.NOT_A_SPEND
                RecentTypeFilter.TRANSFER -> displayClass == TransactionDisplayClass.TRANSFER
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

    // The safe top inset (status bar + display cutout) is applied exactly once on this list,
    // so the page background extends edge-to-edge behind the transparent status bar while the
    // financial overview starts directly below the system icons. No greeting or date header.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MmColors.background)
            .modernTopInsets(),
        contentPadding = PaddingValues(
            bottom = LocalDockContentPadding.current.takeIf { it > 0.dp } ?: MmSpacing.md
        )
    ) {
            item {
                BalanceHeroCard(
                    state = state,
                    hideAmounts = hideAmounts,
                    onToggleVisibility = { homePreferences.setHideAmounts(!hideAmounts) },
                    onOpenBreakdown = { onOpenBreakdown(DashboardBreakdownKind.NET_WORTH) }
                )
            }

            item {
                MonthSummaryCards(
                    state = state,
                    hideAmounts = hideAmounts,
                    onOpenSpent = { onOpenBreakdown(DashboardBreakdownKind.SPENT) },
                    onOpenIncome = { onOpenBreakdown(DashboardBreakdownKind.INCOME) }
                )
            }

            item {
                SectionBlock(
                    title = "Monthly spending",
                    subtitle = "Swipe right to explore older months · select a bar",
                    actionLabel = "See all",
                    onAction = onSeeAllSpendAreas
                ) {
                    MonthlySpendTrendChart(
                        months = spendViewModel.dashboardMonths,
                        monthlyTotals = monthlySpendTotals,
                        selectedIndex = selectedSpendMonth,
                        windowStartIndex = monthWindowStart,
                        hideAmounts = hideAmounts,
                        onSelectMonth = { selectedSpendMonth = it },
                        onShowOlder = {
                            val next = (monthWindowStart + 1).coerceAtMost(spendViewModel.dashboardMonths.size - 6)
                            monthWindowStart = next
                            selectedSpendMonth = next
                        },
                        onShowNewer = {
                            val next = (monthWindowStart - 1).coerceAtLeast(0)
                            monthWindowStart = next
                            selectedSpendMonth = next
                        }
                    )
                }
            }

            if (!state.hasAnyData) {
                item {
                    MmCard(modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
                        MmEmptyState(
                            icon = Icons.Filled.Upload,
                            title = "Your finances start here",
                            message = "Import a statement or add your first transaction to see real numbers.",
                            actionLabel = "Import statement",
                            onAction = onImportPrompt,
                            compact = true
                        )
                    }
                }
            }

            item {
                SectionBlock(
                    title = "Latest transactions",
                    subtitle = if (anyFilterActive) "${filteredRecent.size} match your filter" else null,
                    actionLabel = "See all",
                    onAction = onSeeAllTransactions,
                    // Search and filter live where they act: on the recent-activity header.
                    headerTrailing = {
                        CircleAction(Icons.Filled.Search, "Search", onClick = onOpenSearch)
                        Spacer(Modifier.width(MmSpacing.sm))
                        CircleAction(
                            Icons.Filled.FilterList,
                            "Filter transactions",
                            onClick = { filterOpen = true },
                            badge = anyFilterActive
                        )
                    }
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
                BudgetCard(
                    state = state,
                    onOpenBudget = onOpenBudget,
                    hideAmounts = hideAmounts,
                    modifier = Modifier.revealOnAppear(index = 3)
                )
            }

            item {
                SafeToSpendCard(
                    state = state,
                    hideAmounts = hideAmounts,
                    onOpenBudget = onOpenBudget,
                    modifier = Modifier.revealOnAppear(index = 4)
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
                        backgroundColor = MmColors.accentFill,
                        contentColor = MmColors.onAccent
                    )
                ) { Text("Show ${filteredRecent.size}") }
            }
        }
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    Box {
        androidx.compose.material.Surface(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            shape = CircleShape,
            color = MmColors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MmColors.outline)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.clip(CircleShape).pressableClick(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = MmColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MmColors.warning)
            )
        }
    }
}

/**
 * The balance hero: a lit navy/indigo card carrying the only number that matters at a glance,
 * with the balance-visibility control and the real breakdown affordance. The decorative card art
 * is a pure Canvas illustration (no data), and the whole surface receives the GPU refraction pass.
 */
@Composable
private fun BalanceHeroCard(
    state: DashboardUiState,
    hideAmounts: Boolean,
    onToggleVisibility: () -> Unit,
    onOpenBreakdown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF6268D8),
                        Color(0xFF343A72),
                        Color(0xFF171D32)
                    )
                )
            )
            .mmGlassHighlight(cornerRadius = 32.dp, intensity = 0.62f)
            .pressableClick(onClick = onOpenBreakdown)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color(0xFF9CA3FF).copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(320f, 40f),
                        radius = 420f
                    )
                )
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.xl, vertical = MmSpacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total balance",
                    style = MmType.label,
                    color = Color.White.copy(alpha = 0.86f)
                )
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .pressableClick(onClick = onToggleVisibility),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (hideAmounts) "Show amounts" else "Hide amounts",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                        .pressableClick(onClick = onOpenBreakdown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open balance breakdown",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.height(MmSpacing.sm))
            if (hideAmounts) {
                Text(
                    "••••••",
                    style = MmType.amountHero,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                MmAnimatedAmount(
                    value = state.netWorth.minorUnits,
                    formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                    style = MmType.amountHero,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22D3EE))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.creditCardOutstanding.minorUnits != 0L) {
                        "Net worth · cards outstanding " +
                            if (hideAmounts) "••••" else MoneyFormat.rupeesNoDecimals(state.creditCardOutstanding)
                    } else {
                        "Net worth across your accounts"
                    },
                    style = MmType.caption,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Decorative layered-card illustration in the hero's right half. It is drawn, not an asset: three
 * tilted rounded rectangles with an indigo edge light, kept at low contrast so the amount always
 * wins. Purely ornamental — it encodes no numbers.
 */


/**
 * Two summary cards for the current month. Both are real numbers from FinancialSummary (the same
 * source as the drill-down screens) and both open that breakdown; there are no invented
 * month-over-month percentages.
 */
@Composable
private fun MonthSummaryCards(
    state: DashboardUiState,
    hideAmounts: Boolean,
    onOpenSpent: () -> Unit,
    onOpenIncome: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
    ) {
        MonthSummaryCard(
            label = "Spent this month",
            amount = state.monthSpend,
            tint = MmColors.expense,
            icon = Icons.AutoMirrored.Filled.CallMade,
            hideAmounts = hideAmounts,
            onClick = onOpenSpent,
            modifier = Modifier.weight(1f)
        )
        MonthSummaryCard(
            label = "Income this month",
            amount = state.monthIncome,
            tint = MmColors.income,
            icon = Icons.AutoMirrored.Filled.CallReceived,
            hideAmounts = hideAmounts,
            onClick = onOpenIncome,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MonthSummaryCard(
    label: String,
    amount: Money,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hideAmounts: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MmMetricCard(
        label = label,
        value = if (hideAmounts) "••••" else MoneyFormat.rupeesNoDecimals(amount),
        tint = tint,
        icon = icon,
        modifier = modifier,
        onClick = onClick
    )
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
                    MmProgressBar(progress = progress.coerceIn(0f, 1f), color = ringColor)
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
 * Swipeable six-month spending history for the home dashboard. Values are sourced
 * from Room-backed month windows and tapping a bar selects that month.
 */
@Composable
private fun MonthlySpendTrendChart(
    months: List<MonthWindow>,
    monthlyTotals: List<Long>,
    selectedIndex: Int,
    windowStartIndex: Int,
    hideAmounts: Boolean,
    onSelectMonth: (Int) -> Unit,
    onShowOlder: () -> Unit,
    onShowNewer: () -> Unit
) {
    val safeStart = windowStartIndex.coerceIn(0, (months.size - 6).coerceAtLeast(0))
    val visibleIndices = (safeStart until (safeStart + 6).coerceAtMost(months.size)).toList()
    val chartIndices = visibleIndices.asReversed()
    val chartMonths = chartIndices.map { months[it] }
    val chartValues = chartIndices.map { monthlyTotals.getOrNull(it) ?: 0L }
    val selectedIndexSafe = selectedIndex.coerceIn(0, (months.size - 1).coerceAtLeast(0))
    val selectedTotal = monthlyTotals.getOrNull(selectedIndexSafe) ?: 0L
    val selectedLabel = months.getOrNull(selectedIndexSafe)?.label.orEmpty()
    val previousTotal = monthlyTotals.getOrNull(selectedIndexSafe - 1)
    val difference = previousTotal?.let { selectedTotal - it }
    val maxTotal = (chartValues.maxOrNull() ?: 0L).coerceAtLeast(1L)
    MmCard(contentPadding = PaddingValues(0.dp)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(MmColors.heroGradient))
                    .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.lg)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "TOTAL SPENT",
                                style = MmType.caption.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
                                color = Color.White.copy(alpha = 0.72f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                if (hideAmounts) "••••••" else MoneyFormat.rupeesNoDecimals(Money(selectedTotal)),
                                style = MmType.amountLarge,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(selectedLabel, style = MmType.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Selected month", style = MmType.caption, color = Color.White.copy(alpha = 0.68f))
                        }
                    }
                    if (difference != null) {
                        Spacer(Modifier.height(MmSpacing.md))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.13f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val arrow = when {
                                difference > 0L -> "↑"
                                difference < 0L -> "↓"
                                else -> "•"
                            }
                            Text(
                                if (hideAmounts) "$arrow Compared with previous month"
                                else "$arrow ${MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(difference)))} vs previous month",
                                style = MmType.caption,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            val chartAccent = MmColors.accent
            val chartAccentFill = MmColors.accentFill
            val chartOutline = MmColors.outline
            val chartBackground = MmColors.background
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(months.size, safeStart) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    totalDrag > 55f && safeStart < months.size - 6 -> onShowOlder()
                                    totalDrag < -55f && safeStart > 0 -> onShowNewer()
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                change.consume()
                            }
                        )
                    }
                    .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spending trend", style = MmType.sectionTitle, color = MmColors.textPrimary)
                        Text("Swipe right for older months", style = MmType.caption, color = MmColors.textTertiary)
                    }
                    Text(
                        "${safeStart + 1}–${(safeStart + chartMonths.size).coerceAtMost(months.size)} / ${months.size}",
                        style = MmType.caption.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                        color = MmColors.textTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(MmSpacing.md))

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    if (chartValues.isNotEmpty()) {
                        val left = 8.dp.toPx()
                        val right = size.width - 8.dp.toPx()
                        val top = 10.dp.toPx()
                        val bottom = size.height - 8.dp.toPx()
                        val plotHeight = (bottom - top).coerceAtLeast(1f)
                        val slotWidth = (right - left) / chartValues.size
                        val barWidth = (slotWidth * 0.48f).coerceAtMost(34.dp.toPx()).coerceAtLeast(12.dp.toPx())

                        for (grid in 0..3) {
                            val y = top + plotHeight * grid / 3f
                            drawLine(
                                color = chartOutline.copy(alpha = 0.18f),
                                start = Offset(left, y),
                                end = Offset(right, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        chartValues.forEachIndexed { displayIndex, amount ->
                            val centerX = left + slotWidth * (displayIndex + 0.5f)
                            val barHeight = if (amount <= 0L) 3.dp.toPx() else
                                (amount.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f) * (plotHeight * 0.88f)
                            val topY = bottom - barHeight
                            val globalIndex = chartIndices[displayIndex]
                            val selected = globalIndex == selectedIndexSafe
                            val rectLeft = centerX - barWidth / 2f

                            if (selected) {
                                drawRoundRect(
                                    color = chartAccent.copy(alpha = 0.12f),
                                    topLeft = Offset(rectLeft - 5.dp.toPx(), topY - 5.dp.toPx()),
                                    size = Size(barWidth + 10.dp.toPx(), barHeight + 10.dp.toPx()),
                                    cornerRadius = CornerRadius(12.dp.toPx())
                                )
                            }
                            drawRoundRect(
                                color = chartOutline.copy(alpha = if (selected) 0.20f else 0.10f),
                                topLeft = Offset(rectLeft, top),
                                size = Size(barWidth, plotHeight),
                                cornerRadius = CornerRadius(barWidth / 2f)
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = if (selected) {
                                        listOf(chartAccent, chartAccentFill, chartAccent.copy(alpha = 0.72f))
                                    } else {
                                        listOf(chartAccent.copy(alpha = 0.68f), chartAccent.copy(alpha = 0.36f))
                                    },
                                    startY = topY,
                                    endY = bottom
                                ),
                                topLeft = Offset(rectLeft, topY),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f)
                            )
                            if (selected) {
                                drawCircle(
                                    color = chartBackground,
                                    radius = 4.dp.toPx(),
                                    center = Offset(centerX, (topY - 7.dp.toPx()).coerceAtLeast(top))
                                )
                                drawCircle(
                                    color = chartAccent,
                                    radius = 2.dp.toPx(),
                                    center = Offset(centerX, (topY - 7.dp.toPx()).coerceAtLeast(top))
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    chartMonths.forEachIndexed { displayIndex, month ->
                        val index = chartIndices[displayIndex]
                        val selected = index == selectedIndexSafe
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MmColors.accent.copy(alpha = 0.13f)
                                    else Color.Transparent
                                )
                                .pressableClick { onSelectMonth(index) }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                month.label.substringBefore(" "),
                                style = MmType.caption.copy(fontSize = 10.sp),
                                color = if (selected) MmColors.accent else MmColors.textTertiary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (chartValues.all { it <= 0L }) {
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        "Your monthly spending trend will appear here once you record transactions.",
                        style = MmType.caption,
                        color = MmColors.textSecondary
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
    val segments = rows.map { row ->
        val name = categoryNames[row.categoryId] ?: "Uncategorised"
        Triple(mmCategoryTint(name), row.total, name)
    }
    val positiveSegments = segments.filter { it.second > 0L }
    val categoryTotal = positiveSegments.sumOf { it.second }
    val restTotal = (monthSpend.minorUnits - categoryTotal).coerceAtLeast(0L)
    val ringTotal = (categoryTotal + restTotal).coerceAtLeast(1L)
    val chartSegments = buildList {
        addAll(positiveSegments)
        if (restTotal > 0L) add(Triple(MmColors.surfaceMuted, restTotal, "Other"))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val reveal by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 640),
                label = "categoryDonutReveal"
            )
            Canvas(Modifier.size(112.dp)) {
                val stroke = 15.dp.toPx()
                val gap = 3.5f
                var startAngle = -90f
                chartSegments.forEach { (tint, amount, _) ->
                    val fullSweep = amount.toFloat() / ringTotal.toFloat() * 360f
                    val sweep = fullSweep * reveal
                    drawArc(
                        color = tint,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    startAngle += fullSweep
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("SPENT", style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 0.7.sp), color = MmColors.textTertiary)
                Text(
                    MoneyFormat.rupeesNoDecimals(monthSpend),
                    style = MmType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(MmSpacing.lg))
        Column(Modifier.weight(1f)) {
            chartSegments.forEachIndexed { index, (tint, amount, name) ->
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
                        "${amount * 100L / ringTotal}%",
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
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmSectionHeader(
                title = title,
                subtitle = subtitle,
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.weight(1f)
            )
            if (headerTrailing != null) {
                Spacer(Modifier.width(MmSpacing.sm))
                headerTrailing()
            }
        }
        Spacer(Modifier.height(MmSpacing.sm))
        MmCard(contentPadding = PaddingValues(vertical = 6.dp, horizontal = 0.dp)) {
            content()
        }
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
