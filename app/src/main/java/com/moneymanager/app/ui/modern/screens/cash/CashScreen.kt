package com.moneymanager.app.ui.modern.screens.cash

import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MmMetricCard
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.cash.CashViewModel

/**
 * Cash search covers the visible transaction identity, category/account names, date text and
 * a nearby amount. A plain number is interpreted as a rupee amount with a small ±5% tolerance,
 * bounded from ₹25 to ₹500, so rounded amounts still find their ledger rows.
 */
private fun matchesCashSearch(
    txn: com.moneymanager.app.data.local.entity.TransactionEntity,
    rawQuery: String,
    categoryNames: Map<Long, String>,
    accountNames: Map<Long, String>
): Boolean {
    val query = rawQuery.trim().lowercase()
    val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
    val searchable = listOfNotNull(
        txn.merchantReceiverSender,
        txn.rawCategoryName,
        txn.categoryId?.let { categoryNames[it] },
        txn.notes,
        txn.rawPaymentType,
        txn.txnType,
        txn.txnSubType.name,
        txn.txnKind.toString(),
        txn.rawDateString,
        date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
        date.format(DateTimeFormatter.ISO_LOCAL_DATE),
        accountNames[txn.accountId]
    ).joinToString(" ").lowercase()
    val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isNotEmpty() && tokens.all { searchable.contains(it) }) return true

    val amountText = query.replace("₹", "").replace(",", "").replace(" ", "")
    if (!amountText.matches(Regex("\\d+(\\.\\d{1,2})?"))) return false
    val targetMinor = runCatching {
        (amountText.toBigDecimal() * java.math.BigDecimal(100)).longValueExact()
    }.getOrNull() ?: return false
    val tolerance = (targetMinor / 20L).coerceAtLeast(2_500L).coerceAtMost(50_000L)
    val actualMinor = kotlin.math.abs(txn.creditMinorUnits - txn.debitMinorUnits)
    return kotlin.math.abs(actualMinor - targetMinor) <= tolerance
}

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
    viewModel: CashViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val scrollChrome = rememberScrollChromeState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    val zone = ZoneId.of("Asia/Kolkata")
    val filtered = remember(state.transactions, query, state.categoryNames, state.accountNames) {
        if (query.isBlank()) state.transactions else state.transactions.filter { txn ->
            matchesCashSearch(txn, query, state.categoryNames, state.accountNames)
        }
    }
    val currentMonth = LocalDate.now(zone).withDayOfMonth(1)
    val grouped = filtered.groupBy {
        Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(zone).toLocalDate().withDayOfMonth(1)
    }.toSortedMap(compareByDescending { it }).toMutableMap()
    if (!grouped.containsKey(currentMonth)) grouped[currentMonth] = emptyList()

    Box(Modifier.fillMaxSize().mmPageBackdrop()) {
        Column(Modifier.fillMaxSize()) {

            MoneyManagerTopBar(
                title = "Cash in hand",
                subtitle = "Live balance from your ledger",
                onBack = onBack,
                collapseProgress = scrollChrome.progress,
                compact = scrollChrome.collapsed.value,
                keepTitleOnCollapse = true,
                modifier = Modifier
                    .overlayWithoutLayoutSpace()
                    .onSizeChanged { headerHeightPx = it.height },
                actions = {
                    state.primaryCashAccountId?.let { cashAccountId ->
                        MmTopBarAction(
                            icon = Icons.Filled.Edit,
                            contentDescription = "Edit cash balance",
                            onClick = { onEditCash(cashAccountId) }
                        )
                    }
                    MmTopBarAction(
                        icon = Icons.Filled.Search,
                        contentDescription = "Search cash",
                        onClick = { showSearch = !showSearch }
                    )
                },
                bottomContent = {
                    if (showSearch) {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MmSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = "Search cash transactions",
                                glassField = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            )

            LazyColumn(
                Modifier.weight(1f).nestedScroll(scrollChrome.connection),
                contentPadding = PaddingValues(
                    start = MmSpacing.screen,
                    top = headerInset + MmSpacing.md,
                    end = MmSpacing.screen,
                    bottom = maxOf(112.dp, LocalDockContentPadding.current)
                ),
                verticalArrangement = Arrangement.spacedBy(MmSpacing.xs)
            ) {
                stickyHeader(key = "cash-balance") {
                    MmBalancePanel(
                        label = "Available now",
                        value = MoneyFormat.rupeesNoDecimals(state.cashInHand),
                        animatedValueMinorUnits = state.cashInHand.minorUnits,
                        visualValues = state.monthTotals.toList()
                            .sortedBy { it.first }
                            .takeLast(6)
                            .map { (_, totals) -> kotlin.math.abs(totals.incomeMinor) + kotlin.math.abs(totals.spendMinor) },
                        visualLabel = "Monthly movement",
                        caption = "Live ledger balance · updated when you record cash transactions",
                        artworkKind = "cash",
                        glass = true,
                        compactProgress = scrollChrome.progress,
                        modifier = Modifier
                            .padding(vertical = if (scrollChrome.collapsed.value) MmSpacing.xs else MmSpacing.md)
                            .animateContentSize()
                    )
                }
                item(key = "cash-month-metrics") {
                    AnimatedVisibility(
                        visible = !scrollChrome.collapsed.value,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = MmSpacing.md),
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
                            CashMetric(
                                icon = Icons.Filled.Wallet,
                                amount = MoneyFormat.rupeesNoDecimals(
                                    Money(state.monthIncome.minorUnits - state.monthSpend.minorUnits)
                                ),
                                label = "Net this month",
                                tint = if (state.monthIncome.minorUnits >= state.monthSpend.minorUnits) MmColors.income else MmColors.expense,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                grouped.entries.sortedByDescending { it.key }.forEach { (month, rows) ->
                    // Canonical per-month totals from the ViewModel (transaction semantics, not a
                    // raw debit/credit sign test) so this header reconciles with the monthly
                    // figures above and with the rest of the app.
                    val totals = state.monthTotals[month]
                    val monthSpend = totals?.spendMinor ?: 0L
                    val monthIncome = totals?.incomeMinor ?: 0L
                    val realRows = rows.filterNot { it.txnSubType.name.contains("CASH_FORWARD") }
                    val forward = rows.firstOrNull { it.txnSubType.name == "CREDIT_CASH_FORWARD" }

                    item(key = "header-$month") {
                        CashMonthHeader(
                            month,
                            Money(monthIncome),
                            Money(monthSpend),
                            modifier = Modifier.revealOnAppear(index = 0)
                        )
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
                        MmTransactionRow(
                            txn = txn,
                            onClick = { onTransactionClick(txn.id) },
                            showDivider = false
                        )
                    }
                }
            }
        }

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
    val shape = RoundedCornerShape(21.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.18f), MmColors.surfaceElevated, MmColors.surface)))
            .border(1.dp, tint.copy(alpha = 0.22f), shape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MmType.caption.copy(fontSize = 10.sp), color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(amount, style = MmType.amountTile.copy(fontSize = 15.sp), color = MmColors.textPrimary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CashMonthHeader(
    month: LocalDate,
    income: Money,
    spend: Money,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(top = MmSpacing.md, bottom = MmSpacing.xs),
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
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = MmSpacing.sm)
            .clip(RoundedCornerShape(17.dp))
            .background(MmColors.surfaceContainer)
            .padding(horizontal = 13.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MmIconBadge(icon = Icons.Filled.ArrowUpward, tint = MmColors.warning, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Forwarded cash balance", style = MmType.body, color = MmColors.textPrimary, maxLines = 1)
            Text("Carried into the next month", style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(MoneyFormat.rupeesNoDecimals(amount.abs()), style = MmType.amountRow, color = MmColors.textPrimary, maxLines = 1, softWrap = false)
    }
}
