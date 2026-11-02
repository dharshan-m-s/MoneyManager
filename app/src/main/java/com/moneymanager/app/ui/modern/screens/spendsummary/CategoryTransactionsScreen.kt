package com.moneymanager.app.ui.modern.screens.spendsummary

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.height
import androidx.compose.foundation.padding
import androidx.compose.foundation.width
import androidx.compose.foundation.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MmChip
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.spendsummary.CategoryTransactionsViewModel
import com.moneymanager.app.ui.spendsummary.DashboardSpendHistory
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId
import java.time.format.DateTimeFormatter
@Composable
fun CategoryTransactionsScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: CategoryTransactionsViewModel = hiltViewModel()
) {
    val zone = remember { ZoneId.of("Asia/Kolkata") }
    val rowsFlow = remember(categoryId, viewModel) { viewModel.transactions(categoryId) }
    val rows by rowsFlow.collectAsState()
    val historyFlow = remember(categoryId, viewModel) { viewModel.categoryHistory(categoryId) }
    val history by historyFlow.collectAsState()
    var title by remember { mutableStateOf("Category") }
    LaunchedEffect(categoryId) { title = viewModel.categoryName(categoryId) }

    var rangeName by rememberSaveable { mutableStateOf(SpendSummaryRange.MONTHLY.name) }
    val range = SpendSummaryRange.valueOf(rangeName)
    var selectedBucketIndex by rememberSaveable { mutableStateOf(0) }
    var customStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

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
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    val customHistoryFlow = remember(categoryId, customStartMillis, customEndMillis, viewModel) {
        val start = customStartMillis
        val end = customEndMillis
        if (start != null && end != null && end >= start) {
            viewModel.customCategoryHistory(categoryId, start, end + 86_400_000L)
        } else {
            flowOf(DashboardSpendHistory(emptyList(), emptyList()))
        }
    }
    val customHistory by customHistoryFlow.collectAsState()
    val chartHistory = remember(history, customHistory, range) {
        if (range == SpendSummaryRange.CUSTOM) customHistory else aggregateSummaryHistory(history, range)
    }
    val monthWindow by viewModel.spendAreasSelection.window.collectAsState()
    LaunchedEffect(rangeName, chartHistory.months, monthWindow) {
        val selectedWindow = monthWindow
        val matchingPeriod = selectedWindow?.let { window ->
            chartHistory.months.indexOfFirst {
                it.startInclusive == window.first && it.endExclusive == window.second
            }
        } ?: -1
        selectedBucketIndex = matchingPeriod.takeIf { it >= 0 } ?: 0
    }
    val selectedIndex = selectedBucketIndex.coerceIn(0, (chartHistory.months.size - 1).coerceAtLeast(0))
    val selectedPeriod = chartHistory.months.getOrNull(selectedIndex)
    LaunchedEffect(monthWindow) {
        monthWindow?.let { window ->
            val start = java.time.Instant.ofEpochMilli(window.first).atZone(zone).toLocalDate()
            val end = java.time.Instant.ofEpochMilli(window.second - 1L).atZone(zone).toLocalDate()
            val monthCount = java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.YearMonth.from(start), java.time.YearMonth.from(end)
            ) + 1L
            val exactCalendarWindow = start.dayOfMonth == 1 && end.dayOfMonth == end.lengthOfMonth()
            rangeName = when {
                !exactCalendarWindow -> SpendSummaryRange.CUSTOM.name
                monthCount == 2L -> SpendSummaryRange.BIMONTHLY.name
                monthCount == 3L -> SpendSummaryRange.QUARTERLY.name
                monthCount == 12L -> SpendSummaryRange.YEARLY.name
                monthCount == 1L -> SpendSummaryRange.MONTHLY.name
                else -> SpendSummaryRange.CUSTOM.name
            }
            if (!exactCalendarWindow) {
                customStartMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
                customEndMillis = end.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
    }
    val monthLabel = remember(monthWindow, selectedPeriod) {
        selectedPeriod?.label ?: monthWindow?.let { window ->
            val start = java.time.Instant.ofEpochMilli(window.first).atZone(zone).toLocalDate()
            val end = java.time.Instant.ofEpochMilli(window.second - 1L).atZone(zone).toLocalDate()
            val formatter = DateTimeFormatter.ofPattern("MMM ''yy")
            if (start.withDayOfMonth(1) == end.withDayOfMonth(1)) formatter.format(start)
            else formatter.format(start) + " – " + formatter.format(end)
        } ?: viewModel.monthLabel(null)
    }
    val spent = rows.sumOf { it.debitMinorUnits - it.creditMinorUnits }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = "${rows.size} ${if (rows.size == 1) "transaction" else "transactions"} • $monthLabel",
            onBack = onBack
        )
        if (history.months.isNotEmpty()) {
            CategoryHistoryGraph(
                history = chartHistory,
                selectedIndex = selectedIndex,
                range = range,
                customStartLabel = customStartMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() } ?: "Start date",
                customEndLabel = customEndMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() } ?: "End date",
                onRangeChange = { selected ->
                    rangeName = selected.name
                    if (selected == SpendSummaryRange.CUSTOM) {
                        val today = LocalDate.now(zone)
                        customStartMillis = today.minusMonths(5).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        customEndMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
                        openCustomDatePicker(true)
                    }
                },
                onChooseCustomStart = { openCustomDatePicker(true) },
                onChooseCustomEnd = { openCustomDatePicker(false) },
                onSelect = { index ->
                    selectedBucketIndex = index
                    chartHistory.months.getOrNull(index)?.let { period ->
                        viewModel.selectMonth(period.startInclusive, period.endExclusive)
                    }
                }
            )
            Spacer(Modifier.height(MmSpacing.sm))
        }

        if (rows.isEmpty()) {
            MmEmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No transactions in $title",
                message = "Transactions you record in this category will be listed here.",
                modifier = Modifier.fillMaxSize()
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MmSpacing.lg,
                end = MmSpacing.lg,
                top = MmSpacing.lg,
                bottom = MmSpacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.xs)
        ) {
            item {
                MmBalancePanel(
                    label = "NET SPEND · $monthLabel",
                    value = MoneyFormat.rupeesNoDecimals(Money(spent)),
                    caption = "Transactions included in this selected period",
                    tint = MmColors.expense,
                    animatedValueMinorUnits = spent,
                    visualValues = chartHistory.totals,
                    visualLabel = "SPENDING TREND"
                )
                Spacer(Modifier.height(MmSpacing.sm))
            }
            itemsIndexed(rows, key = { _, txn -> txn.id }) { index, txn ->
                MmTransactionRow(
                    txn = txn,
                    onClick = { onTransactionClick(txn.id) },
                    modifier = Modifier.revealOnAppear(index = index)
                )
            }
        }
    }
}

