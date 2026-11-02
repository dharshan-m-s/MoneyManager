package com.moneymanager.app.ui.modern.screens.spendsummary

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.mmGlassHighlight
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed
import com.moneymanager.app.ui.spendsummary.CategoryTransactionsViewModel
import com.moneymanager.app.ui.spendsummary.MonthWindow
import com.moneymanager.app.ui.components.pressableClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Transactions for one category within the SELECTED Spend Areas month (never all-time). Uses the
 * same canonical transaction row as the rest of the app, so tapping anything opens the one shared
 * transaction detail screen.
 */
@Composable
fun CategoryTransactionsScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: CategoryTransactionsViewModel = hiltViewModel()
) {
    val rowsFlow = remember(categoryId, viewModel) { viewModel.transactions(categoryId) }
    val rows by rowsFlow.collectAsState()
    var title by remember { mutableStateOf("Category") }
    LaunchedEffect(categoryId) { title = viewModel.categoryName(categoryId) }
    val monthWindow by viewModel.spendAreasSelection.window.collectAsState()
    val monthLabel = remember(monthWindow) { viewModel.monthLabel(monthWindow) }
    val months = viewModel.availableMonths
    val selectedIdx by viewModel.selectedMonthIndex.collectAsState()
    val monthlyTotalsFlow = remember(categoryId, viewModel) { viewModel.monthlyTotals(categoryId) }
    val monthlyTotals by monthlyTotalsFlow.collectAsState()

    val spent = rows.sumOf { it.debitMinorUnits - it.creditMinorUnits }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = "${rows.size} ${if (rows.size == 1) "transaction" else "transactions"} • $monthLabel",
            onBack = onBack
        )
        if (months.isNotEmpty()) {
            MonthBarGraph(
                months = months,
                monthlyTotals = monthlyTotals,
                selectedIdx = selectedIdx,
                onSelect = viewModel::selectMonth
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
                    caption = "Transactions included in this selected month",
                    tint = MmColors.expense,
                    animatedValueMinorUnits = spent,
                    visualValues = monthlyTotals,
                    visualLabel = "MONTHLY TREND"
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

/**
 * One bar per month for quick selection in category drill-down.
 */
@Composable
private fun MonthBarGraph(months: List<MonthWindow>, monthlyTotals: List<Long>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    val maxTotal = (monthlyTotals.maxOfOrNull { kotlin.math.abs(it) } ?: 0L).coerceAtLeast(1L)
    LazyRow(modifier = Modifier.fillMaxWidth().height(112.dp), contentPadding = PaddingValues(horizontal = MmSpacing.lg), horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
        itemsIndexed(months.asReversed(), key = { _, month -> month.startInclusive }) { reversedIdx, month ->
            val idx = months.lastIndex - reversedIdx
            val amount = monthlyTotals.getOrNull(idx) ?: 0L
            val magnitude = kotlin.math.abs(amount)
            val selected = idx == selectedIdx
            val fraction = if (magnitude > 0L) (magnitude.toFloat() / maxTotal.toFloat()).coerceIn(0.08f, 1f) else 0f
            Column(Modifier.width(52.dp).fillMaxHeight().pressableClick { onSelect(idx) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (amount == 0L) "₹0" else MoneyFormat.rupeesNoDecimals(Money(amount)), style = MmType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = if (selected) MmColors.accent else MmColors.textTertiary, maxLines = 1, softWrap = false)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(12.dp)).background(MmColors.surfaceMuted.copy(alpha = 0.72f)), contentAlignment = Alignment.BottomCenter) {
                        if (fraction > 0f) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(MmColors.accent, MmColors.accentFill))))
                        } else {
                            Box(Modifier.fillMaxWidth(0.42f).height(3.dp).clip(CircleShape).background(MmColors.outline))
                        }
                    }
                }
                Text(month.label.substringBefore(" "), style = MmType.caption.copy(fontSize = 10.sp), color = if (selected) MmColors.accent else MmColors.textSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                Text(month.label.substringAfter(" '", ""), style = MmType.caption.copy(fontSize = 8.sp), color = MmColors.textTertiary, maxLines = 1)
            }
        }
    }
}
