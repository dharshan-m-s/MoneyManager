package com.moneymanager.app.ui.modern.screens.spendsummary

import android.app.DatePickerDialog

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.components.MmChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.FinanceArtwork
import com.moneymanager.app.ui.modern.screens.categories.CategoryVisual
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
import java.time.LocalDate
import java.time.ZoneId

internal enum class SpendSummaryRange(val label: String) {
    MONTHLY("Monthly"),
    BIMONTHLY("Bimonthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    CUSTOM("Custom")
}

/** Aggregate all historical monthly buckets into calendar-aligned periods. */
internal fun aggregateSummaryHistory(
    history: com.moneymanager.app.ui.spendsummary.DashboardSpendHistory,
    range: SpendSummaryRange
): com.moneymanager.app.ui.spendsummary.DashboardSpendHistory {
    val zone = java.time.ZoneId.of("Asia/Kolkata")
    if (history.months.isEmpty()) return history
    val totalsByMonth = history.months.indices.associate { index ->
        val month = java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(history.months[index].startInclusive).atZone(zone)
        )
        month to (history.totals.getOrNull(index) ?: 0L)
    }
    val oldest = totalsByMonth.keys.minOrNull() ?: return history
    val current = java.time.YearMonth.now(zone)
    val denseMonths = mutableListOf<MonthWindow>()
    val denseTotals = mutableListOf<Long>()
    var cursor = oldest
    while (!cursor.isAfter(current)) {
        val start = cursor.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = cursor.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        denseMonths += MonthWindow(
            start,
            end,
            cursor.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) +
                " '${cursor.year.toString().takeLast(2)}"
        )
        denseTotals += totalsByMonth[cursor] ?: 0L
        cursor = cursor.plusMonths(1)
    }
    val denseHistory = com.moneymanager.app.ui.spendsummary.DashboardSpendHistory(
        months = denseMonths.reversed(),
        totals = denseTotals.reversed()
    )
    if (range == SpendSummaryRange.MONTHLY || range == SpendSummaryRange.CUSTOM) return denseHistory
    val buckets = denseHistory.months.indices.groupBy { index ->
        val month = java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(denseHistory.months[index].startInclusive).atZone(zone)
        )
        when (range) {
            SpendSummaryRange.MONTHLY -> month.toString()
            SpendSummaryRange.BIMONTHLY -> "${month.year}-B${(month.monthValue - 1) / 2}"
            SpendSummaryRange.QUARTERLY -> "${month.year}-Q${(month.monthValue - 1) / 3}"
            SpendSummaryRange.YEARLY -> month.year.toString()
            SpendSummaryRange.CUSTOM -> month.toString()
        }
    }
    val grouped = buckets.values.map { indices ->
        val windows = indices.map { denseHistory.months[it] }
        val start = windows.minBy { it.startInclusive }
        val end = windows.maxBy { it.endExclusive }
        val month = java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(start.startInclusive).atZone(zone)
        )
        val label = when (range) {
            SpendSummaryRange.MONTHLY -> start.label
            SpendSummaryRange.BIMONTHLY -> {
                val first = java.time.YearMonth.of(month.year, ((month.monthValue - 1) / 2) * 2 + 1)
                val last = first.plusMonths(1)
                "${first.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}–" +
                    "${last.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} '${first.year.toString().takeLast(2)}"
            }
            SpendSummaryRange.QUARTERLY -> "Q${(month.monthValue - 1) / 3 + 1} '${month.year.toString().takeLast(2)}"
            SpendSummaryRange.YEARLY -> month.year.toString()
            SpendSummaryRange.CUSTOM -> start.label
        }
        MonthWindow(start.startInclusive, end.endExclusive, label) to
            indices.sumOf { denseHistory.totals.getOrNull(it) ?: 0L }
    }.sortedByDescending { it.first.startInclusive }
    return com.moneymanager.app.ui.spendsummary.DashboardSpendHistory(
        months = grouped.map { it.first },
        totals = grouped.map { it.second }
    )
}

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
    val history by viewModel.dashboardSpendHistory.collectAsState()
    val customHistory by viewModel.customSpendHistory.collectAsState()
    var rangeName by rememberSaveable { mutableStateOf(SpendSummaryRange.MONTHLY.name) }
    var selectedBucketIndex by rememberSaveable { mutableIntStateOf(0) }
    var customStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val range = SpendSummaryRange.valueOf(rangeName)
    val buckets = remember(history, range, customHistory) {
        if (range == SpendSummaryRange.CUSTOM) customHistory else aggregateSummaryHistory(history, range)
    }
    val selectedIdx = selectedBucketIndex.coerceIn(0, (buckets.months.size - 1).coerceAtLeast(0))
    val selectedPeriod = buckets.months.getOrNull(selectedIdx)
    val previousPeriod = buckets.months.getOrNull(selectedIdx + 1)
    val expectedMonths = when (range) {
        SpendSummaryRange.MONTHLY -> 1L
        SpendSummaryRange.BIMONTHLY -> 2L
        SpendSummaryRange.QUARTERLY -> 3L
        SpendSummaryRange.YEARLY -> 12L
        SpendSummaryRange.CUSTOM -> 0L
    }
    fun completeMonthCount(window: MonthWindow): Long {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val first = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(window.startInclusive).atZone(zone))
        val last = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(window.endExclusive - 1L).atZone(zone))
        return java.time.temporal.ChronoUnit.MONTHS.between(first, last) + 1L
    }
    val canCompare = selectedPeriod != null &&
        previousPeriod != null &&
        completeMonthCount(selectedPeriod) == expectedMonths &&
        completeMonthCount(previousPeriod) == expectedMonths &&
        selectedPeriod.endExclusive <= System.currentTimeMillis() &&
        range != SpendSummaryRange.CUSTOM
    val positiveCategoryTotal = rows.asSequence().filter { it.total > 0L }.sumOf { it.total }
    val context = LocalContext.current
    val zone = ZoneId.of("Asia/Kolkata")
    fun openCustomDatePicker(selectStart: Boolean) {
        val fallback = if (selectStart) LocalDate.now(zone).minusMonths(5) else LocalDate.now(zone)
        val currentMillis = if (selectStart) customStartMillis else customEndMillis
        val date = currentMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: fallback
        DatePickerDialog(context, { _, year, month, day ->
            val picked = LocalDate.of(year, month + 1, day).atStartOfDay(zone).toInstant().toEpochMilli()
            if (selectStart) {
                customStartMillis = picked
                if (customEndMillis == null || customEndMillis!! < picked) customEndMillis = picked
            } else {
                customEndMillis = picked
                if (customStartMillis == null || customStartMillis!! > picked) customStartMillis = picked
            }
            val start = customStartMillis
            val end = customEndMillis
            if (start != null && end != null) viewModel.selectCustomRange(start, end)
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    LaunchedEffect(rangeName, buckets.months) {
        selectedBucketIndex = 0
    }
    LaunchedEffect(selectedIdx, buckets.months) {
        selectedPeriod?.let { viewModel.selectPeriod(it, previousPeriod) }
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Spend summary",
            subtitle = selectedPeriod?.label.orEmpty(),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MmSpacing.screen,
                end = MmSpacing.screen,
                top = MmSpacing.sm,
                bottom = MmSpacing.xxxl + com.moneymanager.app.ui.components.LocalDockContentPadding.current
            ),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.lg)
        ) {
            item {
                MonthBarGraph(
                    months = buckets.months,
                    monthlyTotals = buckets.totals,
                    selectedIdx = selectedIdx,
                    rangeMode = range,
                    onRangeChange = { selectedRange ->
                        rangeName = selectedRange.name
                        if (selectedRange == SpendSummaryRange.CUSTOM) {
                            val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                            val start = LocalDate.now(zone).minusMonths(5).withDayOfMonth(1)
                                .atStartOfDay(zone).toInstant().toEpochMilli()
                            customStartMillis = start
                            customEndMillis = today
                            viewModel.selectCustomRange(start, today)
                            openCustomDatePicker(true)
                        }
                    },
                    customStartLabel = customStartMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() } ?: "Start date",
                    customEndLabel = customEndMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() } ?: "End date",
                    onChooseCustomStart = { openCustomDatePicker(true) },
                    onChooseCustomEnd = { openCustomDatePicker(false) },
                    onSelect = { selectedBucketIndex = it }
                )
            }

            item {
                SpendOverviewCard(
                    rows = rows,
                    total = total,
                    hasPrevData = hasPrevData && canCompare,
                    previousTotal = prevTotal,
                    previousLabel = previousPeriod?.label.takeIf { canCompare }
                )
            }

            if (rows.isEmpty()) {
                item {
                    MmEmptyState(
                        icon = Icons.Filled.PieChart,
                        title = "No spending in ${selectedPeriod?.label.orEmpty()}",
                        message = "When you record an expense, your spending will appear here by category.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = MmSpacing.xl)
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Category breakdown", style = MmType.sectionTitle, color = MmColors.textPrimary)
                        Text(
                            "Your spending for ${selectedPeriod?.label.orEmpty()}, grouped by category",
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
    val comparisonColor = if (difference > 0L) MmColors.expense else MmColors.income
    val shape = RoundedCornerShape(28.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MmColors.surfaceContainer)
            .padding(MmSpacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TOTAL SPENT",
                    style = MmType.caption.copy(fontSize = 10.sp, letterSpacing = 1.05.sp),
                    color = MmColors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(MmSpacing.sm))
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(total)),
                    style = MmType.amountLarge,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MmSpacing.sm))
                if (hasPrevData && previousLabel != null) {
                    val direction = if (difference > 0L) "↑" else if (difference < 0L) "↓" else "•"
                    Text(
                        "$direction ${MoneyFormat.rupeesNoDecimals(Money(kotlin.math.abs(difference)))} vs $previousLabel",
                        style = MmType.caption,
                        color = comparisonColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        previousLabel?.let { "No spending in $it" } ?: "${rows.size} categories",
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (rows.isNotEmpty()) {
                SpendDonutChart(rows = rows, total = total, modifier = Modifier.size(112.dp))
            } else {
                SpendEmptyGraphic(Modifier.size(112.dp))
            }
        }
        Spacer(Modifier.height(MmSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${rows.size} ${if (rows.size == 1) "category" else "categories"}",
                style = MmType.label,
                color = MmColors.textPrimary
            )
            Spacer(Modifier.weight(1f))
            Text("Tap a category to inspect", style = MmType.caption, color = MmColors.textTertiary)
        }
    }
}

