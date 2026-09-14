package com.moneymanager.app.ui.income

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.PageIncomeStart
import com.moneymanager.app.ui.theme.PageIncomeEnd
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MMGreenIncome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dedicated income analytics - the income counterpart of "Spend Areas". Shows this month's
 * income split by category. Income toggled OFF in the Add screen ("Income ON/OFF") is listed
 * under "Not counted in income totals" so an off-toggle never makes the money invisible.
 */
@Composable
fun IncomeSummaryScreen(
    onBack: () -> Unit,
    onCategoryClick: (Long, TxnSubType, Boolean) -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: IncomeSummaryViewModel = hiltViewModel()
) {
    val rows by viewModel.categoryRows.collectAsState()
    val excluded by viewModel.excludedIncome.collectAsState()
    val total = rows.sumOf { it.total }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(PageIncomeStart, PageIncomeEnd)))
                .padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                }
                Column {
                    Text("Income Areas", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("Income received this month", color = MMWhite.copy(alpha = .75f), fontSize = 11.sp)
                }
            }
        }

        if (rows.isEmpty() && excluded.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.PieChart, contentDescription = null, tint = MMGrayText)
                Text(
                    "No income recorded for this month yet.",
                    color = MMGrayText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (rows.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IncomeDonutChart(rows, total)
                            Spacer(Modifier.padding(start = 18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("This month", color = MMGrayText, fontSize = 12.sp)
                                Text(MoneyFormat.rupeesNoDecimals(Money(total)), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                                Text("Tap a category to inspect every income transaction", color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                items(rows, key = { it.categoryId ?: -1L }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onCategoryClick(row.categoryId ?: -1L, TxnSubType.INCOME, true) },
                        elevation = 1.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(parseIncomeColor(row.colorHex), CircleShape)
                                )
                                Text(row.name, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(MoneyFormat.rupeesNoDecimals(Money(row.total)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                val pct = if (total > 0) (row.total * 100.0 / total) else 0.0
                                Text(String.format(Locale.US, "%.1f %%", pct), fontSize = 11.sp, color = MMGrayText)
                            }
                        }
                    }
                }
            }

            if (excluded.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(14.dp))
                        Text("Not counted in income totals", color = MMGrayText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                    Text("Income you excluded from statistics still counts in your ledger and is shown here.", color = MMGrayText, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(excluded, key = { it.id }) { txn ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onTransactionClick(txn.id) },
                        elevation = 1.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(txn.rawCategoryName ?: "", fontSize = 11.sp, color = MMGrayText, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(MoneyFormat.rupeesNoDecimals(Money(txn.creditMinorUnits - txn.debitMinorUnits)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MMGreenIncome)
                                Text(DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))), fontSize = 10.sp, color = MMGrayText)
                            }
                        }
                    }
                    Divider(color = MMGrayDivider.copy(alpha = .4f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun IncomeDonutChart(rows: List<IncomeCategoryRow>, total: Long) {
    Canvas(modifier = Modifier.size(156.dp)) {
        var startAngle = -90f
        rows.forEach { row ->
            val sweep = if (total > 0) 360f * row.total / total else 0f
            val visibleSweep = (sweep - 1.5f).coerceAtLeast(0f)
            drawArc(
                parseIncomeColor(row.colorHex), startAngle + 0.75f, visibleSweep, false,
                style = Stroke(width = 30f, cap = StrokeCap.Butt),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
        drawCircle(MMWhite, radius = size.minDimension * .30f)
    }
}

private fun parseIncomeColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    MMGreenIncome
}