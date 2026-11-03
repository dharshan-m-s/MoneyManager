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
import androidx.compose.material.IconButton
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.moneymanager.app.ui.components.MmTopBarAction
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import com.moneymanager.app.ui.income.IncomeViewModel
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    val zone = ZoneId.of("Asia/Kolkata")
    val today = LocalDate.now(zone)
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var periodFilter by rememberSaveable { mutableStateOf(0) }
    val periodLabels = listOf("All time", "This month", "Last 30 days", "This year")
    val filteredRows = remember(rows, query, periodFilter, today) {
        rows.filter { txn ->
            val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone).toLocalDate()
            val inPeriod = when (periodFilter) {
                1 -> date.year == today.year && date.month == today.month
                2 -> !date.isBefore(today.minusDays(29))
                3 -> date.year == today.year
                else -> true
            }
            val haystack = listOfNotNull(
                txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType
            ).joinToString(" ")
            inPeriod && (query.isBlank() || haystack.contains(query.trim(), ignoreCase = true))
        }
    }
    val total = filteredRows.sumOf { it.creditMinorUnits - it.debitMinorUnits }
    val scrollChrome = rememberScrollChromeState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    Column(Modifier.fillMaxSize().mmPageBackdrop()) {
        MoneyManagerTopBar(
            title = "Income",
            subtitle = "${filteredRows.size} transactions · ${periodLabels[periodFilter]}",
            onBack = onBack,
            modifier = Modifier.overlayWithoutLayoutSpace().onSizeChanged { headerHeightPx = it.height },
            actions = {
                MmTopBarAction(Icons.Filled.Search, "Search income", { searchOpen = !searchOpen; if (!searchOpen) query = "" })
                Box {
                    MmTopBarAction(Icons.Filled.FilterList, "Filter income", { filterMenuOpen = true })
                    DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                        periodLabels.forEachIndexed { index, label ->
                            DropdownMenuItem(onClick = { periodFilter = index; filterMenuOpen = false }) {
                                Text(label, color = if (periodFilter == index) MmColors.accent else MmColors.textPrimary)
                            }
                        }
                    }
                }
            },
            bottomContent = {
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs)
                    ) {
                        Box(Modifier.weight(1f)) {
                            MmSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = "Search income",
                                glassField = true
                            )
                        }
                        IconButton(onClick = { searchOpen = false; query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close income search",
                                tint = MmColors.textSecondary
                            )
                        }
                    }
                }
            }
        )
        if (filteredRows.isEmpty()) {
            MmEmptyState(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = if (rows.isEmpty()) "No income recorded yet" else "No matching income",
                message = if (rows.isEmpty()) "Income you add or import appears here, newest first."
                else "Try another search or choose a wider date range.",
                modifier = Modifier.fillMaxSize().padding(top = headerInset)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollChrome.connection),
                contentPadding = PaddingValues(
                    start = MmSpacing.lg,
                    end = MmSpacing.lg,
                    top = headerInset + MmSpacing.sm,
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
                        transactionCount = filteredRows.size,
                        compactProgress = scrollChrome.progress,
                        modifier = Modifier.fillMaxWidth().padding(bottom = MmSpacing.xs)
                    )
                }
                itemsIndexed(filteredRows, key = { _, txn -> txn.id }) { index, txn ->
                    MmTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) }, modifier = Modifier.revealOnAppear(index = index))
                }
            }
        }
    }

}

@Composable
private fun IncomeSummaryCard(
    total: Long,
    transactionCount: Int,
    compactProgress: androidx.compose.runtime.State<Float>,
    modifier: Modifier = Modifier
) {
    MmBalancePanel(
        label = "Total income",
        value = MoneyFormat.rupeesNoDecimals(Money(total)),
        caption = "$transactionCount recorded ${if (transactionCount == 1) "entry" else "entries"}",
        modifier = modifier,
        tint = MmColors.income,
        artworkKind = "income",
        glass = true,
        compactProgress = compactProgress
    )
}
