package com.moneymanager.app.ui.bills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.AddBillerFab
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.components.StatusPill
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    Scaffold(
        floatingActionButton = {
            AddBillerFab(onAddBiller = onAddBiller, modifier = Modifier.padding(bottom = 8.dp))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MoneyManagerTopBar(
                title = "Bills & EMIs",
                subtitle = "Track upcoming payments",
                onBack = onBack,
                pageColors = BillsColors
            )

            if (bills.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BillsColors.accent.copy(alpha = 0.1f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = BillsColors.accent, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text(
                        "No bills added yet.",
                        color = MMGrayText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        "Bills you add or that get generated from imported recurring payments will show up here.",
                        color = MMGrayText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        "Meanwhile add your bills for which you don't get SMSs like rent, maid, EMI etc",
                        color = MMGrayText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 112.dp, top = 4.dp)) {
                    items(bills) { row ->
                        BillCard(
                            row = row,
                            onClick = { onEditBiller(row.bill.id) },
                            onMarkPaid = { if (row.nextInstance != null) viewModel.findPaymentTransactions(row) }
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
            state = paymentSearch,
            onSelectTransaction = viewModel::selectPaymentTransaction,
            onPaidByCash = { if (!paymentSearch.loading) pendingRow.nextInstance?.let { viewModel.markAsPaid(it, null) } },
            onConfirm = {
                val transactionId = paymentSearch.selectedTransactionId
                val instance = pendingRow.nextInstance
                if (instance != null && transactionId != null && !paymentSearch.loading) {
                    viewModel.markAsPaid(instance, transactionId)
                }
            },
            onDismiss = viewModel::clearPaymentSearch
        )
    }
}

@Composable
private fun BillPaymentDialog(
    billName: String,
    amount: Money,
    state: BillPaymentSearchState,
    onSelectTransaction: (Long?) -> Unit,
    onPaidByCash: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRecent by remember(state.billRow?.bill?.id) { mutableStateOf(false) }
    var recentQuery by remember(state.billRow?.bill?.id) { mutableStateOf("") }
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
                    tx.merchantReceiverSender, tx.notes, tx.rawCategoryName, tx.rawPaymentType,
                    tx.txnType.name, tx.txnSubType.name, candidate.account?.nickname,
                    candidate.account?.institutionName, candidate.account?.sourceAccountId,
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
            color = MaterialTheme.colors.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mark bill as paid", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
                        Text(
                            "$billName \u2022 ${MoneyFormat.rupeesNoDecimals(amount)}",
                            fontSize = 12.sp,
                            color = MMGrayText,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MMGrayText)
                    }
                }

                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = 0.dp,
                    backgroundColor = MMSurfaceMuted
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BillsColors.accent.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = BillsColors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("We checked your recent payments", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Pick the transaction you already recorded. Nothing new will be created.",
                                fontSize = 11.sp,
                                color = MMGrayText,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                if (state.loading) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Searching recent transactions\u2026", color = MMGrayText, fontSize = 13.sp)
                    }
                } else if (!showRecent) {
                    if (state.matches.isEmpty()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text("No strong automatic match", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "The biller name or amount may be different in your statement. Use Recent Transactions to choose it manually.",
                                fontSize = 12.sp,
                                color = MMGrayText,
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
                    OutlinedTextField(
                        value = recentQuery,
                        onValueChange = { recentQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search recent payments by name, amount, date, account\u2026") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
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
                            color = MMGrayText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }

                state.error?.let {
                    Text(it, color = MMMaroonOverdue, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }

                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (!showRecent) {
                        Button(
                            onClick = { showRecent = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MMSurfaceElevated, contentColor = BillsColors.accent),
                            border = BorderStroke(1.dp, BillsColors.accent),
                            elevation = null
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                            Text("Choose from Recent Transactions", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { showRecent = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to Best Matches", color = BillsColors.accent, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = onPaidByCash,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = BillsColors.accent, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(19.dp))
                        Text("Paid by Cash", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = !state.loading && state.selectedTransactionId != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MMSurfaceElevated, contentColor = BillsColors.accent),
                        border = BorderStroke(1.dp, BillsColors.accent),
                        elevation = null
                    ) {
                        Text("Link Selected Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelectTransaction(tx.id) },
        shape = RoundedCornerShape(16.dp),
        elevation = if (selectedTransactionId == tx.id) 3.dp else 1.dp,
        backgroundColor = if (selectedTransactionId == tx.id) BillsColors.accent.copy(alpha = 0.07f) else MaterialTheme.colors.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selectedTransactionId == tx.id, onClick = { onSelectTransaction(tx.id) }, colors = RadioButtonDefaults.colors(selectedColor = BillsColors.accent))
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    listOf(dateText, accountText.ifBlank { null }).filterNotNull().joinToString(" \u2022 "),
                    fontSize = 11.sp,
                    color = MMGrayText,
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
                    color = MMRedExpense,
                    maxLines = 1,
                    softWrap = false
                )
                if (selectedTransactionId == tx.id) {
                    Text("Selected", color = BillsColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BillCard(
    row: BillRow,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit
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
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM")
    val zone = ZoneId.of("Asia/Kolkata")

    val statusText: String
    val statusColor: Color
    if (paid) {
        statusText = "Paid"
        statusColor = MMGreenIncome
    } else if (next != null) {
        statusText = if (overdue) "${-daysUntilDue} days overdue" else {
            val days = daysUntilDue
            if (days == 1L) "1 day to pay" else "$days days to pay"
        }
        statusColor = if (overdue) MMMaroonOverdue else MMAmberDue
    } else {
        statusText = "No bill due"
        statusColor = MMGrayText
    }

    val dueDateStr = dateFormat.format(Instant.ofEpochMilli(dueMillis).atZone(zone))
    val displayDate = if (paid) {
        val paidMillis = latestPaid.paidAtEpochMillis ?: dueMillis
        "Paid on: " + dateFormat.format(Instant.ofEpochMilli(paidMillis).atZone(zone))
    } else {
        "Due on: $dueDateStr"
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clickableBill(onClick),
        elevation = 1.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(row.bill.billerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, letterSpacing = (-0.1).sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.bill.accountReferenceId?.let {
                            Text(it, fontSize = 11.sp, color = MMGrayText)
                        }
                        Text(
                            "  ${row.bill.businessPersonal.name}",
                            fontSize = 10.sp,
                            color = MMGrayText
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (paid) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MMGreenIncome, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(next?.amountDueMinorUnits ?: row.bill.estimatedAmountMinorUnits)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (overdue) MMMaroonOverdue else (if (paid) MMGreenIncome else MMRedExpense),
                    letterSpacing = (-0.1).sp
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        displayDate,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                "* Bill amount is based on previous month's bill",
                fontSize = 10.sp,
                color = MMGrayText,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (next != null) {
                Button(
                    onClick = onMarkPaid,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MMSurfaceElevated,
                        contentColor = BillsColors.accent,
                        disabledBackgroundColor = MMBackground,
                        disabledContentColor = MMGrayText
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BillsColors.accent)
                ) {
                    Text("Mark as Paid", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Modifier.clickableBill(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
