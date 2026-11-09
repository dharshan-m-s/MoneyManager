package com.moneymanager.app.ui.modern.screens.bills

import com.moneymanager.app.ui.theme.MmColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.AlertDialog
import androidx.compose.material.RadioButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.billing.BillPaymentWindow
import com.moneymanager.app.domain.model.Money
 import com.moneymanager.app.ui.components.MmAmountField
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmDateField
import com.moneymanager.app.ui.components.MmOption
import com.moneymanager.app.ui.components.MmPickerSheet
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmPrimaryButton
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmPill
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.components.pressScale
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.mmPageBackdrop
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.LocalMmGlassEnabled
import com.moneymanager.app.ui.components.mmLiquidGlass
import com.moneymanager.app.ui.components.rememberScrollChromeState
import com.moneymanager.app.ui.components.LocalDockContentPadding
import com.moneymanager.app.ui.components.overlayWithoutLayoutSpace
import com.moneymanager.app.ui.components.LocalMmGlassBackdrop
import com.moneymanager.app.ui.components.rememberVisiblePageGlassBackdrop
import com.moneymanager.app.ui.components.glass.backdrop.backdrops.layerBackdrop
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.moneymanager.app.ui.components.MmSecondaryButton
import com.moneymanager.app.ui.bills.BillPaymentCandidate
import com.moneymanager.app.ui.bills.BillPaymentSearchState
import com.moneymanager.app.ui.bills.BillRow
import com.moneymanager.app.ui.bills.BillsViewModel

