package com.moneymanager.app.ui.modern.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmEmptyState
import com.moneymanager.app.ui.modern.components.MmLoadingState
import com.moneymanager.app.ui.modern.components.MmTransactionRow
import com.moneymanager.app.ui.modern.components.MoneyFormat
import com.moneymanager.app.ui.modern.components.MoneyManagerTopBar
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.moneymanager.app.ui.modern.components.revealOnAppear
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
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            item {
                MmCard {
                    Text(
                        when (kind) {
                            DashboardBreakdownKind.BALANCE -> "Total available balance"
                            DashboardBreakdownKind.SPENT -> "Total spent"
                            DashboardBreakdownKind.INCOME -> "Total income"
                            DashboardBreakdownKind.NET_WORTH -> "Net worth"
                            DashboardBreakdownKind.BILLS -> "Total unpaid"
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
                    Spacer(Modifier.height(MmSpacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when (kind) {
                                DashboardBreakdownKind.BALANCE -> "Total available balance"
                                DashboardBreakdownKind.SPENT -> "Total spent"
                                DashboardBreakdownKind.INCOME -> "Total income"
                                DashboardBreakdownKind.NET_WORTH -> "Net worth"
                                DashboardBreakdownKind.BILLS -> "Total unpaid"
                            },
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            "ℹ",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                }
            }

            if (kind == DashboardBreakdownKind.NET_WORTH) {
                item {
                    BreakdownSectionHeader(
                        "Assets",
                        MoneyFormat.rupeesNoDecimals(state.assetTotal)
                    )
                }
                if (state.assetLines.isEmpty()) {
                    item { EmptyLine("No included asset accounts.") }
                } else {
                    itemsIndexed(state.assetLines, key = { _, line -> "asset-${line.account.id}" }) { index, line ->
                        AccountLineRow(
                            line = line,
                            liability = false,
                            modifier = Modifier.revealOnAppear(index = index)
                        )
                    }
                }
                item {
                    BreakdownSectionHeader(
                        "Liabilities",
                        "− " + MoneyFormat.rupeesNoDecimals(state.liabilityTotal)
                    )
                }
                if (state.liabilityLines.isEmpty()) {
                    item { EmptyLine("No included liabilities.") }
                } else {
                    itemsIndexed(state.liabilityLines, key = { _, line -> "liability-${line.account.id}" }) { index, line ->
                        AccountLineRow(
                            line = line,
                            liability = true,
                            modifier = Modifier.revealOnAppear(index = index)
                        )
                    }
                }
                item {
                    Divider(color = MmColors.divider, modifier = Modifier.padding(vertical = MmSpacing.sm))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Assets − liabilities", style = MmType.label, color = MmColors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            MoneyFormat.rupeesNoDecimals(state.total),
                            style = MmType.amountRow,
                            color = MmColors.textPrimary
                        )
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
                    line.account.accountType.name.replace('_', ' ')
                        .lowercase()
                        .replaceFirstChar { it.uppercase() },
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
