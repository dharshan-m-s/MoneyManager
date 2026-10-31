package com.moneymanager.app.ui.modern.screens.spendsummary

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.util.Locale
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.spendsummary.MonthWindow
import com.moneymanager.app.ui.spendsummary.SpendCategoryRow
import com.moneymanager.app.ui.spendsummary.SpendSummaryViewModel

/**
 * Spend areas for the selected month only. The month chips re-query Room for that window,
 * the donut answers "where did the money go" at a glance, the list underneath answers
 * "how much exactly", and every row is a real drill-down into the matching transaction list.
 */
@Composable
fun SpendSummaryScreen(
    onBack: () -> Unit,
    onCategoryClick: (Long) -> Unit = {},
    viewModel: SpendSummaryViewModel = hiltViewModel()
) {
    val rows by viewModel.categoryRows.collectAsState()
    val total by viewModel.selectedMonthTotal.collectAsState()
    val hasPrevData by viewModel.hasPreviousMonthData.collectAsState()
    val prevTotal by viewModel.previousMonthTotal.collectAsState()
    val selectedIdx by viewModel.selectedMonthIndex.collectAsState()
    val monthlyTotals by viewModel.monthlyTotals.collectAsState()
    val months = viewModel.availableMonths

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Spend areas",
            subtitle = months[selectedIdx].label,
            onBack = onBack
        )

        // Month graph: one bar per month, each bar is THAT month's total spend only.
        // Tapping a bar re-queries Room for that window and updates everything below.
        MonthBarGraph(
            months = months,
            monthlyTotals = monthlyTotals,
            selectedIdx = selectedIdx,
            onSelect = viewModel::selectMonth
        )

        if (rows.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.PieChart,
                title = "Nothing spent in ${months[selectedIdx].label}",
                message = "Once you record a spend in this month it will be grouped by category here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    top = MmSpacing.lg,
                    bottom = MmSpacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
            ) {
                item {
                    MmCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpendDonutChart(rows = rows, total = total)
                            Spacer(Modifier.width(MmSpacing.lg))
                            Column(Modifier.weight(1f)) {
                                Text("Total spent", style = MmType.caption, color = MmColors.textSecondary)
                                Text(
                                    MoneyFormat.rupeesNoDecimals(Money(total)),
                                    style = MmType.amountLarge,
                                    color = MmColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(MmSpacing.xs))
                                // Month-over-month comparison against the immediately previous month only.
                                if (hasPrevData) {
                                    val diffMinor = total - prevTotal
                                    val isMore = diffMinor > 0
                                    Text(
                                        "${if (isMore) "▲" else "▼"} ${MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(diffMinor)))} vs ${viewModel.previousMonthLabel(selectedIdx)}",
                                        style = MmType.caption,
                                        color = if (isMore) MmColors.expense else MmColors.income,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    // No previous-month data: say so instead of a misleading ₹0 comparison.
                                    viewModel.previousMonthLabel(selectedIdx)?.let { prevLabel ->
                                        Text(
                                            "No spending recorded in $prevLabel",
                                            style = MmType.caption,
                                            color = MmColors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(MmSpacing.xs))
                                    }
                                    Text(
                                        "${rows.size} ${if (rows.size == 1) "category" else "categories"}",
                                        style = MmType.caption,
                                        color = MmColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    MmSectionHeader(
                        title = "By category",
                        subtitle = "Tap a category to see every transaction"
                    )
                }

                items(rows, key = { it.categoryId ?: -1L }) { row ->
                    val color = parseColor(row.colorHex)
                    val fraction = if (total > 0) (row.total.toFloat() / total) else 0f
                    MmCard(
                        onClick = { onCategoryClick(row.categoryId ?: -1L) },
                        contentPadding = PaddingValues(MmSpacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                            }
                            Spacer(Modifier.width(MmSpacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.name,
                                    style = MmType.body,
                                    color = MmColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                MmProgressBar(fraction = fraction, color = color, height = 6.dp)
                            }
                            Spacer(Modifier.width(MmSpacing.md))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    MoneyFormat.rupeesNoDecimals(Money(row.total)),
                                    style = MmType.amountRow,
                                    color = MmColors.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    String.format(Locale.US, "%.0f%%", fraction * 100f),
                                    style = MmType.caption,
                                    color = MmColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendDonutChart(rows: List<SpendCategoryRow>, total: Long) {
    val holeColor = MmColors.surface
    // The ring only shows positive contributions; a category that nets negative (refunds
    // exceeded spend) is a credit, not a wedge. The ring is scaled to those positive rows so it
    // still reads as a full breakdown, while the header total stays the canonical net spend.
    val positiveRows = rows.filter { it.total > 0L }
    val ringTotal = positiveRows.sumOf { it.total }
    // Colours are resolved here (composable scope); the Canvas draw lambda is not composable.
    val rowColors = positiveRows.map { parseColor(it.colorHex) }
    Canvas(modifier = Modifier.size(132.dp)) {
        var startAngle = -90f
        positiveRows.forEachIndexed { index, row ->
            val sweep = if (ringTotal > 0) 360f * row.total / ringTotal else 0f
            val visibleSweep = (sweep - 1.5f).coerceAtLeast(0f)
            drawArc(
                rowColors[index], startAngle + 0.75f, visibleSweep, false,
                style = Stroke(width = 26f, cap = StrokeCap.Butt),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
        // The centre hole uses the current surface colour so it reads correctly in dark mode.
        drawCircle(holeColor, radius = size.minDimension * .31f)
    }
}

/**
 * One bar per month (newest on the right, like the reference). Each bar is that month's total
 * spend only — never cumulative. The selected month is highlighted and drives everything below.
 */
@Composable
private fun MonthBarGraph(
    months: List<MonthWindow>,
    monthlyTotals: List<Long>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    val maxTotal = (monthlyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = MmSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
    ) {
        months.asReversed().forEachIndexed { reversedIdx, month ->
            val idx = months.lastIndex - reversedIdx
            val totalForMonth = monthlyTotals.getOrNull(idx) ?: 0L
            val hasData = totalForMonth > 0L
            val isSelected = idx == selectedIdx
            val fillFraction = if (hasData) (totalForMonth.toFloat() / maxTotal).coerceIn(0.12f, 1f) else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasData) MmColors.accent.copy(alpha = 0.14f) else MmColors.surfaceMuted)
                        .pressableClick { onSelect(idx) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fillFraction)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> MmColors.accent
                                    hasData -> MmColors.accent.copy(alpha = 0.55f)
                                    else -> MmColors.textTertiary.copy(alpha = 0.35f)
                                }
                            )
                    )
                }
                Spacer(Modifier.height(MmSpacing.xs))
                Text(
                    month.label,
                    style = MmType.caption.copy(fontSize = 9.sp),
                    color = if (isSelected) MmColors.accent else MmColors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
            if (reversedIdx != months.lastIndex) {
                Spacer(Modifier.width(MmSpacing.xs))
            }
        }
    }
}

@Composable
private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    MmColors.outline
}