@Composable
fun BillsScreen(
    onBack: () -> Unit,
    onAddBiller: () -> Unit = {},
    onOpenBillHistory: (Long, String) -> Unit = { _, _ -> },
    onEditBiller: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    viewModel: BillsViewModel = hiltViewModel()
) {
    val bills by viewModel.bills.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val scrollChrome = rememberScrollChromeState(collapseDistance = 112.dp)
    val pageGlassBackdrop = androidx.compose.runtime.key(LocalMmGlassBackdrop.current) { rememberVisiblePageGlassBackdrop() }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerInset = with(LocalDensity.current) { headerHeightPx.toDp() }
    val visibleBills = remember(bills, query) {
        if (query.isBlank()) bills else bills.filter { row ->
            listOf(row.bill.billerName, row.bill.nickname, row.bill.accountReferenceId)
                .filterNotNull()
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
    }
    val paymentSearch by viewModel.paymentSearch.collectAsState()
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(paymentSearch.message) {
        paymentSearch.message?.let {
            snackbarState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        // The shared top bar consumes the safe top inset and the floating dock overlays the
        // bottom edge. Disable Scaffold's automatic system insets to avoid a second top gap and
        // an empty bottom strip, but still apply the returned content padding contract.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("screen.bills")) {
          Box(Modifier.fillMaxSize().layerBackdrop(pageGlassBackdrop)) {
          CompositionLocalProvider(
              LocalMmGlassBackdrop provides pageGlassBackdrop,
              LocalMmGlassEnabled provides true
          ) {
          Column(Modifier.fillMaxSize().mmPageBackdrop()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (bills.isEmpty() || visibleBills.isEmpty()) {
                MmEmptyState(
                    icon = Icons.Filled.Payments,
                    title = if (bills.isEmpty()) "No bills yet" else "No matching bills",
                    message = if (bills.isEmpty()) "Add the bills you pay regularly - rent, maid, EMI, utilities - and track " +
                        "them here. Recurring payments found in imported statements appear automatically."
                    else "Try another biller name or clear your search.",
                    actionLabel = if (bills.isEmpty()) "Add a bill" else "Clear search",
                    onAction = if (bills.isEmpty()) onAddBiller else ({ query = "" }),
                    modifier = Modifier.fillMaxSize().padding(top = headerInset)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollChrome.connection),
                    contentPadding = PaddingValues(start = MmSpacing.lg, end = MmSpacing.lg, top = headerInset + MmSpacing.sm, bottom = maxOf(36.dp, LocalDockContentPadding.current)),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                ) {
                    itemsIndexed(visibleBills, key = { _, row -> row.bill.id }) { index, row ->
                        BillCard(
                            row = row,
                            modifier = Modifier.revealOnAppear(index = index),
                            onClick = { onOpenBillHistory(row.bill.id, row.bill.billerName) },
                            onEdit = { onEditBiller(row.bill.id) },
                            onMarkPaid = {
                                row.nextInstance?.let { instance ->
                                    if (instance.amountDueMinorUnits == 0L && instance.paidAmountMinorUnits == 0L) {
                                        viewModel.markAsPaidDirect(instance, 0L)
                                    } else {
                                        viewModel.findPaymentTransactions(row)
                                    }
                                }
                            },
                            onDelete = { viewModel.deleteBill(row.bill.id) }
                        )
                    }
                }
            }
            }
          }
          }
          }
          CompositionLocalProvider(
              LocalMmGlassBackdrop provides pageGlassBackdrop,
              LocalMmGlassEnabled provides true
          ) {
                MoneyManagerTopBar(
                    title = "Bills & EMIs",
                    subtitle = "Track upcoming payments without duplicate transactions",
                    onBack = onBack,
                    collapseProgress = scrollChrome.progress,
                    keepTitleOnCollapse = true,
                    hideNavigationOnCollapse = true,
                    modifier = Modifier.fillMaxWidth().zIndex(3f).onSizeChanged { headerHeightPx = it.height },
                    actions = { BillAddAction(onAdd = onAddBiller) },
                    bottomContent = {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MmSpacing.screen, vertical = MmSpacing.xs)
                                .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MmSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = "Search bills",
                                glassField = true
                            )
                        }
                    }
                )
          }
        }
    }

    val pendingRow = paymentSearch.billRow
    if (pendingRow != null) {
        BillPaymentDialog(
            billName = pendingRow.bill.billerName,
            amount = Money(pendingRow.nextInstance?.amountDueMinorUnits ?: pendingRow.bill.estimatedAmountMinorUnits),
            isCreditCard = pendingRow.bill.billerType == com.moneymanager.app.data.local.entity.BillerType.CREDIT_CARD ||
                pendingRow.bill.linkedAccountId != null,
            state = paymentSearch,
            onSelectTransaction = viewModel::selectPaymentTransaction,
            onConfirm = { adjustmentMinorUnits ->
                val transactionId = paymentSearch.selectedTransactionId
                val instance = pendingRow.nextInstance
                if (instance != null && transactionId != null && !paymentSearch.loading && !paymentSearch.saving) {
                    viewModel.markAsPaid(instance, transactionId, settlementAdjustmentMinorUnits = adjustmentMinorUnits)
                }
            },
            onRecordSource = viewModel::setRecordSourceAccount,
            onRecordAmount = viewModel::setRecordAmount,
            onRecordDate = viewModel::setRecordDate,
            onRecordNotes = viewModel::setRecordNotes,
            onRecordPayment = viewModel::recordPayment,
            onSearchOlder = { viewModel.searchOlderTransactions() },
            onDismiss = viewModel::clearPaymentSearch
        )
    }
}

