package com.moneymanager.app.ui.spendsummary

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MmSectionHeader
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.util.Locale

/**
 * Spend areas for the current month. The donut answers "where did the money go" at a glance,
 * the list underneath answers "how much exactly", and every row is a real drill-down into the
 * matching transaction list.
 */
@Composable
fun SpendSummaryScreen(
    onBack: () -> Unit,
    onCategoryClick: (Long) -> Unit = {},
    viewModel: SpendSummaryViewModel = hiltViewModel()
) {
    val rows by viewModel.categoryRows.collectAsState()
    val total = rows.sumOf { it.total }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Spend areas",
            subtitle = "This month",
            onBack = onBack
        )

        if (rows.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.PieChart,
                title = "Nothing spent yet this month",
                message = "Once you record a spend it will be grouped by category here.",
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
                            Text(
                                "${rows.size} ${if (rows.size == 1) "category" else "categories"}",
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
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

@Composable
private fun SpendDonutChart(rows: List<SpendCategoryRow>, total: Long) {
    val holeColor = MmColors.surface
    Canvas(modifier = Modifier.size(132.dp)) {
        var startAngle = -90f
        rows.forEach { row ->
            val sweep = if (total > 0) 360f * row.total / total else 0f
            val visibleSweep = (sweep - 1.5f).coerceAtLeast(0f)
            drawArc(
                parseColor(row.colorHex), startAngle + 0.75f, visibleSweep, false,
                style = Stroke(width = 26f, cap = StrokeCap.Butt),
                size = Size(size.width, size.height)
            )
            startAngle += sweep
        }
        // The centre hole uses the current surface colour so it reads correctly in dark mode.
        drawCircle(holeColor, radius = size.minDimension * .31f)
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    MMGreen
}
