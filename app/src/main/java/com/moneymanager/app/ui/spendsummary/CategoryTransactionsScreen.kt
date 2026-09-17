package com.moneymanager.app.ui.spendsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

/**
 * Every transaction ever recorded in one category, always newest first. Uses the same canonical
 * transaction row as the rest of the app, so tapping anything here opens the one shared
 * transaction detail screen.
 */
@Composable
fun CategoryTransactionsScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: CategoryTransactionsViewModel = hiltViewModel()
) {
    val rowsFlow = remember(categoryId) { viewModel.transactions(categoryId) }
    val rows by rowsFlow.collectAsState()
    var title by remember { mutableStateOf("Category") }
    LaunchedEffect(categoryId) { title = viewModel.categoryName(categoryId) }

    val spent = rows.sumOf { it.debitMinorUnits - it.creditMinorUnits }

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = "${rows.size} ${if (rows.size == 1) "transaction" else "transactions"} • all history",
            onBack = onBack
        )

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
                MmCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Net spend in this category", style = MmType.caption, color = MmColors.textSecondary)
                            Text(
                                MoneyFormat.rupeesNoDecimals(Money(spent)),
                                style = MmType.amountLarge,
                                color = MmColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MmPill("All time", tint = MmColors.accent, filled = true)
                    }
                }
                Spacer(Modifier.height(MmSpacing.sm))
            }

            items(rows, key = { it.id }) { txn ->
                MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
            }
        }
    }
}
