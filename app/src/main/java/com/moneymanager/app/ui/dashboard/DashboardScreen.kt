package com.moneymanager.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MMBlue
import com.moneymanager.app.ui.theme.MMPurple
import com.moneymanager.app.ui.theme.MMBrown
import com.moneymanager.app.ui.theme.MMCyan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Column(Modifier.fillMaxSize().background(androidx.compose.material.MaterialTheme.colors.background)) {
            DashboardTopBar(onOpenDrawer = onOpenDrawer, onOpenSearch = onOpenSearch, onOpenRecentFilter = { showRecentFilter = true })

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material.MaterialTheme.colors.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp)
            ) {
                item { SafeToSpendCard(state, onClick = onOpenBudget) }

                if (!state.hasAnyData) {
                    item { ImportPromptCard(onImportPrompt) }
                }

                item {
                    SectionCard(title = "Latest Transactions", onSeeAll = onSeeAllTransactions) {
                        if (filteredRecent.isEmpty()) {
                            EmptyStateRow(if (state.recentTransactions.isEmpty()) {
                                "No transactions yet. Import your Moneyview statement or add a transaction to get started."
                            } else {
                                "No recent transactions match the filter."
                            })
                        } else {
                            filteredRecent.forEach { txn ->
                                TransactionRow(
                                    title = txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction",
                                    subtitle = txn.rawCategoryName ?: "",
                                    dateEpochMillis = txn.occurredAtEpochMillis,
                                    amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Top Spend Areas", onSeeAll = onSeeAllSpendAreas) {
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
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Bills", onSeeAll = onSeeAllBills) {
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(MMGreenDark)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.background(MMWhite.copy(alpha = .10f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = MMWhite)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 2.dp)) {
            Text(
                text = "Money Manager",
                color = MMWhite.copy(alpha = .68f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = DateTimeFormatter.ofPattern("MMM yyyy").format(LocalDate.now()),
                color = MMWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        IconButton(
            onClick = onOpenSearch,
            modifier = Modifier.background(MMWhite.copy(alpha = .10f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = MMWhite)
        }
        IconButton(
            onClick = onOpenRecentFilter,
            modifier = Modifier.background(MMWhite.copy(alpha = .10f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter recent transactions", tint = MMWhite)
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
                OutlinedTextField(query, onQuery, label = { Text("Search merchant, category, notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL" to "All", "EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (v, label) ->
                        Surface(modifier = Modifier.clickable { onType(v) }, shape = MaterialTheme.shapes.small, color = if (type == v) MaterialTheme.colors.primary else MaterialTheme.colors.surface, border = if (type == v) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .18f))) {
                            Text(label, color = if (type == v) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                }
                OutlinedTextField(from, onFrom, label = { Text("From date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(to, onTo, label = { Text("To date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("RESET") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}

@Composable
private fun SafeToSpendCard(state: DashboardUiState, onClick: () -> Unit) {
    val progress = state.budgetProgress
    val ringColor = when {
        state.budget.minorUnits <= 0L -> MMGreen
        progress < 0.75f -> MMGreen
        progress < 1f -> MMAmberDue
        else -> MMRedExpense
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.10f)
                Canvas(Modifier.size(156.dp)) {
                    val stroke = 16f
                    drawArc(
                        trackColor, 0f, 360f, false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    val sweep = if (state.budget.minorUnits > 0L) {
                        (progress.coerceAtMost(1f) * 360f)
                    } else 0f
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
                    Text(if (state.budget.minorUnits > 0L) "Budget used" else "Net Worth", color = MMGrayText, fontSize = 11.sp)
                    Text(
                        if (state.budget.minorUnits > 0L) "${(progress * 100).toInt()}%" else MoneyFormat.rupeesNoDecimals(state.netWorth),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ringColor
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
            Spacer(Modifier.height(8.dp))
            if (state.budget.minorUnits > 0L) {
                Text(
                    when {
                        progress < 0.75f -> "On track"
                        progress < 1f -> "Nearing your budget"
                        progress == 1f -> "Budget reached"
                        else -> "Over budget"
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ringColor
                )
                Text(
                    "${MoneyFormat.rupeesNoDecimals(state.monthSpend)} of ${MoneyFormat.rupeesNoDecimals(state.budget)}",
                    color = MMGrayText, fontSize = 11.sp
                )
                TextButton(onClick = onClick) { Text("Manage budget") }
            } else {
                Text("Set a monthly budget to track spending", color = MMGrayText, fontSize = 12.sp)
                TextButton(onClick = onClick) { Text("Set budget") }
            }

            Divider(color = MMGrayDivider, modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Bank", state.bankBalance)
                Metric("Safe to spend", state.safeToSpend, emphasize = true)
                Metric("Bills due", state.upcomingBillsTotal)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Money, emphasize: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        Text(label, color = MMGrayText, fontSize = 10.sp)
        Text(
            MoneyFormat.rupeesNoDecimals(value),
            fontSize = if (emphasize) 14.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasize && value.isNegative) MMRedExpense else Color.Unspecified
        )
    }
}

@Composable
private fun SectionCard(title: String, onSeeAll: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = 0.dp,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, com.moneymanager.app.ui.theme.MMOutline.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .let { if (onSeeAll != null) it.clickable(onClick = onSeeAll) else it },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ChevronRight, contentDescription = "See all", tint = MMGrayText)
            }
            Spacer(Modifier.padding(top = 8.dp))
            content()
        }
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = 0.dp,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, com.moneymanager.app.ui.theme.MMOutline.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cash", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Your cash wallet", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open cash", tint = MMGrayText)
            }
            Text(MoneyFormat.rupeesNoDecimals(balance), fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(12.dp).padding(top = 2.dp)) {
                drawRoundRect(
                    color = MMGrayDivider.copy(alpha = .80f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                )
                if (spend.minorUnits > 0L) {
                    drawRoundRect(
                        color = MMRedExpense,
                        size = androidx.compose.ui.geometry.Size(size.width * spendFraction, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(MoneyFormat.rupeesNoDecimals(spend), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Spend", fontSize = 11.sp, color = MMGrayText)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(MoneyFormat.rupeesNoDecimals(withdrawn), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Withdrawn", fontSize = 11.sp, color = MMGrayText)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = MMGreen, modifier = Modifier.size(20.dp))
                Text("Tap to view cash activity", fontSize = 11.sp, color = MMGrayText, modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun ImportPromptCard(onImportPrompt: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onImportPrompt),
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.10f)
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
                    color = MMGrayText
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = com.moneymanager.app.ui.theme.MMGreen)
        }
    }
}

@Composable
private fun EmptyStateRow(message: String) {
    Text(message, fontSize = 13.sp, color = MMGrayText)
}

@Composable
private fun TransactionRow(title: String, subtitle: String, dateEpochMillis: Long, amount: Money) {
    val dateStr = DateTimeFormatter.ofPattern("dd MMM").format(
        Instant.ofEpochMilli(dateEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    color = if (amount.isNegative) MMRedExpense else MMGreenIncome
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
        MMRedExpense, MMGreen, MMAmberDue, MMBlue, MMPurple, MMBrown
    )
    Canvas(modifier = Modifier.size(80.dp)) {
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value / total
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 18f),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
    }
}
