package com.moneymanager.app.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite

@Composable
fun BudgetScreen(
    onClose: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text("Monthly budget", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(end = 24.dp))
        }

        Text("Your spend trend", fontSize = 13.sp, color = MMGrayText, modifier = Modifier.padding(top = 8.dp))
        Text("Budget status updates live as transactions are added or edited.", fontSize = 11.sp, color = MMGrayText, modifier = Modifier.padding(top = 2.dp))

        SpendTrendChart(
            trend = state.trend,
            budgetMinorUnits = state.currentBudgetMinorUnits,
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)
        )

        Spacer(Modifier.padding(top = 16.dp))
        Text("My monthly budget", fontSize = 13.sp, color = MMGrayText)
        OutlinedTextField(
            value = state.budgetInputText,
            onValueChange = viewModel::onBudgetInputChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp)
        )

        Text(
            "We'll calculate your 'Safe to Spend' from the amount you enter above by " +
                "deducting your spend and upcoming bills. That way you know how much you can " +
                "spend and still stay in budget.",
            fontSize = 12.sp,
            color = MMGrayText,
            modifier = Modifier.padding(top = 12.dp)
        )

        Spacer(Modifier.padding(top = 16.dp))
        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = MMGreen)
        ) {
            Text("SET NOW", color = MMWhite)
        }

        Text(
            "* Set budget to zero to disable",
            fontSize = 11.sp,
            color = MMGrayText,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SpendTrendChart(trend: List<MonthSpend>, budgetMinorUnits: Long, modifier: Modifier = Modifier) {
    if (trend.isEmpty()) return
    val maxVal = maxOf(trend.maxOf { it.amount }, budgetMinorUnits, 1L)

    Column(modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val stepX = size.width / (trend.size - 1).coerceAtLeast(1)
            fun yFor(v: Long) = size.height - (v.toFloat() / maxVal) * size.height

            if (budgetMinorUnits > 0) {
                val y = yFor(budgetMinorUnits)
                drawLine(
                    color = MMRedExpense,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }

            val points = trend.mapIndexed { i, m -> Offset(i * stepX, yFor(m.amount)) }
            for (i in 0 until points.size - 1) {
                drawLine(color = MMGreen, start = points[i], end = points[i + 1], strokeWidth = 5f)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            trend.forEach { Text(it.label, fontSize = 10.sp, color = MMGrayText) }
        }
        if (budgetMinorUnits > 0) {
            Text(
                MoneyFormat.rupeesNoDecimals(Money(budgetMinorUnits)),
                fontSize = 11.sp,
                color = MMRedExpense,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
