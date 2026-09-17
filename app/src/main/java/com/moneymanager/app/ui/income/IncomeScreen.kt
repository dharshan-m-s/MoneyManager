package com.moneymanager.app.ui.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing

@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    val total = rows.sumOf { it.creditMinorUnits - it.debitMinorUnits }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Income",
            subtitle = "${rows.size} transactions • ${MoneyFormat.rupeesNoDecimals(Money(total))}",
            onBack = onBack
        )
        if (rows.isEmpty()) {
            MmEmptyState(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "No income recorded yet",
                message = "Income you add or import appears here, newest first."
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = MmSpacing.xxl)) {
                items(rows, key = { it.id }) { txn ->
                    MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                }
            }
        }
    }
}
