package com.moneymanager.app.ui.modern.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MmMetricCard
import com.moneymanager.app.ui.components.MmBankLogo
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmLoadingState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed
import com.moneymanager.app.ui.dashboard.AccountLine
import com.moneymanager.app.ui.dashboard.DashboardBreakdownKind
import com.moneymanager.app.ui.dashboard.DashboardBreakdownViewModel

private val breakdownZone = ZoneId.of("Asia/Kolkata")
private val breakdownDate = DateTimeFormatter.ofPattern("dd MMM yyyy")

@Composable
fun DashboardBreakdownScreen(
    kind: DashboardBreakdownKind,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: DashboardBreakdownViewModel = hiltViewModel()
) {
    val state by viewModel.stateFor(kind).collectAsState()

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = when (kind) {
                DashboardBreakdownKind.BALANCE -> "Balance breakdown"
                DashboardBreakdownKind.SPENT -> "Spending breakdown"
                DashboardBreakdownKind.INCOME -> "Income breakdown"
                DashboardBreakdownKind.NET_WORTH -> "Net worth breakdown"
                DashboardBreakdownKind.BILLS -> "Unpaid bills"
            },
            subtitle = state.periodLabel.takeIf { it.isNotBlank() },
            onBack = onBack
        )

        if (state.loading) {
            MmLoadingState(text = "Adding up the ledger…", modifier = Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(MmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            item {
                when (kind) {
                    DashboardBreakdownKind.BALANCE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                            MmBalancePanel(
                                label = "TOTAL AVAILABLE BALANCE",
                                value = MoneyFormat.rupeesNoDecimals(state.total),
                                animatedValueMinorUnits = state.total.minorUnits,
                                visualValues = state.accountLines.map { it.amountMinorUnits }.sorted(),
                                visualLabel = "Account mix",
                                caption = "Across ${state.accountLines.size} included spending accounts"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                                MmMetricCard(
                                    label = "Included accounts",
                                    value = state.accountLines.size.toString(),
                                    tint = MmColors.accent,
                                    icon = Icons.Filled.AccountBalanceWallet,
                                    modifier = Modifier.weight(1f)
                                )
                                MmMetricCard(
                                    label = "Account types",
                                    value = state.accountLines.map { it.account.accountType }.distinct().size.toString(),
                                    tint = MmColors.income,
                                    icon = Icons.Filled.AccountBalance,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    DashboardBreakdownKind.NET_WORTH -> {
                        val allLines = state.assetLines + state.liabilityLines
                        val grouped = allLines.groupBy { it.account.accountType }
                        val composition = grouped.map { (type, lines) ->
                            CompositionSegment(
                                label = AccountTypeCatalog.info(type).pluralLabel,
                                amount = lines.sumOf { it.amountMinorUnits },
                                tint = accountTypeTint(type)
                            )
                        }.sortedByDescending { it.amount }
                        Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                            MmBalancePanel(
                                label = "YOUR NET WORTH",
                                value = MoneyFormat.rupeesNoDecimals(state.total),
                                animatedValueMinorUnits = state.total.minorUnits,
                                visualValues = composition.map { it.amount },
                                visualLabel = "Financial mix",
                                caption = "${allLines.size} accounts across ${grouped.size} account types"
                            )
                            BalanceCompositionCard(
                                title = "Where your money is held",
                                subtitle = "Live balances grouped by the account types you use",
                                segments = composition
                            )
                        }
                    }
                    else -> {
                        MmCard {
                            Text(
                                when (kind) {
                                    DashboardBreakdownKind.SPENT -> "Total spent"
                                    DashboardBreakdownKind.INCOME -> "Total income"
                                    DashboardBreakdownKind.BILLS -> "Total unpaid"
                                    else -> "Overview"
                                },
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                MoneyFormat.rupeesNoDecimals(state.total),
                                style = MmType.amountLarge,
                                color = if (state.total.isNegative) MmColors.expense else MmColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(MmSpacing.sm))
                            Text(state.formula, style = MmType.caption, color = MmColors.textSecondary)
                        }
                    }
                }
            }

            if (kind == DashboardBreakdownKind.BALANCE) {
                item {
                    BalanceCompositionCard(
                        title = "Where your available balance sits",
                        subtitle = "Positive balances by account type · overdrafts remain included in the total",
                        segments = state.accountLines
                            .filter { it.amountMinorUnits > 0L }
                            .groupBy { it.account.accountType }
                            .map { (type, lines) ->
                                CompositionSegment(
                                    label = type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                    amount = lines.sumOf { it.amountMinorUnits },
                                    tint = accountTypeTint(type)
                                )
                            }
                            .sortedByDescending { it.amount }
                    )
                }
            }

            if (kind == DashboardBreakdownKind.NET_WORTH) {
                val groupedLines = (state.assetLines + state.liabilityLines)
                    .groupBy { it.account.accountType }
                    .toSortedMap(compareBy { it.ordinal })
                groupedLines.forEach { (type, lines) ->
                    val liability = AccountTypeCatalog.isLiability(type)
                    val amount = lines.sumOf { it.amountMinorUnits }
                    item(key = "net-worth-type-${type.name}") {
                        BreakdownSectionHeader(
                            AccountTypeCatalog.info(type).pluralLabel,
                            (if (liability) "− " else "") + MoneyFormat.rupeesNoDecimals(Money(amount))
                        )
                    }
                    itemsIndexed(
                        lines.sortedByDescending { it.amountMinorUnits },
                        key = { _, line -> "net-worth-${type.name}-${line.account.id}" }
                    ) { index, line ->
                        AccountLineRow(
                            line = line,
                            liability = liability,
                            modifier = Modifier.revealOnAppear(index = index)
                        )
                    }
                }
                if (state.assetLines.isEmpty() && state.liabilityLines.isEmpty()) {
                    item { EmptyLine("Add accounts to see a detailed breakdown of your financial position.") }
                }
                item {
                    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Net worth", style = MmType.sectionTitle, color = MmColors.textPrimary)
                                Text("Calculated from all included account balances", style = MmType.caption, color = MmColors.textSecondary)
                            }
                            Text(
                                MoneyFormat.rupeesNoDecimals(state.total),
                                style = MmType.amountRow,
                                color = if (state.total.isNegative) MmColors.expense else MmColors.income
                            )
                        }
                    }
                }
            }

            if (kind == DashboardBreakdownKind.BALANCE) {
                if (state.accountLines.isEmpty()) {
                    item { EmptyLine("No included accounts.") }
                } else {
                    itemsIndexed(state.accountLines, key = { _, line -> "balance-${line.account.id}" }) { index, line ->
                        AccountLineRow(
                            line = line,
                            liability = false,
                            modifier = Modifier.revealOnAppear(index = index)
                        )
                    }
                }
            }

            if (kind == DashboardBreakdownKind.BILLS) {
                if (state.billLines.isEmpty()) {
                    item {
                        MmEmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "No unpaid bills",
                            message = "Every generated bill occurrence is paid."
                        )
                    }
                } else {
                    itemsIndexed(state.billLines, key = { _, line -> "bill-${line.instance.id}" }) { index, line ->
                        MmCard(modifier = Modifier.revealOnAppear(index = index)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        line.bill?.billerName ?: "Bill",
                                        style = MmType.body,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MmColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Due " + breakdownDate.format(
                                            Instant.ofEpochMilli(line.instance.dueDateEpochMillis).atZone(breakdownZone)
                                        ),
                                        style = MmType.caption,
                                        color = MmColors.textSecondary
                                    )
                                }
                                Text(
                                    MoneyFormat.rupeesNoDecimals(
                                        Money(line.instance.amountDueMinorUnits - line.instance.paidAmountMinorUnits)
                                    ),
                                    style = MmType.amountRow,
                                    color = MmColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            if (kind == DashboardBreakdownKind.SPENT || kind == DashboardBreakdownKind.INCOME) {
                val label = if (kind == DashboardBreakdownKind.SPENT) "Included transactions" else "Included income"
                item {
                    BreakdownSectionHeader(label, MoneyFormat.rupeesNoDecimals(state.total))
                }
                if (state.transactions.isEmpty()) {
                    item { EmptyLine("No transactions match this period.") }
                } else {
                    items(state.transactions, key = { it.id }) { txn ->
                        MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                    }
                }
            }

            item { Spacer(Modifier.height(MmSpacing.xxl)) }
        }
    }
}

