package com.moneymanager.app.ui.modern.screens.income

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmAnimatedAmount
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MmSpotlightPanel
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.income.IncomeViewModel
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    val total = rows.sumOf { it.creditMinorUnits - it.debitMinorUnits }
    val scrollChrome = rememberScrollChromeState()

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = "Income",
            subtitle = "${rows.size} transactions",
            onBack = onBack,
            collapseProgress = scrollChrome.progress
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
                modifier = Modifier.fillMaxSize().nestedScroll(scrollChrome.connection),
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    top = MmSpacing.sm,
                    bottom = MmSpacing.xxl + LocalDockContentPadding.current
                ),
                verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
            ) {
                item(key = "income-intro") {
                    MmSpotlightPanel(
                        eyebrow = "CASH FLOW",
                        title = "Money coming in",
                        description = "Every recorded credit, collected in one place and ordered newest first.",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        tint = MmColors.income
                    )
                }
                stickyHeader(key = "income-summary") {
                    IncomeSummaryCard(
                        total = total,
                        transactionCount = rows.size,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MmColors.background)
                            .padding(bottom = MmSpacing.xs)
                    )
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

@Composable
private fun IncomeSummaryCard(
    total: Long,
    transactionCount: Int,
    modifier: Modifier = Modifier
) {
    MmBalancePanel(
        label = "Total income",
        value = MoneyFormat.rupeesNoDecimals(Money(total)),
        caption = "$transactionCount recorded ${if (transactionCount == 1) "entry" else "entries"}",
        modifier = modifier,
        tint = MmColors.income,
        artworkKind = "income"
    )
}