@Composable
private fun CategoryHistoryGraph(
    history: DashboardSpendHistory,
    selectedIndex: Int,
    range: SpendSummaryRange,
    customStartLabel: String,
    customEndLabel: String,
    onRangeChange: (SpendSummaryRange) -> Unit,
    onChooseCustomStart: () -> Unit,
    onChooseCustomEnd: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val chronologicalIndices = remember(history.months) { history.months.indices.reversed().toList() }
    val selectedTotal = history.totals.getOrNull(selectedIndex) ?: 0L
    val selectedLabel = history.months.getOrNull(selectedIndex)?.label.orEmpty()
    val maxTotal = (history.totals.maxOfOrNull { kotlin.math.abs(it) } ?: 0L).coerceAtLeast(1L)
    com.moneymanager.app.ui.components.MmCard(contentPadding = PaddingValues(MmSpacing.lg)) {
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
                    MmChip(label = option.label, selected = range == option, onClick = { onRangeChange(option) })
                }
            }
            if (range == SpendSummaryRange.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                    TextButton(onClick = onChooseCustomStart) { Text(customStartLabel, color = MmColors.accent) }
                    TextButton(onClick = onChooseCustomEnd) { Text(customEndLabel, color = MmColors.accent) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (range) {
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
                    "${history.months.size} periods",
                    style = MmType.caption.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                    color = MmColors.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (chronologicalIndices.isNotEmpty()) {
                val listState = rememberLazyListState()
                LaunchedEffect(range, history.months.size) {
                    listState.scrollToItem((chronologicalIndices.size - 6).coerceAtLeast(0))
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(224.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    itemsIndexed(chronologicalIndices, key = { _, index -> history.months[index].startInclusive }) { _, index ->
                        SummaryMonthBar(
                            month = history.months[index],
                            amount = history.totals.getOrNull(index) ?: 0L,
                            maxTotal = maxTotal,
                            selected = index == selectedIndex,
                            modifier = Modifier.width(56.dp),
                            onClick = { onSelect(index) }
                        )
                    }
                }
            } else {
                Text("No spending history yet", style = MmType.body, color = MmColors.textSecondary)
            }
        }
    }
}
