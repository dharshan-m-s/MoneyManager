package com.moneymanager.app.ui.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.SectionCard
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSeeAllSpendAreas: () -> Unit,
    onSeeAllIncomeAreas: () -> Unit = {},
    onSeeAllBills: () -> Unit,
    onImportPrompt: () -> Unit,
    onOpenBudget: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenCash: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRecentFilter by remember { mutableStateOf(false) }
    var recentQuery by remember { mutableStateOf("") }
    var recentType by remember { mutableStateOf("ALL") }
    var recentFrom by remember { mutableStateOf("") }
    var recentTo by remember { mutableStateOf("") }
    val filteredRecent = remember(state.recentTransactions, recentQuery, recentType, recentFrom, recentTo) {
        val q = recentQuery.trim().lowercase()
        fun date(v: String): LocalDate? = runCatching { LocalDate.parse(v.trim()) }.getOrNull()
        val from = date(recentFrom)
        val to = date(recentTo)
        state.recentTransactions.filter { txn ->
            val hay = listOfNotNull(txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType, txn.rawDateString).joinToString(" ").lowercase()
            val typeOk = when (recentType) {
                "EXPENSE" -> txn.txnSubType == TxnSubType.EXPENSE
                "INCOME" -> txn.txnSubType == TxnSubType.INCOME
                "TRANSFER" -> txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
                else -> true
            }
            val d = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
            (q.isBlank() || hay.contains(q)) && typeOk && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        DashboardTopBar(onOpenDrawer = onOpenDrawer, onOpenSearch = onOpenSearch, onOpenRecentFilter = { showRecentFilter = true })

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp)
        ) {
            item { SafeToSpendCard(state, onClick = onOpenBudget) }

            if (!state.hasAnyData) {
                item { ImportPromptCard(onImportPrompt) }
            }

            item {
                SectionCard(
                    title = "Latest Transactions",
                    accentColor = DashboardColors.accent,
                    onSeeAll = onSeeAllTransactions
                ) {
                    if (filteredRecent.isEmpty()) {
                        EmptyStateRow(if (state.recentTransactions.isEmpty()) {
                            "No transactions yet. Import your Moneyview statement or add a transaction to get started."
                        } else {
                            "No recent transactions match the filter."
                        })
                    } else {
                        filteredRecent.forEach { txn ->
                            DashboardTransactionRow(
                                transactionId = txn.id,
                                title = txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction",
                                subtitle = txn.rawCategoryName ?: "",
                                dateEpochMillis = txn.occurredAtEpochMillis,
                                amount = Money(txn.creditMinorUnits - txn.debitMinorUnits),
                                onTransactionClick = onTransactionClick
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Top Spend Areas",
                    accentColor = DashboardColors.accent,
                    onSeeAll = onSeeAllSpendAreas
                ) {
                    if (state.topCategoryTotals.isEmpty()) {
                        EmptyStateRow("No spend recorded for this month yet.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpendDonut(state.topCategoryTotals.map { it.total })
                            Spacer(Modifier.padding(start = 16.dp))
                            Column {
                                Text("Spend", color = MMGrayText, fontSize = 12.sp)
                                Text(
                                    MoneyFormat.rupeesNoDecimals(state.monthSpend),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Top Income Areas",
                    accentColor = DashboardColors.accent,
                    onSeeAll = onSeeAllIncomeAreas
                ) {
                    if (state.topIncomeCategoryTotals.isEmpty()) {
                        EmptyStateRow("No income recorded for this month yet.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IncomeDonut(state.topIncomeCategoryTotals.map { it.total })
                            Spacer(Modifier.padding(start = 16.dp))
                            Column {
                                Text("Income", color = MMGrayText, fontSize = 12.sp)
                                Text(
                                    MoneyFormat.rupeesNoDecimals(state.monthIncome),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MMGreenIncome,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Bills",
                    accentColor = DashboardColors.accent,
                    onSeeAll = onSeeAllBills
                ) {
                    if (state.upcomingBillsCount == 0) {
                        EmptyStateRow("No bills added yet.")
                    } else {
                        Text(
                            "You have ${state.upcomingBillsCount} bill(s) due",
                            fontSize = 13.sp,
                            color = MMGrayText
                        )
                        Text(
                            MoneyFormat.rupeesNoDecimals(state.upcomingBillsTotal),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                            letterSpacing = (-0.2).sp
                        )
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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    RecentFilterDialog(
        show = showRecentFilter, query = recentQuery, type = recentType, from = recentFrom, to = recentTo,
        onQuery = { recentQuery = it }, onType = { recentType = it }, onFrom = { recentFrom = it }, onTo = { recentTo = it },
        onReset = { recentQuery = ""; recentType = "ALL"; recentFrom = ""; recentTo = "" },
        onDismiss = { showRecentFilter = false }
    )
}

@Composable
private fun DashboardTopBar(onOpenDrawer: () -> Unit, onOpenSearch: () -> Unit, onOpenRecentFilter: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PageDashboardStart, PageDashboardEnd),
                        startX = 0f,
                        endX = 1200f
                    )
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MMWhite.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = MMWhite, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 2.dp)) {
                    Text(
                        text = "Money Manager",
                        color = MMWhite.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                    Text(
                        text = DateTimeFormatter.ofPattern("MMM yyyy").format(LocalDate.now()),
                        color = MMWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                }
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MMWhite.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = MMWhite, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenRecentFilter,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MMWhite.copy(alpha = 0.12f))
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter recent transactions", tint = MMWhite, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentFilterDialog(
    show: Boolean,
    query: String,
    type: String,
    from: String,
    to: String,
    onQuery: (String) -> Unit,
    onType: (String) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter recent transactions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(query, onQuery, label = { Text("Search merchant, category, notes") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL" to "All", "EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (v, label) ->
                        Surface(
                            modifier = Modifier.clickable { onType(v) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (type == v) DashboardColors.accent else MaterialTheme.colors.surface,
                            border = if (type == v) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.18f))
                        ) {
                            Text(
                                label,
                                color = if (type == v) Color.White else MaterialTheme.colors.onSurface,
                                fontSize = 11.sp,
                                fontWeight = if (type == v) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(from, onFrom, label = { Text("From date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(to, onTo, label = { Text("To date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("RESET") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = DashboardColors.accent, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
private fun SafeToSpendCard(state: DashboardUiState, onClick: () -> Unit) {
    val progress = state.budgetProgress
    val ringColor = when {
        state.budget.minorUnits <= 0L -> DashboardColors.accent
        progress < 0.75f -> DashboardColors.accent
        progress < 1f -> MMAmberDue
        else -> MMRedExpense
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (state.budget.minorUnits > 0L) progress.coerceAtMost(1f) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        elevation = 1.dp,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                val outlineColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
                Canvas(Modifier.size(180.dp)) {
                    val stroke = 18f
                    drawArc(
                        outlineColor, 0f, 360f, false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    val sweep = animatedProgress * 360f
                    if (state.budget.minorUnits > 0L) {
                        drawArc(
                            ringColor, -90f, sweep, false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    if (state.budget.minorUnits > 0L && progress > 1f) {
                        drawArc(
                            MMRedExpense, -90f, 360f, false,
                            style = Stroke(width = stroke + 4f, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.budget.minorUnits > 0L) "Budget used" else "Net Worth",
                        color = MMGrayText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (state.budget.minorUnits > 0L) "${(progress * 100).toInt()}%" else MoneyFormat.rupeesNoDecimals(state.netWorth),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ringColor,
                        letterSpacing = (-0.3).sp
                    )
                    if (state.budget.minorUnits > 0L) {
                        val remaining = state.budget.minorUnits - state.monthSpend.minorUnits
                        Text(
                            if (remaining >= 0L)
                                "${MoneyFormat.rupeesNoDecimals(Money(remaining))} left"
                            else
                                "${MoneyFormat.rupeesNoDecimals(Money(-remaining))} over",
                            color = MMGrayText, fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (state.budget.minorUnits > 0L) {
                Text(
                    when {
                        progress < 0.75f -> "On track"
                        progress < 1f -> "Nearing your budget"
                        progress == 1f -> "Budget reached"
                        else -> "Over budget"
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ringColor,
                    letterSpacing = (-0.1).sp
                )
                Text(
                    "${MoneyFormat.rupeesNoDecimals(state.monthSpend)} of ${MoneyFormat.rupeesNoDecimals(state.budget)}",
                    color = MMGrayText, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                TextButton(onClick = onClick) { Text("Manage budget") }
            } else {
                Text("Set a monthly budget to track spending", color = MMGrayText, fontSize = 12.sp)
                TextButton(onClick = onClick) { Text("Set budget") }
            }

            Divider(color = MMGrayDivider, modifier = Modifier.padding(vertical = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DashboardMetric("Bank", state.bankBalance)
                DashboardMetric("Safe to spend", state.safeToSpend, emphasize = true)
                DashboardMetric("Bills due", state.upcomingBillsTotal)
            }
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: Money, emphasize: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        Text(label, color = MMGrayText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(
            MoneyFormat.rupeesNoDecimals(value),
            fontSize = if (emphasize) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasize && value.isNegative) MMRedExpense else Color.Unspecified,
            letterSpacing = (-0.1).sp
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
    val totalActivity = (spend.minorUnits + withdrawn.minorUnits).coerceAtLeast(1L)
    val spendFraction = (spend.minorUnits.toFloat() / totalActivity.toFloat()).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        elevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = CashColors.accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = CashColors.accent, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cash", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
                    Text("Your cash wallet", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open cash", tint = MMGrayText)
            }
            Text(
                MoneyFormat.rupeesNoDecimals(balance),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
                letterSpacing = (-0.4).sp
            )
            val dividerColor = MMGrayDivider
            Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 6.dp)) {
                drawRoundRect(
                    color = dividerColor.copy(alpha = 0.7f),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                )
                if (spend.minorUnits > 0L) {
                    drawRoundRect(
                        color = MMRedExpense,
                        size = Size(size.width * spendFraction, size.height),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(MoneyFormat.rupeesNoDecimals(spend), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Spend", fontSize = 11.sp, color = MMGrayText)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(MoneyFormat.rupeesNoDecimals(withdrawn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Withdrawn", fontSize = 11.sp, color = MMGrayText)
                }
            }
        }
    }
}

@Composable
private fun ImportPromptCard(onImportPrompt: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onImportPrompt),
        elevation = 0.dp,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = DashboardColors.accentLight
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Import your statement", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Bring in your Moneyview history to see real numbers here.",
                    fontSize = 12.sp,
                    color = MMGrayText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Surface(
                shape = CircleShape,
                color = DashboardColors.accent,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyStateRow(message: String) {
    Text(message, fontSize = 13.sp, color = MMGrayText, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun DashboardTransactionRow(transactionId: Long, title: String, subtitle: String, dateEpochMillis: Long, amount: Money, onTransactionClick: (Long) -> Unit = {}) {
    val dateStr = DateTimeFormatter.ofPattern("dd MMM").format(
        Instant.ofEpochMilli(dateEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onTransactionClick(transactionId) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle.ifBlank { "Transaction" }, fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 94.dp, max = 118.dp)) {
                Text(
                    MoneyFormat.rupeesNoDecimals(amount.abs()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (amount.isNegative) MMRedExpense else MMGreenIncome,
                    letterSpacing = (-0.1).sp
                )
                Text(dateStr, fontSize = 11.sp, color = MMGrayText)
            }
        }
        Divider(color = MMGrayDivider)
    }
}

@Composable
private fun SpendDonut(values: List<Long>) {
    val total = values.sum().takeIf { it > 0 } ?: 1L
    val colors = listOf(
        DashboardColors.accent, MMRedExpense, MMAmberDue, MMBlue, MMPurple, MMBrown
    )
    Canvas(modifier = Modifier.size(84.dp)) {
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value / total
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 20f, cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun IncomeDonut(values: List<Long>) {
    val total = values.sum().takeIf { it > 0 } ?: 1L
    val colors = listOf(
        MMGreen, MMGreenIncome, MMGreenDark, MMBlue, MMPurple, MMBrown
    )
    Canvas(modifier = Modifier.size(84.dp)) {
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value / total
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 20f, cap = StrokeCap.Round),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
    }
}
