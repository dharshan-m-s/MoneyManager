package com.moneymanager.app.ui.modern.screens.reimbursements

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmBalancePanel
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmTransactionRow
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.LocalMmGlassBackdrop
import com.moneymanager.app.ui.components.LocalMmGlassEnabled
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.layerBackdrop
import com.moneymanager.app.ui.components.rememberVisiblePageGlassBackdrop
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import com.moneymanager.app.ui.components.scrollCompactField
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.revealOnAppear
import androidx.compose.foundation.lazy.itemsIndexed
import com.moneymanager.app.ui.reimbursements.ReimbursementsViewModel

/**
 * Money you are owed back. Each pending item keeps the canonical transaction row (so it still
 * opens the one transaction detail screen) and adds a single clear action underneath.
 */
@Composable
fun ReimbursementsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit = {},
    viewModel: ReimbursementsViewModel = hiltViewModel()
) {
    val pending by viewModel.pending.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val scrollChrome = rememberScrollChromeState(collapseDistance = 112.dp)
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    val visiblePending = remember(pending, query) {
        if (query.isBlank()) pending else pending.filter { txn ->
            listOfNotNull(txn.merchantReceiverSender, txn.rawCategoryName, txn.notes)
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    val owedTotal = pending.sumOf { it.debitMinorUnits - it.creditMinorUnits }
    var summaryHeightPx by remember { mutableIntStateOf(0) }
    val summaryHeight = with(LocalDensity.current) {
        headerInset + MmSpacing.sm +
            (if (summaryHeightPx > 0) summaryHeightPx.toDp() else 188.dp) + MmSpacing.sm
    }
    val pageGlassBackdrop = key(LocalMmGlassBackdrop.current) { rememberVisiblePageGlassBackdrop() }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageGlassBackdrop)) {
            Box(Modifier.fillMaxSize().background(MmColors.background).mmPageBackdrop())
            if (pending.isEmpty()) {
                MmEmptyState(
                    icon = Icons.Filled.SwapHoriz,
                    title = "Nothing owed to you",
                    message = "Mark a transaction as reimbursable when you add it and it will be " +
                        "tracked here until it is paid back.",
                    modifier = Modifier.fillMaxSize().padding(top = headerInset)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollChrome.connection),
                    contentPadding = PaddingValues(
                        start = MmSpacing.lg,
                        end = MmSpacing.lg,
                        top = summaryHeight,
                        bottom = MmSpacing.xxl + LocalDockContentPadding.current
                    ),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                ) {
                    itemsIndexed(visiblePending, key = { _, txn -> txn.id }) { index, txn ->
                        MmCard(
                            modifier = Modifier.revealOnAppear(index = index),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            MmTransactionRow(
                                txn = txn,
                                onClick = { onTransactionClick(txn.id) },
                                showDivider = false
                            )
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = MmSpacing.lg, end = MmSpacing.lg, bottom = MmSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MmPrimaryButton(
                                    text = "Mark reimbursed",
                                    onClick = { viewModel.markReimbursed(txn.id) },
                                    modifier = Modifier.height(40.dp)
                                )
                                Spacer(Modifier.width(MmSpacing.md))
                                Text(
                                    "Only marks this transaction — nothing else changes.",
                                    style = MmType.caption,
                                    color = MmColors.textSecondary,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        key(pageGlassBackdrop) {
            CompositionLocalProvider(
                LocalMmGlassBackdrop provides pageGlassBackdrop,
                LocalMmGlassEnabled provides true
            ) {
                MoneyManagerTopBar(
                    title = "Reimbursements",
                    subtitle = "${pending.size} pending",
                    onBack = onBack,
                    collapseProgress = scrollChrome.progress,
                    keepTitleOnCollapse = true,
                    modifier = Modifier.zIndex(3f).overlayWithoutLayoutSpace()
                        .onSizeChanged { headerHeightPx = it.height },
                    bottomContent = {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MmSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = "Search reimbursements",
                                glassField = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
                if (pending.isNotEmpty()) {
                    MmBalancePanel(
                        label = "AMOUNT TO COLLECT",
                        value = MoneyFormat.rupeesNoDecimals(Money(owedTotal)),
                        caption = "Tap a transaction to review it, then mark it reimbursed when the money returns.",
                        tint = MmColors.warning,
                        artworkKind = "income",
                        glass = true,
                        compactProgress = scrollChrome.progress,
                        modifier = Modifier.zIndex(2f)
                            .padding(top = headerInset + MmSpacing.sm, start = MmSpacing.lg, end = MmSpacing.lg)
                            .fillMaxWidth()
                            .onSizeChanged { summaryHeightPx = it.height }
                    )
                }
            }
        }
    }
}
