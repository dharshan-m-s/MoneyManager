package com.moneymanager.app.ui.modern.screens.bills

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.bills.BillsViewModel
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val billHistoryZone: ZoneId = ZoneId.of("Asia/Kolkata")
private val billHistoryDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val billHistoryMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM ''yy")

@Composable
fun BillHistoryScreen(
    billId: Long,
    billName: String,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val historyFlow = remember(billId, viewModel) { viewModel.historyForBill(billId) }
    val instances by historyFlow.collectAsState(initial = emptyList())
    val chronological = remember(instances) { instances.sortedBy { it.dueDateEpochMillis } }
    val monthly = remember(chronological) {
        chronological.groupBy {
            YearMonth.from(Instant.ofEpochMilli(it.dueDateEpochMillis).atZone(billHistoryZone))
        }.toSortedMap().map { (month, entries) ->
            BillHistoryMonth(
                label = billHistoryMonth.format(month.atDay(1)),
                due = entries.sumOf { it.amountDueMinorUnits },
                paid = entries.sumOf { it.paidAmountMinorUnits.coerceAtMost(it.amountDueMinorUnits) },
                count = entries.size
            )
        }
    }
    val totalPaid = chronological.sumOf { it.paidAmountMinorUnits }
    val totalDue = chronological.sumOf { it.amountDueMinorUnits }
    val settledCount = chronological.count { it.paid }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Bill history",
            subtitle = billName,
            onBack = onBack,
            glassChrome = false,
            solidChrome = true,
            actions = {
                androidx.compose.material.TextButton(onClick = { onEdit(billId) }) {
                    Text("Edit", color = MmColors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MmSpacing.lg,
                end = MmSpacing.lg,
                top = MmSpacing.md,
                bottom = MmSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            item(key = "bill-history-overview") {
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                    BillHistoryMetric(
                        label = "TOTAL PAID",
                        value = MoneyFormat.rupeesNoDecimals(Money(totalPaid)),
                        modifier = Modifier.weight(1f),
                        tint = MmColors.income
                    )
                    BillHistoryMetric(
                        label = "TOTAL DUE",
                        value = MoneyFormat.rupeesNoDecimals(Money(totalDue)),
                        modifier = Modifier.weight(1f),
                        tint = MmColors.textPrimary
                    )
                }
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    "$settledCount of ${chronological.size} occurrences settled",
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    modifier = Modifier.padding(horizontal = MmSpacing.xs)
                )
            }
            item(key = "bill-history-chart") {
                BillHistoryBarGraph(months = monthly)
            }
            item(key = "bill-history-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("All occurrences", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(
                        "Every generated cycle, newest first",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
            }
            if (instances.isEmpty()) {
                item(key = "bill-history-empty") {
                    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
                        Text("No bill history yet", style = MmType.body, color = MmColors.textPrimary)
                        Text(
                            "Generated due dates and recorded payments will appear here.",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                }
            } else {
                items(instances, key = { it.id }) { instance ->
                    BillHistoryOccurrence(instance)
                }
            }
        }
    }
}

private data class BillHistoryMonth(
    val label: String,
    val due: Long,
    val paid: Long,
    val count: Int
)

@Composable
private fun BillHistoryMetric(
    label: String,
    value: String,
    modifier: Modifier,
    tint: androidx.compose.ui.graphics.Color
) {
    MmCard(modifier = modifier, contentPadding = PaddingValues(MmSpacing.md)) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            style = MmType.amountRow,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BillHistoryBarGraph(months: List<BillHistoryMonth>) {
    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Payment activity", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text(
                    "Monthly amount paid compared with amount due",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
            if (months.isEmpty()) {
                Text("The chart will appear when bill occurrences are generated.", style = MmType.caption, color = MmColors.textSecondary)
            } else {
                val maxValue = months.maxOf { maxOf(it.due, it.paid) }.coerceAtLeast(1L)
                Row(
                    modifier = Modifier.fillMaxWidth().height(174.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    months.takeLast(18).forEach { month ->
                        Column(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                MoneyFormat.rupeesNoDecimals(Money(month.paid)),
                                style = MmType.caption,
                                color = MmColors.textSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(Modifier.height(5.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(0.72f)
                                    .weight(1f, fill = true)
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(MmColors.surfaceContainerHigh),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeightFraction((month.due.toFloat() / maxValue).coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                        .background(MmColors.textTertiary.copy(alpha = 0.42f))
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeightFraction((month.paid.toFloat() / maxValue).coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                        .background(MmColors.accent)
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(month.label, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(3.dp)).background(MmColors.textTertiary.copy(alpha = 0.55f)))
                        Spacer(Modifier.width(5.dp))
                        Text("Due", style = MmType.caption, color = MmColors.textSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(9.dp).height(9.dp).clip(RoundedCornerShape(3.dp)).background(MmColors.accent))
                        Spacer(Modifier.width(5.dp))
                        Text("Paid", style = MmType.caption, color = MmColors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${months.size} months", style = MmType.caption, color = MmColors.textTertiary)
                }
                if (months.size > 18) {
                    Text("Chart shows the latest 18 months. The full occurrence timeline below is not limited.", style = MmType.caption, color = MmColors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun Modifier.fillMaxHeightFraction(fraction: Float): Modifier =
    this.then(Modifier.height((120f * fraction).dp))

@Composable
private fun BillHistoryOccurrence(instance: BillInstanceEntity) {
    val due = billHistoryDate.format(Instant.ofEpochMilli(instance.dueDateEpochMillis).atZone(billHistoryZone))
    val paidAt = instance.paidAtEpochMillis?.let {
        billHistoryDate.format(Instant.ofEpochMilli(it).atZone(billHistoryZone))
    }
    val status = when {
        instance.paid -> "Paid"
        instance.paidAmountMinorUnits > 0L -> "Partly paid"
        instance.dueDateEpochMillis < System.currentTimeMillis() -> "Overdue"
        else -> "Upcoming"
    }
    val statusColor = when {
        instance.paid -> MmColors.income
        instance.paidAmountMinorUnits > 0L -> MmColors.accent
        instance.dueDateEpochMillis < System.currentTimeMillis() -> MmColors.expense
        else -> MmColors.warning
    }
    MmCard(contentPadding = PaddingValues(MmSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(due, style = MmType.body, color = MmColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("Due amount", style = MmType.caption, color = MmColors.textSecondary)
            }
            Text(status, style = MmType.caption, color = statusColor, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                MoneyFormat.rupeesNoDecimals(Money(instance.amountDueMinorUnits)),
                style = MmType.amountRow,
                color = MmColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("Paid", style = MmType.caption, color = MmColors.textSecondary)
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(instance.paidAmountMinorUnits)),
                    style = MmType.label,
                    color = MmColors.income
                )
            }
        }
        if (paidAt != null) {
            Spacer(Modifier.height(5.dp))
            Text("Payment date: $paidAt", style = MmType.caption, color = MmColors.textSecondary)
        }
    }
}
