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
import androidx.compose.ui.graphics.Brush
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
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.spendsummary.MonthWindow
import com.moneymanager.app.ui.spendsummary.SpendCategoryRow
import com.moneymanager.app.ui.spendsummary.SpendSummaryViewModel
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.util.Locale

/**
 * Monthly spend analytics. All amounts and category rows are supplied by the existing
 * Room-backed SpendSummaryViewModel; this screen is presentation-only.
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
    val selectedMonth = months.getOrNull(selectedIdx)
    val positiveCategoryTotal = rows.asSequence().filter { it.total > 0L }.sumOf { it.total }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Spend summary",
            subtitle = selectedMonth?.label.orEmpty(),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MmSpacing.screen,
                end = MmSpacing.screen,
                top = MmSpacing.sm,
                bottom = MmSpacing.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            item {
                MonthBarGraph(
                    months = months,
                    monthlyTotals = monthlyTotals,
                    selectedIdx = selectedIdx,
                    onSelect = viewModel::selectMonth
                )
            }

            item {
                SpendOverviewCard(
                    rows = rows,
                    total = total,
                    hasPrevData = hasPrevData,
                    previousTotal = prevTotal,
                    previousLabel = viewModel.previousMonthLabel(selectedIdx)
                )
            }

            if (rows.isEmpty()) {
                item {
                    MmEmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "No spending in ${selectedMonth?.label.orEmpty()}",
                        message = "When you record an expense, your spending will appear here by category.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = MmSpacing.xl)
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Category breakdown", style = MmType.sectionTitle, color = MmColors.textPrimary)
                        Text(
                            "Your spending, grouped by category",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                    }
                }

                items(rows, key = { "${it.categoryId ?: -1L}:${it.name}" }) { row ->
                    SpendCategoryCard(
                        row = row,
                        fraction = if (row.total > 0L && positiveCategoryTotal > 0L) {
                            row.total.toFloat() / positiveCategoryTotal.toFloat()
                        } else 0f,
                        onClick = { row.categoryId?.let(onCategoryClick) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendOverviewCard(
    rows: List<SpendCategoryRow>,
    total: Long,
    hasPrevData: Boolean,
    previousTotal: Long,
    previousLabel: String?
) {
    val difference = total - previousTotal
    val comparisonColor = if (difference > 0L) Color(0xFFFFD2D2) else Color(0xFFBDF4DA)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(MmColors.heroGradient))
            .padding(horizontal = MmSpacing.xl, vertical = MmSpacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TOTAL SPENT",
                    style = MmType.caption.copy(fontSize = 11.sp, letterSpacing = 1.1.sp),
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(total)),
                    style = MmType.amountLarge,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MmSpacing.sm))
                if (hasPrevData && previousLabel != null) {
                    val direction = if (difference > 0L) "↑" else if (difference < 0L) "↓" else "•"
                    Text(
                        "${direction} ${MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(difference)))} vs ${previousLabel}",
                        style = MmType.caption,
                        color = comparisonColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        previousLabel?.let { "No spending in $it" } ?: "${rows.size} categories",
                        style = MmType.caption,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(MmSpacing.md))
                Text(
                    "${rows.size} ${if (rows.size == 1) "category" else "categories"}",
                    style = MmType.label,
                    color = Color.White.copy(alpha = 0.86f)
                )
            }
            if (rows.isNotEmpty()) {
                SpendDonutChart(rows = rows, total = total, modifier = Modifier.size(112.dp))
            } else {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = Color.White.copy(alpha = 0.72f), style = MmType.sectionTitle)
                }
            }
        }
    }
}

@Composable
private fun SpendDonutChart(
    rows: List<SpendCategoryRow>,
    total: Long,
    modifier: Modifier = Modifier
) {
    val positiveRows = rows.filter { it.total > 0L }
    val ringTotal = positiveRows.sumOf { it.total }.coerceAtLeast(1L)
    val rowColors = positiveRows.map { parseColor(it.colorHex) }
    val reveal = remember(rows, total) { Animatable(0f) }

    LaunchedEffect(rows, total) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 720))
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            var startAngle = -90f
            positiveRows.forEachIndexed { index, row ->
                val fullSweep = 360f * row.total.toFloat() / ringTotal.toFloat()
                val sweep = fullSweep * reveal.value
                drawArc(
                    color = rowColors[index],
                    startAngle = startAngle + 2f,
                    sweepAngle = (sweep - 4f).coerceAtLeast(0f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(size.width, size.height)
                )
                startAngle += fullSweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AREAS", style = MmType.caption.copy(fontSize = 9.sp, letterSpacing = 0.8.sp), color = Color.White.copy(alpha = 0.68f))
            Text(
                positiveRows.size.toString(),
                style = MmType.amountLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun MonthBarGraph(
    months: List<MonthWindow>,
    monthlyTotals: List<Long>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    val maxTotal = (monthlyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val selectedTotal = monthlyTotals.getOrNull(selectedIdx) ?: 0L

    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Spending trend", style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text("Select a month to explore", style = MmType.caption, color = MmColors.textSecondary)
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
            Modifier.fillMaxWidth().height(116.dp),
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
        ) {
            months.asReversed().forEachIndexed { reversedIdx, month ->
                val index = months.lastIndex - reversedIdx
                val amount = monthlyTotals.getOrNull(index) ?: 0L
                val selected = index == selectedIdx
                val targetFraction = if (amount > 0L) {
                    (amount.toFloat() / maxTotal.toFloat()).coerceIn(0.08f, 1f)
                } else 0f
                val barFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 560),
                    label = "spendSummaryBar"
                )

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (selected) MmColors.accent.copy(alpha = 0.10f)
                                else MmColors.surfaceContainer
                            )
                            .pressableClick { onSelect(index) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (barFraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (selected) 0.70f else 0.56f)
                                    .fillMaxHeight(barFraction)
                                    .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            if (selected) listOf(MmColors.accent.copy(alpha = 0.68f), MmColors.accent)
                                            else listOf(MmColors.accent.copy(alpha = 0.28f), MmColors.accent.copy(alpha = 0.72f))
                                        )
                                    )
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.40f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(MmColors.outline)
                            )
                        }
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
private fun SpendCategoryCard(
    row: SpendCategoryRow,
    fraction: Float,
    onClick: () -> Unit
) {
    val color = parseColor(row.colorHex)
    val isCredit = row.total < 0L
    MmCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = MmSpacing.md, vertical = MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(color.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(13.dp).clip(CircleShape).background(color))
            }
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MmType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                if (!isCredit) {
                    MmProgressBar(fraction = fraction, color = color, height = 6.dp)
                } else {
                    Text("Net credit this month", style = MmType.caption, color = MmColors.income)
                }
            }
            Spacer(Modifier.width(MmSpacing.md))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(row.total)),
                    style = MmType.amountRow,
                    color = if (isCredit) MmColors.income else MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isCredit) "Credit" else String.format(Locale.US, "%.0f%%", fraction * 100f),
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    MmColors.outline
}
