package com.moneymanager.app.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmLegendDot
import com.moneymanager.app.ui.components.MmProgressBar
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import androidx.compose.ui.unit.sp

@Composable
fun BudgetScreen(
    onClose: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    val spendThisMonth = state.trend.lastOrNull()?.amount ?: 0L
    val budget = state.currentBudgetMinorUnits
    val remaining = budget - spendThisMonth
    val progress = if (budget > 0L) (spendThisMonth.toFloat() / budget.toFloat()) else 0f
    val statusColor = when {
        budget <= 0L -> MmColors.accent
        progress < 0.75f -> MmColors.income
        progress < 1f -> MmColors.warning
        else -> MmColors.expense
    }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(title = "Monthly budget", onBack = onClose)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(MmSpacing.lg)
                .padding(bottom = MmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
        ) {
            MmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmIconBadge(icon = Icons.Filled.MonetizationOn, tint = statusColor, size = 44.dp)
                    Spacer(Modifier.padding(start = MmSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (budget > 0L) "This month" else "No budget set",
                            style = MmType.label,
                            color = MmColors.textSecondary
                        )
                        Text(
                            if (budget > 0L) MoneyFormat.rupeesNoDecimals(Money(budget)) else "Set a limit below",
                            style = MmType.amountLarge,
                            color = MmColors.textPrimary
                        )
                    }
                }
                if (budget > 0L) {
                    Spacer(Modifier.height(MmSpacing.md))
                    MmProgressBar(fraction = progress, color = statusColor)
                    Spacer(Modifier.height(MmSpacing.sm))
                    Text(
                        when {
                            progress < 0.75f -> "You are on track this month."
                            progress < 1f -> "You are nearing your budget."
                            progress < 1.01f -> "You have reached your budget."
                            else -> "You are over budget."
                        },
                        style = MmType.label,
                        color = statusColor
                    )
                }
                Spacer(Modifier.height(MmSpacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)) {
                    BudgetStat("Spent", Money(spendThisMonth), MmColors.expense, Modifier.weight(1f))
                    BudgetStat(
                        if (remaining >= 0L) "Remaining" else "Over by",
                        Money(if (remaining >= 0L) remaining else -remaining),
                        statusColor,
                        Modifier.weight(1f)
                    )
                }
            }

            MmCard {
                Text("Spend trend", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.sm))
                if (state.trend.isEmpty()) {
                    MmEmptyState(
                        icon = Icons.Filled.MonetizationOn,
                        title = "No spend history yet",
                        message = "Your last six months of spending will appear here."
                    )
                } else {
                    SpendTrendChart(
                        trend = state.trend,
                        budgetMinorUnits = budget,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Spacer(Modifier.height(MmSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MmLegendDot(MmColors.accent)
                        Spacer(Modifier.padding(start = 6.dp))
                        Text("Monthly spend", style = MmType.caption, color = MmColors.textSecondary)
                        if (budget > 0L) {
                            Spacer(Modifier.padding(start = MmSpacing.md))
                            MmLegendDot(MmColors.expense)
                            Spacer(Modifier.padding(start = 6.dp))
                            Text("Budget", style = MmType.caption, color = MmColors.textSecondary)
                        }
                    }
                }
            }

            MmCard {
                Text("Set your budget", style = MmType.label, color = MmColors.textSecondary)
                Spacer(Modifier.height(MmSpacing.md))
                MmAmountField(
                    value = state.budgetInputText,
                    onValueChange = viewModel::onBudgetInputChange,
                    label = "Monthly budget",
                    supportingText = "Set to zero to turn the budget off."
                )
                Spacer(Modifier.height(MmSpacing.md))
                Text(
                    "\"Safe to spend\" is calculated by deducting this month's spend and upcoming " +
                        "bills from your budget, so you always know what is genuinely left.",
                    style = MmType.caption,
                    color = MmColors.textSecondary
                )
                Spacer(Modifier.height(MmSpacing.lg))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                ) {
                    Text("Save budget", color = MmColors.onAccent, style = MmType.label)
                }
            }
        }
    }
}

@Composable
private fun BudgetStat(label: String, amount: Money, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MmColors.surfaceMuted, RoundedCornerShape(MmSpacing.radiusRow))
            .padding(MmSpacing.md)
    ) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary)
        Spacer(Modifier.height(3.dp))
        Text(
            MoneyFormat.rupeesNoDecimals(amount),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun SpendTrendChart(trend: List<MonthSpend>, budgetMinorUnits: Long, modifier: Modifier = Modifier) {
    val maxVal = maxOf(trend.maxOf { it.amount }, budgetMinorUnits, 1L)
    val lineColor = MmColors.accent
    val budgetColor = MmColors.expense
    val gridColor = MmColors.divider

    Column(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            fun yFor(value: Long) = size.height - (value.toFloat() / maxVal) * size.height
            if (budgetMinorUnits > 0L) {
                val y = yFor(budgetMinorUnits)
                drawLine(
                    color = budgetColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }
            val stepX = size.width / (trend.size - 1).coerceAtLeast(1)
            val points = trend.mapIndexed { index, month -> Offset(index * stepX, yFor(month.amount)) }
            for (index in 0 until points.size - 1) {
                drawLine(color = lineColor, start = points[index], end = points[index + 1], strokeWidth = 5f)
            }
            points.forEach { point ->
                drawCircle(color = lineColor, radius = 7f, center = point)
                drawCircle(color = Color.White, radius = 3f, center = point)
            }
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 2f
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            trend.forEach { Text(it.label, fontSize = 10.sp, color = MmColors.textSecondary) }
        }
    }
}
