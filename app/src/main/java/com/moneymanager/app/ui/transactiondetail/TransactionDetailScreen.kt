package com.moneymanager.app.ui.transactiondetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(transactionId: Long, onBack: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val txn by viewModel.observe(transactionId).collectAsState(initial = null)
    val categories by viewModel.observeCategories().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var business by remember { mutableStateOf(false) }
    var reimbursable by remember { mutableStateOf(false) }
    var includeStats by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(txn?.id) {
        txn?.let { t ->
            amount = Money(t.creditMinorUnits.takeIf { it != 0L } ?: t.debitMinorUnits).abs().toBigDecimal().toPlainString()
            merchant = t.merchantReceiverSender.orEmpty()
            notes = t.notes.orEmpty()
            business = t.businessPersonal.raw == "business"
            reimbursable = t.reimbursable
            includeStats = t.includeInStatistics
            categoryId = t.categoryId
        }
    }

    Column(Modifier.fillMaxSize().background(MMBackground)) {
        Row(Modifier.fillMaxWidth().background(MMGreenDark).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
            Text(if (editing) "Edit Transaction" else "Transaction", color = MMWhite, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (txn != null) {
                IconButton(onClick = {
                    if (editing) viewModel.saveEditable(transactionId, amount, merchant, notes, business, reimbursable, includeStats, categoryId) { editing = false }
                    else editing = true
                }) { Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, if (editing) "Save" else "Edit", tint = MMWhite) }
            }
        }

        if (txn == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Transaction not found.", color = MMGrayText) }
        } else {
            val t = txn!!
            val amountValue = Money(t.creditMinorUnits - t.debitMinorUnits)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(MoneyFormat.rupeesNoDecimals(amountValue.abs()), fontSize = 30.sp, color = if (amountValue.isNegative) MMRedExpense else MMGreenIncome)
                if (editing && t.txnSubType.name != "TRANSFER_IN" && t.txnSubType.name != "TRANSFER_OUT") {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / Receiver / Sender") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(categories.firstOrNull { it.id == categoryId }?.name ?: "Uncategorized", modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(onClick = { categoryId = null; categoryMenu = false }) { Text("Uncategorized") }
                            categories.forEach { c ->
                                DropdownMenuItem(onClick = { categoryId = c.id; categoryMenu = false }) { Text(c.name) }
                            }
                        }
                    }
                    SettingRow("Business", business) { business = it }
                    SettingRow("Reimbursable", reimbursable) { reimbursable = it }
                    SettingRow(if (t.txnSubType.name == "INCOME") "Include in income statistics" else "Include in spend statistics", includeStats) { includeStats = it }
                    Text("Date, account, payment type and imported balance/snapshot fields are protected. Amount, category, description and analytics flags are editable and the account is recalculated after saving.", fontSize = 11.sp, color = MMGrayText)
                } else {
                    if (editing) Text("Transfer legs are kept intact; edit their details through the transfer flow.", fontSize = 12.sp, color = MMGrayText)
                    DetailRow("Merchant / Receiver / Sender", t.merchantReceiverSender ?: "—")
                    DetailRow("Category", t.rawCategoryName ?: "—")
                    DetailRow("Date", DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(Instant.ofEpochMilli(t.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))))
                    DetailRow("Type", t.txnType.raw.ifBlank { t.txnType.name })
                    DetailRow("SubType", t.txnSubType.raw.ifBlank { t.txnSubType.name })
                    DetailRow("Transaction Type", t.txnKind.raw.ifBlank { t.txnKind.name })
                    DetailRow("Payment Type", t.rawPaymentType ?: t.paymentType.raw.ifBlank { t.paymentType.name })
                    DetailRow("Personal / Business", t.businessPersonal.raw.ifBlank { t.businessPersonal.name })
                    DetailRow("Account ID", t.accountId.toString())
                    DetailRow("Credit", MoneyFormat.rupeesNoDecimals(Money(t.creditMinorUnits)))
                    DetailRow("Debit", MoneyFormat.rupeesNoDecimals(Money(t.debitMinorUnits)))
                    DetailRow("Balance Snapshot", t.balanceSnapshotMinorUnits?.let { MoneyFormat.rupeesNoDecimals(Money(it)) } ?: "—")
                    DetailRow("Outstanding Snapshot", t.outstandingSnapshotMinorUnits?.let { MoneyFormat.rupeesNoDecimals(Money(it)) } ?: "—")
                    DetailRow("Available Limit Snapshot", t.availableLimitSnapshotMinorUnits?.let { MoneyFormat.rupeesNoDecimals(Money(it)) } ?: "—")
                    DetailRow("Reimbursable", if (t.reimbursable) "Yes" else "No")
                    DetailRow("Reimbursed", if (t.reimbursed) "Yes" else "No")
                    DetailRow("Notes", t.notes ?: "—")
                }
            }
        }
    }
}

@Composable private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontSize = 14.sp); Switch(checked, onChange) }
}

@Composable private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, fontSize = 11.sp, color = MMGrayText); Text(value, fontSize = 14.sp); Divider(color = MMGrayDivider, modifier = Modifier.padding(top = 6.dp)) }
}