@Composable
private fun BillPaymentDialog(
    billName: String,
    amount: Money,
    isCreditCard: Boolean,
    state: BillPaymentSearchState,
    onSelectTransaction: (Long?) -> Unit,
    onConfirm: (Long) -> Unit,
    onRecordSource: (Long) -> Unit,
    onRecordAmount: (String) -> Unit,
    onRecordDate: (Long) -> Unit,
    onRecordNotes: (String) -> Unit,
    onRecordPayment: (Long) -> Unit,
    onSearchOlder: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showRecent by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var recentQuery by remember(state.billRow?.bill?.id) { mutableStateOf("") }
    var showRecordPayment by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var initialPaymentModeChosen by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var pendingSettlement by remember(state.billRow?.bill?.id) { mutableStateOf<PendingBillSettlement?>(null) }
    val selectedSource = state.accounts.firstOrNull { it.id == state.recordSourceAccountId }
    val occurrence = state.billRow?.nextInstance
    val formScroll = rememberScrollState()
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val zone = ZoneId.of("Asia/Kolkata")
    val totalDue = occurrence?.amountDueMinorUnits ?: amount.minorUnits
    val paidSoFar = occurrence?.paidAmountMinorUnits ?: 0L
    val remainingDue = (totalDue - paidSoFar).coerceAtLeast(0L)

    LaunchedEffect(state.loading, state.matches.isEmpty(), state.recentTransactions.isEmpty()) {
        if (!state.loading && !initialPaymentModeChosen) {
            when {
                state.matches.isNotEmpty() -> Unit
                state.recentTransactions.isNotEmpty() -> showRecent = true
                else -> showRecordPayment = true
            }
            initialPaymentModeChosen = true
        }
    }

    val recentRows: List<BillPaymentCandidate> = remember(state.recentTransactions, recentQuery) {
        val tokens = recentQuery.trim().lowercase().split(Regex("\\s+"))
            .map(String::trim).filter { it.length >= 2 }
        if (tokens.isEmpty()) state.recentTransactions else state.recentTransactions.mapNotNull { candidate ->
            val tx = candidate.transaction
            val haystack = listOfNotNull(
                tx.merchantReceiverSender, tx.notes, tx.rawCategoryName, tx.rawPaymentType,
                tx.txnType.name, tx.txnSubType.name, candidate.account?.nickname,
                candidate.account?.institutionName, candidate.account?.sourceAccountId,
                MoneyFormat.rupeesNoDecimals(Money(tx.debitMinorUnits)),
                MoneyFormat.rupeesNoDecimals(Money(tx.debitMinorUnits)).replace(Regex("[^0-9.]"), ""),
                dateFormat.format(Instant.ofEpochMilli(tx.occurredAtEpochMillis).atZone(zone))
            ).joinToString(" ").lowercase()
            val score = tokens.count(haystack::contains)
            if (score > 0) candidate to score else null
        }.sortedWith(
            compareByDescending<Pair<BillPaymentCandidate, Int>> { it.second }
                .thenByDescending { it.first.transaction.occurredAtEpochMillis }
        ).map { it.first }
    }

    val selectedTransaction = (state.matches + state.recentTransactions)
        .firstOrNull { it.transaction.id == state.selectedTransactionId }?.transaction

    fun requestSettlement(actualMinor: Long, confirm: (Long) -> Unit) {
        val difference = remainingDue - actualMinor
        if (actualMinor > 0L && difference > 0L) {
            pendingSettlement = PendingBillSettlement(
                billAmountMinorUnits = totalDue,
                actualPaymentMinorUnits = actualMinor,
                differenceMinorUnits = difference,
                keepPending = { confirm(0L) },
                settleBill = { confirm(difference) }
            )
        } else confirm(0L)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.82f),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(0.8.dp, MmColors.outline.copy(alpha = 0.45f)),
            elevation = 20.dp,
            color = MmColors.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                // Compact, fixed header: the bill and balance remain visible while the list scrolls.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SETTLE BILL", style = MmType.caption, color = MmColors.accent, fontWeight = FontWeight.Bold)
                            Text(billName, style = MmType.sectionTitle, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = MmColors.textSecondary)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(MmColors.accent.copy(alpha = 0.17f), MmColors.surfaceContainerHigh, MmColors.surfaceContainer)))
                            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("REMAINING", style = MmType.caption, color = MmColors.textSecondary, fontWeight = FontWeight.SemiBold)
                            Text(MoneyFormat.rupeesNoDecimals(Money(remainingDue)), style = MmType.amountTile, color = MmColors.textPrimary)
                            Text(
                                if (paidSoFar > 0L) "${MoneyFormat.rupeesNoDecimals(Money(paidSoFar))} already paid"
                                else "Total bill ${MoneyFormat.rupeesNoDecimals(Money(totalDue))}",
                                style = MmType.caption,
                                color = MmColors.textSecondary
                            )
                        }
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MmColors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isCreditCard) Icons.Filled.CreditCard else Icons.Filled.Payments,
                                contentDescription = null,
                                tint = MmColors.accent,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }

                    PaymentModeSelector(
                        recordPayment = showRecordPayment,
                        onSelect = { showRecordPayment = it }
                    )
                }

                if (showRecordPayment) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(formScroll)
                            .padding(horizontal = MmSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MmSpacing.md)
                    ) {
                        Text("New payment", style = MmType.label, color = MmColors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isCreditCard) "A transfer is recorded from the account you choose."
                            else "Only the amount actually paid is recorded as an expense.",
                            style = MmType.caption,
                            color = MmColors.textSecondary
                        )
                        MmCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAccountPicker = true },
                            backgroundColor = MmColors.surfaceContainer
                        ) {
                            Text("PAID FROM", style = MmType.caption, color = MmColors.textSecondary, fontWeight = FontWeight.SemiBold)
                            Text(
                                selectedSource?.nickname?.ifBlank { selectedSource.institutionName } ?: "Choose an account",
                                style = MmType.body,
                                color = if (selectedSource != null) MmColors.textPrimary else MmColors.accent
                            )
                        }
                        MmAmountField(value = state.recordAmount, onValueChange = onRecordAmount, label = "Amount paid")
                        MmDateField(
                            label = "Payment date",
                            epochMillis = state.recordDateEpochMillis,
                            onChange = onRecordDate,
                            includeTime = true
                        )
                        OutlinedTextField(
                            value = state.recordNotes,
                            onValueChange = onRecordNotes,
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                        ) {
                            PaymentListTab("Best matches", selected = !showRecent, modifier = Modifier.weight(1f)) { showRecent = false }
                            PaymentListTab("Recent", selected = showRecent, modifier = Modifier.weight(1f)) { showRecent = true }
                        }

                        if (showRecent) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs)) {
                                MmSearchField(value = recentQuery, onValueChange = { recentQuery = it }, hint = "Search name, amount or date")
                            }
                        }

                        val rows = if (showRecent) recentRows else state.matches
                        when {
                            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Finding payments…", style = MmType.caption, color = MmColors.textSecondary)
                            }
                            rows.isEmpty() -> Column(
                                Modifier.weight(1f).fillMaxWidth().padding(horizontal = MmSpacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(MmColors.surfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = MmColors.textSecondary)
                                }
                                Spacer(Modifier.height(MmSpacing.md))
                                Text(
                                    if (showRecent) "No transactions found" else "No close matches yet",
                                    style = MmType.label,
                                    color = MmColors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (showRecent) "Try another search, or record a new payment."
                                    else "Check Recent for another payment or enter one manually.",
                                    style = MmType.caption,
                                    color = MmColors.textSecondary
                                )
                                TextButton(onClick = onSearchOlder) {
                                    Text("Search older transactions", color = MmColors.accent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = MmSpacing.md, vertical = MmSpacing.xs)
                            ) {
                                items(rows, key = { it.transaction.id }) { candidate ->
                                    BillPaymentCandidateRow(candidate, state.selectedTransactionId, dateFormat, zone, onSelectTransaction)
                                }
                            }
                        }
                    }
                }

                state.error?.let { error ->
                    Text(error, style = MmType.caption, color = MmColors.expense, modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs))
                }

                Column(
                    Modifier.fillMaxWidth().background(MmColors.surfaceContainer)
                        .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MmSpacing.xs)
                ) {
                    if (showRecordPayment) {
                        MmPrimaryButton(
                            text = if (state.saving) "Recording payment…" else "Record payment",
                            onClick = {
                                val actual = runCatching {
                                    Money.fromRupees(BigDecimal(state.recordAmount.trim().ifBlank { "0" })).minorUnits
                                }.getOrNull() ?: 0L
                                requestSettlement(actual, onRecordPayment)
                            },
                            enabled = !state.saving && state.recordSourceAccountId != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                    } else {
                        val linkedOutcome = selectedTransaction?.let { txn ->
                            val diff = remainingDue - txn.debitMinorUnits
                            when {
                                diff > 0L -> "${MoneyFormat.rupeesNoDecimals(Money(txn.debitMinorUnits))} selected · ${MoneyFormat.rupeesNoDecimals(Money(diff))} will remain"
                                diff < 0L -> "${MoneyFormat.rupeesNoDecimals(Money(txn.debitMinorUnits))} selected · extra stays as a credit"
                                else -> "${MoneyFormat.rupeesNoDecimals(Money(txn.debitMinorUnits))} selected · exact settlement"
                            }
                        }
                        Text(
                            linkedOutcome ?: "Select a transaction you have already recorded.",
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        MmPrimaryButton(
                            text = if (state.saving) "Linking payment…" else "Link selected payment",
                            onClick = {
                                requestSettlement(selectedTransaction?.debitMinorUnits ?: 0L, onConfirm)
                            },
                            enabled = !state.loading && !state.saving && selectedTransaction != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAccountPicker) {
        MmPickerSheet(
            title = "Paid from",
            options = state.accounts.map { account ->
                MmOption(
                    id = account.id,
                    label = account.nickname.ifBlank { account.institutionName },
                    subtitle = account.institutionName.takeIf { it.isNotBlank() && it != account.nickname }
                )
            },
            selectedId = state.recordSourceAccountId,
            onSelect = { option ->
                onRecordSource(option.id)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    pendingSettlement?.let { pending ->
        Dialog(onDismissRequest = { pendingSettlement = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f),
                shape = RoundedCornerShape(28.dp),
                elevation = 20.dp,
                color = MmColors.surface
            ) {
                Column(Modifier.fillMaxWidth().padding(MmSpacing.lg), verticalArrangement = Arrangement.spacedBy(MmSpacing.md)) {
                    Text("PAYMENT DIFFERENCE", style = MmType.caption, color = MmColors.warning, fontWeight = FontWeight.Bold)
                    Text("Is there still an amount pending?", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MmColors.surfaceContainer).padding(MmSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MmSpacing.sm)
                    ) {
                        SettlementAmountRow("Original bill", pending.billAmountMinorUnits)
                        SettlementAmountRow("Actually paid", pending.actualPaymentMinorUnits)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MmColors.outline.copy(alpha = 0.35f)))
                        SettlementAmountRow("Difference", pending.differenceMinorUnits, MmColors.warning)
                    }
                    MmSecondaryButton(
                        text = "Amount still pending",
                        onClick = { pendingSettlement = null; pending.keepPending() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                    MmPrimaryButton(
                        text = "Bill fully settled",
                        onClick = { pendingSettlement = null; pending.settleBill() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                    TextButton(onClick = { pendingSettlement = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = MmColors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentModeSelector(recordPayment: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MmColors.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PaymentModeTab("Match a payment", selected = !recordPayment, modifier = Modifier.weight(1f)) { onSelect(false) }
        PaymentModeTab("New payment", selected = recordPayment, modifier = Modifier.weight(1f)) { onSelect(true) }
    }
}

@Composable
private fun PaymentModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) MmColors.surface else Color.Transparent)
            .pressableClick(onClick = onClick)
            .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MmType.caption, color = if (selected) MmColors.textPrimary else MmColors.textSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PaymentListTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) MmColors.accent.copy(alpha = 0.12f) else MmColors.surfaceContainer)
            .pressableClick(onClick = onClick)
            .padding(horizontal = MmSpacing.sm, vertical = MmSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MmType.caption, color = if (selected) MmColors.accent else MmColors.textSecondary, fontWeight = FontWeight.SemiBold)
    }
}

private data class PendingBillSettlement(
    val billAmountMinorUnits: Long,
    val actualPaymentMinorUnits: Long,
    val differenceMinorUnits: Long,
    val keepPending: () -> Unit,
    val settleBill: () -> Unit
)

@Composable
private fun SettlementAmountRow(
    label: String,
    amountMinorUnits: Long,
    tint: androidx.compose.ui.graphics.Color = MmColors.textPrimary
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MmType.caption, color = MmColors.textSecondary)
        Text(
            MoneyFormat.rupeesNoDecimals(Money(amountMinorUnits)),
            style = MmType.caption,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BillPaymentCandidateRow(
    candidate: BillPaymentCandidate,
    selectedTransactionId: Long?,
    dateFormat: DateTimeFormatter,
    zone: ZoneId,
    onSelectTransaction: (Long?) -> Unit
) {
    val tx = candidate.transaction
    val title = tx.merchantReceiverSender?.takeIf { it.isNotBlank() }
        ?: tx.notes?.takeIf { it.isNotBlank() }
        ?: tx.rawCategoryName?.takeIf { it.isNotBlank() }
        ?: "Unnamed payment"
    val dateText = dateFormat.format(Instant.ofEpochMilli(tx.occurredAtEpochMillis).atZone(zone))
    val accountText = candidate.account?.nickname?.ifBlank { candidate.account.institutionName }.orEmpty()

    MmCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { onSelectTransaction(tx.id) },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        backgroundColor = if (selectedTransactionId == tx.id) {
            MmColors.accent.copy(alpha = 0.08f)
        } else {
            MmColors.surface
        }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selectedTransactionId == tx.id, onClick = { onSelectTransaction(tx.id) })
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    listOf(dateText, accountText.ifBlank { null }).filterNotNull().joinToString(" • "),
                    fontSize = 11.sp,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(tx.debitMinorUnits)),
                    fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                    color = MmColors.expense,
                    maxLines = 1,
                    softWrap = false
                )
                if (selectedTransactionId == tx.id) {
                    Text("Selected", color = MmColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BillCard(
    row: BillRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDetectedPayment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember(row.bill.id) { mutableStateOf(false) }
    val interactionSource = remember(row.bill.id) { MutableInteractionSource() }
    val now = System.currentTimeMillis()
    val next = row.nextInstance
    val latestPaid = row.latestPaid
    val dueMillis = next?.dueDateEpochMillis ?: row.bill.nextDueDateEpochMillis
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
    val daysUntilDue = ChronoUnit.DAYS.between(
        today,
        Instant.ofEpochMilli(dueMillis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
    )
    val overdue = next != null && daysUntilDue < 0
    val paid = next == null && latestPaid != null
    val partialInstance = next?.takeIf {
        it.paidAmountMinorUnits in 1L until it.amountDueMinorUnits
    }
    val partial = partialInstance != null
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM")
    val zone = ZoneId.of("Asia/Kolkata")

    // "Mark as paid" only makes sense once the payment date is near: an occurrence that is
    // months away has nothing to settle yet. Overdue bills and partially paid ones stay
    // actionable.
    val payable = BillPaymentWindow.isPayableNow(row.bill, next, now)
    val detected = null

    val statusText: String
    val statusColor: Color
    if (paid) {
        statusText = "Paid"
        statusColor = MmColors.income
    } else if (partial) {
        statusText = "Partly paid"
        statusColor = if (overdue) MmColors.expense else MmColors.accent
    } else if (next != null) {
        statusText = if (overdue) "${-daysUntilDue} days overdue" else {
            val days = daysUntilDue
            if (days == 1L) "1 day to pay" else "$days days to pay"
        }
        statusColor = if (overdue) MmColors.expense else MmColors.warning
    } else {
        statusText = "No bill due"
        statusColor = MmColors.textSecondary
    }

    val dueDateStr = dateFormat.format(Instant.ofEpochMilli(dueMillis).atZone(zone))
    val displayDate = if (paid) {
        val paidMillis = latestPaid.paidAtEpochMillis ?: dueMillis
        "Paid on: " + dateFormat.format(Instant.ofEpochMilli(paidMillis).atZone(zone))
    } else if (partialInstance != null) {
        "Paid ${MoneyFormat.rupeesNoDecimals(Money(partialInstance.paidAmountMinorUnits))} of " +
            "${MoneyFormat.rupeesNoDecimals(Money(partialInstance.amountDueMinorUnits))} • Due $dueDateStr"
    } else {
        "Due on: $dueDateStr"
    }

    // What still has to be paid: the remaining part of the amount due after recorded payments.
    val amountRemainingMinor = next?.let { it.amountDueMinorUnits - it.paidAmountMinorUnits }
        ?: row.bill.estimatedAmountMinorUnits

    MmCard(
        modifier = modifier
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs)
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = { showDeleteConfirmation = true }
            )
            .then(
                if (overdue) Modifier.border(
                    width = 1.5.dp,
                    color = MmColors.expense.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(MmSpacing.radiusCard)
                ) else Modifier
            ),
        contentPadding = PaddingValues(MmSpacing.card)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.moneymanager.app.ui.components.FinanceArtwork(
                kind = "bill_${row.bill.billerType.name.lowercase()}",
                tint = when {
                    overdue -> MmColors.expense
                    paid -> MmColors.income
                    else -> MmColors.warning
                },
                modifier = Modifier.size(58.dp)
            )
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    row.bill.billerName,
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    row.bill.accountReferenceId?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(MmSpacing.xs))
                    }
                    Text(
                        row.bill.billerType.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { c -> c.uppercase() } +
                            " • " + com.moneymanager.app.domain.billing.BillSchedule.label(row.bill.billingCycle),
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (paid) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Paid",
                    tint = MmColors.income,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit bill", tint = MmColors.textSecondary)
            }
        }

        Spacer(Modifier.height(MmSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(amountRemainingMinor)),
                    style = MmType.amountRow,
                color = when {
                    overdue -> MmColors.expense
                    paid -> MmColors.income
                    else -> MmColors.textPrimary
                }
            )
                MmPill(statusText, tint = statusColor, filled = true)
                Text(displayDate, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open bill", tint = MmColors.textTertiary)
        }

        if (next != null && payable) {
            Spacer(Modifier.height(MmSpacing.md))
            MmPrimaryButton(
                text = "Mark as paid",
                onClick = onMarkPaid,
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${row.bill.billerName}?") },
            text = { Text("This removes the bill and its scheduled payment history. Transactions already recorded in your accounts will remain in your ledger.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; onDelete() }) {
                    Text("Delete", color = MmColors.expense)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BillAddAction(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(MmColors.accent.copy(alpha = 0.11f))
            .pressableClick(onClick = onAdd)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MmColors.accent, modifier = Modifier.size(16.dp))
        Text("Add bill", color = MmColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun billerIcon(type: com.moneymanager.app.data.local.entity.BillerType): androidx.compose.ui.graphics.vector.ImageVector =
    when (type) {
        com.moneymanager.app.data.local.entity.BillerType.ELECTRICITY -> Icons.Filled.Bolt
        com.moneymanager.app.data.local.entity.BillerType.GAS -> Icons.Filled.LocalFireDepartment
        com.moneymanager.app.data.local.entity.BillerType.WATER -> Icons.Filled.WaterDrop
        com.moneymanager.app.data.local.entity.BillerType.MOBILE -> Icons.Filled.Phone
        com.moneymanager.app.data.local.entity.BillerType.BROADBAND_WIFI -> Icons.Filled.Wifi
        com.moneymanager.app.data.local.entity.BillerType.DTH -> Icons.Filled.Tv
        com.moneymanager.app.data.local.entity.BillerType.INSURANCE -> Icons.Filled.Security
        com.moneymanager.app.data.local.entity.BillerType.LOAN_EMI -> Icons.Filled.Payments
        com.moneymanager.app.data.local.entity.BillerType.RENT -> Icons.Filled.Home
        com.moneymanager.app.data.local.entity.BillerType.PENSION -> Icons.Filled.Savings
        com.moneymanager.app.data.local.entity.BillerType.CREDIT_CARD -> Icons.Filled.CreditCard
        com.moneymanager.app.data.local.entity.BillerType.OTHER -> Icons.Filled.Payments
    }