@Composable
private fun SpendEmptyGraphic(modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "emptySpendGraphicReveal"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.39f
            val inner = size.minDimension * 0.27f
            drawCircle(Color.White.copy(alpha = 0.10f), radius = outer, center = center, style = Stroke(width = 5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = inner, center = center, style = Stroke(width = 4.dp.toPx()))
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFFB9B8FF), Color(0xFF6F79F5), Color(0xFFB9B8FF))),
                startAngle = -90f,
                sweepAngle = 285f * reveal,
                useCenter = false,
                topLeft = Offset(center.x - outer, center.y - outer),
                size = Size(outer * 2f, outer * 2f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(Color.White.copy(alpha = 0.72f), radius = 3.dp.toPx(), center = Offset(center.x + outer * 0.72f, center.y - outer * 0.72f))
            drawCircle(Color(0xFFB9B8FF), radius = 2.5.dp.toPx(), center = Offset(center.x - outer * 0.82f, center.y + outer * 0.35f))
        }
        Text("—", color = Color.White.copy(alpha = 0.82f), style = MmType.sectionTitle)
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
    rangeMode: SpendSummaryRange,
    onRangeChange: (SpendSummaryRange) -> Unit,
    customStartLabel: String,
    customEndLabel: String,
    onChooseCustomStart: () -> Unit,
    onChooseCustomEnd: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val chronologicalIndices = remember(months) { months.indices.reversed().toList() }
    val selectedTotal = monthlyTotals.getOrNull(selectedIdx) ?: 0L
    val selectedLabel = months.getOrNull(selectedIdx)?.label.orEmpty()
    val maxTotal = (monthlyTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)

    MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Spending trend", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text("Every month in your recorded timeline", style = MmType.caption, color = MmColors.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        MoneyFormat.rupeesNoDecimals(Money(selectedTotal)),
                        style = MmType.amountRow,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(selectedLabel, style = MmType.caption, color = MmColors.accent)
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs)
            ) {
                SpendSummaryRange.entries.forEach { option ->
                    MmChip(
                        label = option.label,
                        selected = rangeMode == option,
                        onClick = { onRangeChange(option) }
                    )
                }
            }

            if (rangeMode == SpendSummaryRange.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                    androidx.compose.material.TextButton(onClick = onChooseCustomStart) {
                        Text(customStartLabel, color = MmColors.accent)
                    }
                    androidx.compose.material.TextButton(onClick = onChooseCustomEnd) {
                        Text(customEndLabel, color = MmColors.accent)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (rangeMode) {
                        SpendSummaryRange.MONTHLY -> "Monthly view"
                        SpendSummaryRange.BIMONTHLY -> "Two-month view"
                        SpendSummaryRange.QUARTERLY -> "Quarterly view"
                        SpendSummaryRange.YEARLY -> "Yearly view"
                        SpendSummaryRange.CUSTOM -> "Custom date range"
                    },
                    style = MmType.caption,
                    color = MmColors.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${months.size} periods",
                    style = MmType.caption.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                    color = MmColors.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (chronologicalIndices.isNotEmpty()) {
                val listState = rememberLazyListState()
                LaunchedEffect(rangeMode, months.size) {
                    listState.scrollToItem((chronologicalIndices.size - 6).coerceAtLeast(0))
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(224.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    itemsIndexed(
                        items = chronologicalIndices,
                        key = { _, index -> months[index].startInclusive }
                    ) { _, index ->
                        SummaryMonthBar(
                            month = months[index],
                            amount = monthlyTotals.getOrNull(index) ?: 0L,
                            maxTotal = maxTotal,
                            selected = index == selectedIdx,
                            modifier = Modifier.width(56.dp),
                            onClick = { onSelect(index) }
                        )
                    }
                }
            } else {
                MmEmptyState(
                    icon = Icons.Filled.PieChart,
                    title = "No spending history yet",
                    message = "Your monthly, quarterly and yearly bars will appear here as you record transactions."
                )
            }
        }
    }
}

