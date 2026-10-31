package com.moneymanager.app.ui.modern.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed
import com.moneymanager.app.ui.income.IncomeViewModel

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
            subtitle = "${rows.size} transactions",
            onBack = onBack
        )
        if (rows.isEmpty()) {
            MmEmptyState(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "No income recorded yet",
                message = "Income you add or import appears here, newest first.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    bottom = MmSpacing.xxl
                )
            ) {
                item {
                    MmCard(
                        modifier = Modifier.padding(vertical = MmSpacing.sm),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(MmSpacing.card)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Income total", style = MmType.caption, color = MmColors.textSecondary)
                                Text(
                                    MoneyFormat.rupeesNoDecimals(Money(total)),
                                    style = MmType.amountLarge,
                                    color = MmColors.income,
                                    maxLines = 1
                                )
                            }
                        }
                    }
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
}
