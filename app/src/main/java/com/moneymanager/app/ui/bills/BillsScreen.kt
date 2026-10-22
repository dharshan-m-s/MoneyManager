package com.moneymanager.app.ui.bills

import com.moneymanager.app.ui.theme.MmColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.RadioButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.billing.BillPaymentWindow
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.AddBillerFab
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
import com.moneymanager.app.ui.components.revealOnAppear
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.moneymanager.app.ui.components.MmSecondaryButton

@Composable
fun BillsScreen(
    onBack: () -> Unit,
    onAddBiller: () -> Unit = {},
    onEditBiller: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    viewModel: BillsViewModel = hiltViewModel()
) {
    val bills by viewModel.bills.collectAsState()
    val paymentSearch by viewModel.paymentSearch.collectAsState()
    val snackbarState = remember { SnackbarHostState() }

    LaunchedEffect(paymentSearch.message) {
        paymentSearch.message?.let {
            snackbarState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            AddBillerFab(onAddBiller = onAddBiller, modifier = Modifier.padding(bottom = 8.dp))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MoneyManagerTopBar(
                title = "Bills & EMIs",
                subtitle = "Track upcoming payments without duplicate transactions",
                onBack = onBack,
            )

            if (bills.isEmpty()) {
                MmEmptyState(
                    icon = Icons.Filled.Payments,
                    title = "No bills yet",
                    message = "Add the bills you pay regularly - rent, maid, EMI, utilities - and track " +
                        "them here. Recurring payments found in imported statements appear automatically.",
                    actionLabel = "Add a bill",
                    onAction = onAddBiller
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 112.dp)) {
                    itemsIndexed(bills, key = { _, row -> row.bill.id }) { index, row ->
                        BillCard(
                            row = row,
                            modifier = Modifier.revealOnAppear(index = index),
                            onClick = { onEditBiller(row.bill.id) },
                            onMarkPaid = { if (row.nextInstance != null) viewModel.findPaymentTransactions(row) },
                            onConfirmDetectedPayment = { viewModel.markDetectedPaymentPaid(row) }
                        )
                    }
                }
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
            onPaidByCash = { amountMinorUnits ->
                if (!paymentSearch.loading && !paymentSearch.saving) {
                    viewModel.markAsPaid(pendingRow.nextInstance!!, null, amountMinorUnits)
                }
            },
            onConfirm = {
                val transactionId = paymentSearch.selectedTransactionId
                val instance = pendingRow.nextInstance
                if (instance != null && transactionId != null && !paymentSearch.loading && !paymentSearch.saving) {
                    viewModel.markAsPaid(instance, transactionId)
                }
            },
            onRecordSource = viewModel::setRecordSourceAccount,
            onRecordAmount = viewModel::setRecordAmount,
            onRecordDate = viewModel::setRecordDate,
            onRecordNotes = viewModel::setRecordNotes,
            onRecordPayment = viewModel::recordPayment,
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
    onPaidByCash: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onRecordSource: (Long) -> Unit,
    onRecordAmount: (String) -> Unit,
    onRecordDate: (Long) -> Unit,
    onRecordNotes: (String) -> Unit,
    onRecordPayment: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRecent by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var recentQuery by remember(state.billRow?.bill?.id) { mutableStateOf("") }
    var showRecordPayment by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var showCashPayment by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var cashAmount by remember(state.billRow?.bill?.id) { mutableStateOf("") }
    var showAccountPicker by remember { mutableStateOf(false) }
    val selectedSource = state.accounts.firstOrNull { it.id == state.recordSourceAccountId }
    val occurrence = state.billRow?.nextInstance
    val paymentFormScroll = rememberScrollState()
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val zone = ZoneId.of("Asia/Kolkata")

    val recentRows: List<BillPaymentCandidate> = remember(state.recentTransactions, recentQuery) {
        val q = recentQuery.trim().lowercase()
        val queryTokens = q.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        if (queryTokens.isEmpty()) {
            state.recentTransactions
        } else {
            val scored: List<Pair<BillPaymentCandidate, Int>> = state.recentTransactions.mapNotNull { candidate ->
                val tx = candidate.transaction
                val haystack = listOfNotNull(
                    tx.merchantReceiverSender,
                    tx.notes,
                    tx.rawCategoryName,
                    tx.rawPaymentType,
                    tx.txnType.name,
                    tx.txnSubType.name,
                    candidate.account?.nickname,
                    candidate.account?.institutionName,
                    candidate.account?.sourceAccountId,
                    MoneyFormat.rupeesNoDecimals(Money(tx.debitMinorUnits)),
                    MoneyFormat.rupeesNoDecimals(Money(tx.debitMinorUnits)).replace(Regex("[^0-9.]"), ""),
                    dateFormat.format(Instant.ofEpochMilli(tx.occurredAtEpochMillis).atZone(zone))
                ).joinToString(" ").lowercase()
                val matched = queryTokens.count { token -> haystack.contains(token) }
                if (matched > 0) candidate to matched else null
            }
            scored
                .sortedWith(compareByDescending<Pair<BillPaymentCandidate, Int>> { it.second }
                    .thenByDescending { it.first.transaction.occurredAtEpochMillis })
                .map { it.first }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f),
            shape = RoundedCornerShape(24.dp),
            elevation = 14.dp,
            color = androidx.compose.material.MaterialTheme.colors.surface
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (showRecordPayment) Modifier.verticalScroll(paymentFormScroll)
                        else Modifier
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mark bill as paid", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$billName • ${MoneyFormat.rupeesNoDecimals(amount)}",
                            fontSize = 12.sp,
                            color = MmColors.textSecondary,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MmColors.textSecondary)
                    }
                }

                Card(
                    Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
                    shape = RoundedCornerShape(MmSpacing.radiusRow),
                    elevation = 0.dp,
                    backgroundColor = MmColors.surfaceMuted
                ) {
                    Row(Modifier.padding(MmSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MmColors.accent, modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("We checked your recent payments", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pick the transaction you already recorded. Nothing new will be created.",
                            fontSize = 11.sp,
                            color = MmColors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                if (occurrence != null && occurrence.paidAmountMinorUnits > 0L) {
                    val fmt = { minor: Long -> MoneyFormat.rupeesNoDecimals(Money(minor)) }
                    Text(
                        "Already paid ${fmt(occurrence.paidAmountMinorUnits)} of " +
                            "${fmt(occurrence.amountDueMinorUnits)} - " +
                            "${fmt(occurrence.amountDueMinorUnits - occurrence.paidAmountMinorUnits)} remaining",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MmColors.accent,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                }

                if (!showRecordPayment) {
                if (state.loading) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Searching recent transactions…", color = MmColors.textSecondary, fontSize = 13.sp)
                    }
                } else if (!showRecent) {
                    if (state.matches.isEmpty()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text("No strong automatic match", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "The biller name or amount may be different in your statement. Use Recent Transactions to choose it manually.",
                                fontSize = 12.sp,
                                color = MmColors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            "Best matches",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            items(state.matches, key = { it.transaction.id }) { candidate ->
                                BillPaymentCandidateRow(candidate, state.selectedTransactionId, dateFormat, zone, onSelectTransaction)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.sm)) {
                        MmSearchField(
                            value = recentQuery,
                            onValueChange = { recentQuery = it },
                            hint = "Search by name, amount, date or account"
                        )
                    }
                    Text(
                        "Recent transactions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        items(recentRows, key = { it.transaction.id }) { candidate ->
                            BillPaymentCandidateRow(candidate, state.selectedTransactionId, dateFormat, zone, onSelectTransaction)
                        }
                    }
                    if (recentRows.isEmpty()) {
                        Text(
                            "No recent transaction matches that search.",
                            color = MmColors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }

                }

                if (isCreditCard && showRecordPayment) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs)) {
                        Text("Record a new payment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Creates a transfer from the source account and reduces the card " +
                                "outstanding. It is not counted as ordinary spending.",
                            fontSize = 11.sp,
                            color = MmColors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = MmSpacing.sm)
                        )
                        MmCard(onClick = { showAccountPicker = true }) {
                            Text("Paid from", style = MmType.caption, color = MmColors.textSecondary)
                            Text(
                                selectedSource?.nickname?.ifBlank { selectedSource.institutionName }
                                    ?: "Choose an account",
                                style = MmType.body,
                                color = if (selectedSource != null) MmColors.textPrimary else MmColors.expense
                            )
                        }
                        Spacer(Modifier.height(MmSpacing.sm))
                        MmAmountField(
                            value = state.recordAmount,
                            onValueChange = onRecordAmount,
                            label = "Payment amount"
                        )
                        Spacer(Modifier.height(MmSpacing.sm))
                        MmDateField(
                            label = "Payment date",
                            epochMillis = state.recordDateEpochMillis,
                            onChange = onRecordDate,
                            includeTime = true
                        )
                        Spacer(Modifier.height(MmSpacing.sm))
                        MmPrimaryButton(
                            text = if (state.saving) "Recording…" else "Record payment",
                            onClick = onRecordPayment,
                            enabled = !state.saving && state.recordSourceAccountId != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }

                val selectedTxn = (state.matches + state.recentTransactions)
                    .firstOrNull { it.transaction.id == state.selectedTransactionId }
                    ?.transaction
                if (selectedTxn != null && occurrence != null) {
                    val fmt = { minor: Long -> MoneyFormat.rupeesNoDecimals(Money(minor)) }
                    val remainingForLink =
                        (occurrence.amountDueMinorUnits - occurrence.paidAmountMinorUnits)
                            .coerceAtLeast(0L)
                    val txnAmount = selectedTxn.debitMinorUnits
                    val outcome = when {
                        txnAmount >= remainingForLink -> {
                            val excess = txnAmount - remainingForLink
                            if (excess > 0L) {
                                "Records ${fmt(txnAmount)} - settles this occurrence with " +
                                    "${fmt(excess)} extra, kept as a credit"
                            } else {
                                "Records ${fmt(txnAmount)} - settles this occurrence in full"
                            }
                        }
                        else -> "Records ${fmt(txnAmount)} - " +
                            "${fmt(remainingForLink - txnAmount)} still due after this"
                    }
                    Text(
                        outcome,
                        fontSize = 12.sp,
                        color = MmColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                state.error?.let {
                    Text(it, color = MmColors.expense, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }

                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (isCreditCard) {
                        MmPrimaryButton(
                            text = if (showRecordPayment) "Hide payment form" else "Record a new payment",
                            onClick = { showRecordPayment = !showRecordPayment },
                            icon = Icons.Filled.Payments,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                    if (!showRecent) {
                        MmSecondaryButton(
                            text = "Choose from Recent Transactions",
                            onClick = { showRecent = true },
                            icon = Icons.Filled.Search,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    } else {
                        TextButton(onClick = { showRecent = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to Best Matches", color = MmColors.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (showCashPayment) {
                        val cashMinor = runCatching {
                            Money.fromRupees(BigDecimal(cashAmount.trim().ifBlank { "0" })).minorUnits
                        }.getOrNull() ?: 0L
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            MmAmountField(
                                value = cashAmount,
                                onValueChange = { cashAmount = it },
                                label = "Amount paid now"
                            )
                            Spacer(Modifier.height(MmSpacing.sm))
                            MmPrimaryButton(
                                text = if (state.saving) "Recording…" else "Confirm cash payment",
                                onClick = { onPaidByCash(cashMinor) },
                                enabled = !state.saving && cashMinor > 0L,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                            TextButton(
                                onClick = { showCashPayment = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = MmColors.textSecondary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                    MmSecondaryButton(
                        text = if (isCreditCard) "Already recorded — settle by cash" else "Paid by Cash",
                        onClick = {
                            cashAmount = occurrence
                                ?.let { (it.amountDueMinorUnits - it.paidAmountMinorUnits).coerceAtLeast(0L) }
                                ?.let { Money(it).toBigDecimal().stripTrailingZeros().toPlainString() }
                                ?: ""
                            showCashPayment = true
                        },
                        enabled = !state.saving,
                        icon = Icons.Filled.Payments,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)
                    )
                    }

                    MmSecondaryButton(
                        text = "Link Selected Payment",
                        onClick = onConfirm,
                        enabled = !state.loading && !state.saving && state.selectedTransactionId != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)
                    )
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

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).pressableClick { onSelectTransaction(tx.id) },
        shape = RoundedCornerShape(16.dp),
        elevation = if (selectedTransactionId == tx.id) 3.dp else 1.dp,
        backgroundColor = if (selectedTransactionId == tx.id) MmColors.accent.copy(alpha = .08f) else MmColors.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
    onMarkPaid: () -> Unit,
    onConfirmDetectedPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val partial = next != null && next.paidAmountMinorUnits in 1L until next.amountDueMinorUnits
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM")
    val zone = ZoneId.of("Asia/Kolkata")

    // "Mark as paid" only makes sense once the payment date is near: an occurrence that is
    // months away has nothing to settle yet. Overdue bills and partially paid ones stay
    // actionable.
    val payable = BillPaymentWindow.isPayableNow(row.bill, next, now)
    val detected = row.detectedPayment

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
    } else if (partial && next != null) {
        "Paid ${MoneyFormat.rupeesNoDecimals(Money(next.paidAmountMinorUnits))} of " +
            "${MoneyFormat.rupeesNoDecimals(Money(next.amountDueMinorUnits))} • Due $dueDateStr"
    } else {
        "Due on: $dueDateStr"
    }

    // What still has to be paid: the remaining part of the amount due after recorded payments.
    val amountRemainingMinor = next?.let { it.amountDueMinorUnits - it.paidAmountMinorUnits }
        ?: row.bill.estimatedAmountMinorUnits

    MmCard(
        modifier = modifier
            .padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs)
            .then(
                if (overdue) Modifier.border(
                    width = 1.5.dp,
                    color = MmColors.expense.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(MmSpacing.radiusCard)
                ) else Modifier
            ),
        onClick = onClick,
        contentPadding = PaddingValues(MmSpacing.card)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(
                icon = billerIcon(row.bill.billerType),
                tint = if (overdue) MmColors.expense else if (paid) MmColors.income else MmColors.warning,
                size = 44.dp
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
        }

        Spacer(Modifier.height(MmSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                MoneyFormat.rupeesNoDecimals(Money(amountRemainingMinor)),
                style = MmType.amountRow,
                color = when {
                    overdue -> MmColors.expense
                    paid -> MmColors.income
                    else -> MmColors.textPrimary
                }
            )
            Spacer(Modifier.width(MmSpacing.sm))
            MmPill(statusText, tint = statusColor, filled = true)
            Spacer(Modifier.weight(1f))
            Text(displayDate, style = MmType.caption, color = MmColors.textSecondary, maxLines = 1)
        }

        if (detected != null && next != null) {
            // The money already reached this card. Tell the user and let them settle the
            // bill in one tap instead of hunting for the transaction themselves.
            Spacer(Modifier.height(MmSpacing.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MmSpacing.radiusRow))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                MmColors.income.copy(alpha = 0.18f),
                                MmColors.income.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .pressableClick(onClick = onConfirmDetectedPayment)
                    .padding(horizontal = MmSpacing.md, vertical = MmSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material.Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MmColors.income,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(MmSpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Payment of " + MoneyFormat.rupeesNoDecimals(Money(detected.creditMinorUnits)) + " detected",
                        style = MmType.label,
                        color = MmColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Tap to mark this bill paid",
                        style = MmType.caption,
                        color = MmColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (next != null && payable) {
            Spacer(Modifier.height(MmSpacing.md))
            MmPrimaryButton(
                text = "Mark as paid",
                onClick = onMarkPaid,
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (next != null) {
            // Still ahead of the payment window: say when the action appears instead of
            // offering a button the user cannot use meaningfully yet.
            Spacer(Modifier.height(MmSpacing.md))
            val lead = BillPaymentWindow.leadDays(row.bill)
            Text(
                when {
                    daysUntilDue <= 0L -> "Mark as paid available"
                    else -> "Mark as paid appears " +
                        "$lead day${if (lead == 1) "" else "s"} before the due date" +
                        " (${daysUntilDue} to go)"
                },
                style = MmType.caption,
                color = MmColors.textSecondary
            )
        }
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