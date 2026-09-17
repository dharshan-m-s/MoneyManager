package com.moneymanager.app.ui.cash

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Cash in hand. The balance is shown on the brand header because that is the number the user
 * opened the screen for; below it, cash is grouped by month with income/spend totals per month,
 * and the month-end forward-cash marker is shown as its own row instead of hiding inside a list.
 */
@Composable
fun CashScreen(
    onBack: () -> Unit,
    onEditCash: (Long) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddCashIncome: (Long?) -> Unit,
    onAddCashSpend: (Long?) -> Unit,
    viewModel: CashViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var addExpanded by remember { mutableStateOf(false) }

    val zone = ZoneId.of("Asia/Kolkata")
    val filtered = remember(state.transactions, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.transactions else state.transactions.filter {
            listOfNotNull(
                it.merchantReceiverSender, it.rawCategoryName, it.notes, it.rawPaymentType, it.rawDateString
            ).joinToString(" ").lowercase().contains(q)
        }
    }
    val currentMonth = LocalDate.now(zone).withDayOfMonth(1)
    val grouped = filtered.groupBy {
        Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone).toLocalDate().withDayOfMonth(1)
    }.toSortedMap(compareByDescending { it }).toMutableMap()
    if (!grouped.containsKey(currentMonth)) grouped[currentMonth] = emptyList()

    Box(Modifier.fillMaxSize().background(MmColors.background)) {
        Column(Modifier.fillMaxSize()) {

            // Brand header doubles as the balance hero.
            Column(Modifier.fillMaxWidth().background(MMGreenDark).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MMWhite
                        )
                    }
                    Text(
                        "Cash in hand",
                        color = MMWhite,
                        style = MmType.screenTitle,
                        modifier = Modifier.weight(1f)
                    )
                    state.primaryCashAccountId?.let { cashAccountId ->
                        IconButton(onClick = { onEditCash(cashAccountId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit cash balance", tint = MMWhite)
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Search cash", tint = MMWhite)
                    }
                }
                Column(Modifier.padding(horizontal = MmSpacing.screen).padding(bottom = MmSpacing.lg)) {
                    Text("Live balance from your ledger", color = MMWhite.copy(alpha = 0.78f), style = MmType.caption)
                    Spacer(Modifier.height(MmSpacing.xs))
                    Text(
                        MoneyFormat.rupeesNoDecimals(state.cashInHand),
                        color = MMWhite,
                        style = MmType.amountHero,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showSearch) {
                Box(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
                    MmSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = "Search cash transactions"
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
            ) {
                CashMetric(
                    icon = Icons.Filled.Add,
                    amount = MoneyFormat.rupeesNoDecimals(state.monthIncome),
                    label = "In this month",
                    tint = MmColors.income,
                    modifier = Modifier.weight(1f)
                )
                CashMetric(
                    icon = Icons.Filled.Remove,
                    amount = MoneyFormat.rupeesNoDecimals(state.monthSpend),
                    label = "Out this month",
                    tint = MmColors.expense,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(MmSpacing.xs)
            ) {
                grouped.entries.sortedByDescending { it.key }.forEach { (month, rows) ->
                    val realRows = rows.filterNot { it.txnSubType.name.contains("CASH_FORWARD") }
                    val monthSpend = realRows.filter { it.debitMinorUnits > it.creditMinorUnits }
                        .sumOf { it.debitMinorUnits - it.creditMinorUnits }
                    val monthIncome = realRows.filter { it.creditMinorUnits > it.debitMinorUnits }
                        .sumOf { it.creditMinorUnits - it.debitMinorUnits }
                    val forward = rows.firstOrNull { it.txnSubType.name == "CREDIT_CASH_FORWARD" }

                    item(key = "header-$month") {
                        CashMonthHeader(month, Money(monthIncome), Money(monthSpend))
                    }
                    if (forward != null) {
                        item(key = "forward-$month") {
                            CashForwardRow(Money(forward.creditMinorUnits - forward.debitMinorUnits))
                        }
                    }
                    if (month == currentMonth && realRows.isEmpty()) {
                        item(key = "empty-$month") {
                            Row(Modifier.fillMaxWidth().padding(vertical = MmSpacing.lg)) {
                                Text(
                                    "No cash movements recorded this month yet.",
                                    style = MmType.caption,
                                    color = MmColors.textSecondary
                                )
                            }
                        }
                    }
                    items(realRows, key = { it.id }) { txn ->
                        MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                    }
                }
            }
        }

        // Cash has two directions, so the add control offers both instead of guessing.
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(MmSpacing.lg)
        ) {
            if (addExpanded) {
                AddOption("Cash spend", Icons.Filled.Remove, MmColors.expense) {
                    addExpanded = false
                    onAddCashSpend(state.primaryCashAccountId)
                }
                AddOption("Cash income", Icons.Filled.Add, MmColors.income) {
                    addExpanded = false
                    onAddCashIncome(state.primaryCashAccountId)
                }
                Spacer(Modifier.height(MmSpacing.sm))
            }
            FloatingActionButton(
                onClick = { addExpanded = !addExpanded },
                backgroundColor = MmColors.accent
            ) {
                Icon(
                    if (addExpanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = if (addExpanded) "Close add menu" else "Add cash transaction",
                    tint = MmColors.onAccent
                )
            }
        }
    }
}

@Composable
private fun AddOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(bottom = MmSpacing.sm)
            .clip(RoundedCornerShape(MmSpacing.touchTarget / 2))
            .background(MmColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        Text(label, style = MmType.label, color = MmColors.textPrimary)
        MmIconBadge(icon = icon, tint = tint, size = 36.dp)
    }
}

@Composable
private fun CashMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    amount: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    MmCard(modifier = modifier, contentPadding = PaddingValues(MmSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = icon, tint = tint, size = 32.dp)
            Spacer(Modifier.width(MmSpacing.sm))
            Column {
                Text(
                    amount,
                    style = MmType.amountRow,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CashMonthHeader(month: LocalDate, income: Money, spend: Money) {
    Row(
        Modifier.fillMaxWidth().padding(top = MmSpacing.md, bottom = MmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MmType.label,
            color = MmColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "+${MoneyFormat.rupeesNoDecimals(income)}",
            style = MmType.caption,
            color = MmColors.income
        )
        Spacer(Modifier.width(MmSpacing.md))
        Text(
            "−${MoneyFormat.rupeesNoDecimals(spend)}",
            style = MmType.caption,
            color = MmColors.expense
        )
    }
}

/**
 * Month-end cash rollover marker. Previously drawn on a fixed pale-yellow background, which
 * became a bright block in dark mode; it now uses the themed warning tint on the surface colour.
 */
@Composable
private fun CashForwardRow(amount: Money) {
    MmCard(contentPadding = PaddingValues(MmSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = Icons.Filled.ArrowUpward, tint = MmColors.warning, size = 36.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Forwarded cash balance", style = MmType.body, color = MmColors.textPrimary)
                Text(
                    "Carried into the next month by your statement",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            Text(
                MoneyFormat.rupeesNoDecimals(amount.abs()),
                style = MmType.amountRow,
                color = MmColors.textPrimary
            )
        }
    }
}