private data class CompositionSegment(
    val label: String,
    val amount: Long,
    val tint: androidx.compose.ui.graphics.Color
)

@Composable
private fun BalanceCompositionCard(
    title: String,
    subtitle: String,
    segments: List<CompositionSegment>
) {
    val positive = segments.filter { it.amount > 0L }
    val total = positive.sumOf { it.amount }
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "balanceCompositionReveal"
    )
    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
            Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary)
            Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
            if (positive.isEmpty()) {
                Text("No positive balances to chart yet.", style = MmType.caption, color = MmColors.textTertiary)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.lg)
                ) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 17.dp.toPx()
                            var angle = -90f
                            positive.forEach { segment ->
                                val sweep = 360f * segment.amount.toFloat() / total.toFloat()
                                drawArc(
                                    color = segment.tint,
                                    startAngle = angle + 2.2f,
                                    sweepAngle = (sweep - 4.4f).coerceAtLeast(0f) * reveal,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    size = Size(size.width, size.height)
                                )
                                angle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("GROSS", style = MmType.caption.copy(fontSize = 9.sp), color = MmColors.textTertiary)
                            Text(
                                MoneyFormat.rupeesNoDecimals(Money(total)),
                                style = MmType.amountTile,
                                color = MmColors.textPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                    ) {
                        positive.take(5).forEach { segment ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(segment.tint)
                                )
                                Spacer(Modifier.width(MmSpacing.xs))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        segment.label,
                                        style = MmType.caption,
                                        color = MmColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        MoneyFormat.rupeesNoDecimals(Money(segment.amount)),
                                        style = MmType.amountRow,
                                        color = MmColors.textPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun accountTypeTint(type: AccountType): androidx.compose.ui.graphics.Color = when (type) {
    AccountType.CASH -> MmColors.income
    AccountType.CREDIT_CARD, AccountType.LOAN -> MmColors.expense
    AccountType.INVESTMENT -> MmColors.categoryPalette[3 % MmColors.categoryPalette.size]
    AccountType.WALLET -> MmColors.categoryPalette[1 % MmColors.categoryPalette.size]
    else -> MmColors.accent
}

@Composable
private fun BreakdownSectionHeader(title: String, total: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = MmSpacing.sm, bottom = MmSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
        Text(total, style = MmType.label, color = MmColors.textSecondary)
    }
}

@Composable
private fun AccountLineRow(line: AccountLine, liability: Boolean, modifier: Modifier = Modifier) {
    MmCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmBankLogo(
                bankName = line.account.institutionName,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    line.account.nickname.ifBlank { line.account.institutionName },
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AccountTypeCatalog.info(line.account.accountType).singularLabel,
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            Text(
                (if (liability) "− " else "") +
                    MoneyFormat.rupeesNoDecimals(Money(line.amountMinorUnits)),
                style = MmType.amountRow,
                color = if (liability) MmColors.expense else MmColors.textPrimary
            )
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MmType.caption, color = MmColors.textSecondary)
}
