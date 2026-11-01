package com.moneymanager.app.ui.modern.screens.spendsummary

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    // Percentages describe the positive category outflows shown in the ring; refunds or
    // credit-heavy categories remain visible as credits instead of distorting the chart.
    val positiveCategoryTotal = rows.asSequence().filter { it.total > 0L }.sumOf { it.total }

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
                    val fraction = if (row.total > 0L && positiveCategoryTotal > 0L) {
                        row.total.toFloat() / positiveCategoryTotal.toFloat()
                    } else 0f
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
                                    if (row.total < 0L) "Credit" else String.format(Locale.US, "%.0f%%", fraction * 100f),
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
    val positiveRows = rows.filter { it.total > 0L }
    val ringTotal = positiveRows.sumOf { it.total }.coerceAtLeast(1L)
    val rowColors = positiveRows.map { parseColor(it.colorHex) }
    val reveal = remember(rows, total) { Animatable(0f) }
    val holeColor = MmColors.surface

    LaunchedEffect(rows, total) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 680))
    }

    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.18f
            var startAngle = -90f
            positiveRows.forEachIndexed { index, row ->
                val fullSweep = 360f * row.total.toFloat() / ringTotal.toFloat()
                val sweep = fullSweep * reveal.value
                val gap = 2.2f
                drawArc(
                    color = rowColors[index],
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(size.width, size.height)
                )
                startAngle += fullSweep
            }
            drawCircle(
                color = holeColor,
                radius = size.minDimension * 0.31f
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                "TOTAL",
                style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                color = MmColors.textTertiary
            )
            Text(
                MoneyFormat.rupeesNoDecimals(Money(total)),
                style = MmType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = MmColors.textPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Six-month selector presented as a compact financial chart. Newest month stays on
 * the right, and selecting a bar updates the full category breakdown below it.
 */
@Composable
private fun MonthBarGraph(
    months: List<MonthWindow>,
    monthlyTotals: List<Long>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    val maxTotal = (monthlyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val selectedTotal = monthlyTotals.getOrNull(selectedIdx) ?: 0L
    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm),
        contentPadding = PaddingValues(MmSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Monthly spending", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text("Last six months", style = MmType.caption, color = MmColors.textTertiary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(selectedTotal)),
                    style = MmType.amountRow,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    months.getOrNull(selectedIdx)?.label.orEmpty(),
                    style = MmType.caption,
                    color = MmColors.accent
                )
            }
        }
        Spacer(Modifier.height(MmSpacing.lg))
        Row(
            Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            months.asReversed().forEachIndexed { reversedIdx, month ->
                val idx = months.lastIndex - reversedIdx
                val amount = monthlyTotals.getOrNull(idx) ?: 0L
                val selected = idx == selectedIdx
                val targetFraction = if (amount > 0L) {
                    (amount.toFloat() / maxTotal.toFloat()).coerceIn(0.08f, 1f)
                } else 0f
                val barFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 520),
                    label = "monthBarHeight"
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(MmColors.surfaceContainer)
                            .pressableClick { onSelect(idx) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.58f)
                                .fillMaxHeight(barFraction)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) MmColors.accent
                                    else MmColors.accent.copy(alpha = 0.46f)
                                )
                        )
                    }
                    Spacer(Modifier.height(MmSpacing.xs))
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
    }
}

@Composable
private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    MmColors.outline
}