@Composable
internal fun SummaryMonthBar(
    month: MonthWindow,
    amount: Long,
    maxTotal: Long,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val magnitude = kotlin.math.abs(amount)
    val fraction by animateFloatAsState(
        targetValue = if (magnitude > 0L) (magnitude.toDouble() / maxTotal.coerceAtLeast(1L).toDouble())
            .toFloat().coerceIn(0.035f, 1f) else 0f,
        animationSpec = tween(600),
        label = "summaryTimelineBar"
    )
    val parsed = runCatching {
        java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(month.startInclusive).atZone(java.time.ZoneId.of("Asia/Kolkata"))
        )
    }.getOrNull()
    // Resolve composable theme values before entering the non-composable Canvas draw scope.
    val chartOutline = MmColors.outline
    val chartIncome = MmColors.income
    val chartAccent = MmColors.accent
    val chartAccentFill = MmColors.accentFill
    Column(
        modifier.fillMaxHeight()
            .pressableClick(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (amount == 0L) "₹0" else if (selected) MoneyFormat.rupeesNoDecimals(Money(amount)) else " ",
            style = MmType.caption.copy(fontSize = 9.sp, fontWeight = if (selected || amount == 0L) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) MmColors.accent else MmColors.textTertiary,
            maxLines = 1,
            softWrap = false
        )
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val baseline = size.height * 0.78f
            val barWidth = size.width * if (selected) 0.64f else 0.54f
            val barHeight = if (amount == 0L) 5.dp.toPx() else {
                val available = if (amount > 0L) baseline else size.height - baseline
                (available * fraction).coerceAtLeast(3.dp.toPx())
            }
            val left = (size.width - barWidth) / 2f
            val top = if (amount >= 0L) baseline - barHeight else baseline
            drawLine(
                color = chartOutline.copy(alpha = 0.34f),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx()
            )
            val colors = when {
                amount == 0L -> listOf(chartOutline.copy(alpha = 0.22f), chartOutline.copy(alpha = 0.48f))
                amount < 0L -> listOf(chartIncome.copy(alpha = 0.62f), chartIncome)
                selected -> listOf(chartAccent, chartAccentFill)
                else -> listOf(chartAccent.copy(alpha = 0.38f), chartAccent.copy(alpha = 0.88f))
            }
            val safeTop = top.coerceIn(0f, size.height)
            val safeHeight = barHeight.coerceAtMost((size.height - safeTop).coerceAtLeast(0f))
            drawRoundRect(
                brush = Brush.verticalGradient(colors = colors, startY = safeTop, endY = (safeTop + safeHeight).coerceAtLeast(safeTop + 1f)),
                topLeft = Offset(left, safeTop),
                size = Size(barWidth, safeHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            parsed?.month?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                ?: month.label.substringBefore(" "),
            style = MmType.caption.copy(fontSize = 10.sp),
            color = if (selected) MmColors.accent else MmColors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Text(parsed?.year?.toString()?.takeLast(2) ?: "",
            style = MmType.caption.copy(fontSize = 8.sp), color = MmColors.textTertiary, maxLines = 1)
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
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f)))).padding(2.dp), contentAlignment = Alignment.Center) {
                if (row.category != null) {
                    CategoryVisual(category = row.category, size = 52.dp)
                } else {
                    FinanceArtwork(kind = "category_uncategorized", tint = color, modifier = Modifier.size(52.dp))
                }
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
