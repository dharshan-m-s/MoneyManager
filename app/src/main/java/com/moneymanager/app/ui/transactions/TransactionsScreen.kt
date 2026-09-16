package com.moneymanager.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.*
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TxnSort { NEWEST, OLDEST, HIGHEST, LOWEST, NAME_AZ, NAME_ZA }
private enum class TxnFilterType { ALL, EXPENSE, INCOME, TRANSFER }

private data class TxnFilters(
    val type: TxnFilterType = TxnFilterType.ALL,
    val text: String = "",
    val category: String = "",
    val payment: String = "",
    val businessPersonal: String = "",
    val txnSubtype: String = "",
    val account: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val minAmount: String = "",
    val maxAmount: String = "",
    val reimbursableOnly: Boolean = false,
    val pendingReimbursementOnly: Boolean = false
) {
    fun isActive(): Boolean = this != TxnFilters()
}

private val dateInputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val zone = ZoneId.of("Asia/Kolkata")

@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val rows by viewModel.transactions.collectAsState()
    var sort by remember { mutableStateOf(TxnSort.NEWEST) }
    var filters by remember { mutableStateOf(TxnFilters()) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val visibleRows = remember(rows, sort, filters) {
        val filtered = rows.filter { txn -> matchesFilters(txn, filters) }
        when (sort) {
            TxnSort.NEWEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { it.occurredAtEpochMillis }.thenByDescending { it.id })
            TxnSort.OLDEST -> filtered.sortedWith(compareBy<TransactionEntity> { it.occurredAtEpochMillis }.thenBy { it.id })
            TxnSort.HIGHEST -> filtered.sortedWith(compareByDescending<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.LOWEST -> filtered.sortedWith(compareBy<TransactionEntity> { kotlin.math.abs(it.creditMinorUnits - it.debitMinorUnits) }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_AZ -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { txn: TransactionEntity -> txn.merchantReceiverSender ?: txn.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
            TxnSort.NAME_ZA -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { txn: TransactionEntity -> txn.merchantReceiverSender ?: txn.rawCategoryName ?: "" }.thenByDescending { it.occurredAtEpochMillis })
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = "Transactions",
            subtitle = "${visibleRows.size} transactions",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showFilter = true }) {
                    Icon(Icons.Filled.FilterList, "Filter transactions", tint = MMWhite)
                }
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.Sort, "Sort transactions", tint = MMWhite)
                }
            }
        )

        if (filters.isActive()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filters active • ${visibleRows.size} results",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { filters = TxnFilters() }) { Text("Clear") }
            }
        }

        if (visibleRows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(32.dp))
                    Text("No transactions match these filters.", color = MMGrayText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    if (filters.isActive()) TextButton(onClick = { filters = TxnFilters() }) { Text("Clear filters") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(visibleRows, key = { it.id }) { txn ->
                    ResponsiveTransactionRow(txn = txn, onClick = { onTransactionClick(txn.id) })
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = .10f))
                }
            }
        }
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text("Sort transactions") },
            text = {
                Column {
                    listOf(
                        TxnSort.NEWEST to "Newest first",
                        TxnSort.OLDEST to "Oldest first",
                        TxnSort.HIGHEST to "Highest amount",
                        TxnSort.LOWEST to "Lowest amount",
                        TxnSort.NAME_AZ to "Name (A–Z)",
                        TxnSort.NAME_ZA to "Name (Z–A)"
                    ).forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { sort = value; showSort = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sort == value, onClick = { sort = value; showSort = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSort = false }) { Text("CLOSE") } }
        )
    }

    if (showFilter) {
        TransactionFilterDialog(
            initial = filters,
            onDismiss = { showFilter = false },
            onApply = { filters = it; showFilter = false }
        )
    }
}

private fun matchesFilters(txn: TransactionEntity, f: TxnFilters): Boolean {
    val text = f.text.trim().lowercase()
    if (text.isNotBlank()) {
        val haystack = listOfNotNull(
            txn.merchantReceiverSender, txn.rawCategoryName, txn.notes, txn.rawPaymentType,
            txn.txnType.name, txn.txnSubType.name, txn.txnKind.name, txn.paymentType.name,
            txn.businessPersonal.name, txn.rawDateString
        ).joinToString(" ").lowercase()
        if (!haystack.contains(text)) return false
    }
    when (f.type) {
        TxnFilterType.ALL -> Unit
        TxnFilterType.EXPENSE -> if (txn.txnSubType != TxnSubType.EXPENSE) return false
        TxnFilterType.INCOME -> if (txn.txnSubType != TxnSubType.INCOME) return false
        TxnFilterType.TRANSFER -> if (txn.txnSubType != TxnSubType.TRANSFER_IN && txn.txnSubType != TxnSubType.TRANSFER_OUT) return false
    }
    if (f.category.isNotBlank() && !(txn.rawCategoryName ?: "").contains(f.category, ignoreCase = true)) return false
    if (f.payment.isNotBlank() && !(txn.rawPaymentType ?: txn.paymentType.name).contains(f.payment, ignoreCase = true)) return false
    if (f.businessPersonal.isNotBlank() && !txn.businessPersonal.name.equals(f.businessPersonal.trim(), ignoreCase = true)) return false
    if (f.txnSubtype.isNotBlank() && !txn.txnSubType.name.equals(f.txnSubtype.trim(), ignoreCase = true)) return false
    if (f.account.isNotBlank() && !txn.accountId.toString().contains(f.account.trim(), ignoreCase = true)) return false

    val date = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone).toLocalDate()
    parseDate(f.fromDate)?.let { if (date.isBefore(it)) return false }
    parseDate(f.toDate)?.let { if (date.isAfter(it)) return false }

    val amount = kotlin.math.abs(txn.creditMinorUnits - txn.debitMinorUnits)
    parseMinorAmount(f.minAmount)?.let { if (amount < it) return false }
    parseMinorAmount(f.maxAmount)?.let { if (amount > it) return false }
    if (f.reimbursableOnly && !txn.reimbursable) return false
    if (f.pendingReimbursementOnly && !(txn.reimbursable && !txn.reimbursed)) return false
    return true
}

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim(), dateInputFormatter) }.getOrNull()

private fun parseMinorAmount(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }?.let {
    runCatching { (it.toBigDecimal() * java.math.BigDecimal(100)).longValueExact() }.getOrNull()
}

@Composable
private fun TransactionFilterDialog(
    initial: TxnFilters,
    onDismiss: () -> Unit,
    onApply: (TxnFilters) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter transactions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(draft.text, { draft = draft.copy(text = it) }, label = { Text("Search all fields") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TxnFilterType.ALL to "All", TxnFilterType.EXPENSE to "Expense", TxnFilterType.INCOME to "Income", TxnFilterType.TRANSFER to "Transfer").forEach { (v, label) ->
                        FilterChip(label, draft.type == v) { draft = draft.copy(type = v) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(draft.fromDate, { draft = draft.copy(fromDate = it) }, label = { Text("From date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.toDate, { draft = draft.copy(toDate = it) }, label = { Text("To date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.minAmount, { draft = draft.copy(minAmount = it) }, label = { Text("Minimum amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.maxAmount, { draft = draft.copy(maxAmount = it) }, label = { Text("Maximum amount") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.category, { draft = draft.copy(category = it) }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.payment, { draft = draft.copy(payment = it) }, label = { Text("Payment type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.businessPersonal, { draft = draft.copy(businessPersonal = it) }, label = { Text("Business / Personal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.txnSubtype, { draft = draft.copy(txnSubtype = it) }, label = { Text("Transaction subtype") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.account, { draft = draft.copy(account = it) }, label = { Text("Account ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.reimbursableOnly, { draft = draft.copy(reimbursableOnly = it) })
                    Text("Reimbursable only")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.pendingReimbursementOnly, { draft = draft.copy(pendingReimbursementOnly = it) })
                    Text("Pending reimbursement only")
                }
                if (draft.fromDate.isNotBlank() && parseDate(draft.fromDate) == null) Text("Invalid from date", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.toDate.isNotBlank() && parseDate(draft.toDate) == null) Text("Invalid to date", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.minAmount.isNotBlank() && parseMinorAmount(draft.minAmount) == null) Text("Invalid minimum amount", color = MaterialTheme.colors.error, fontSize = 11.sp)
                if (draft.maxAmount.isNotBlank() && parseMinorAmount(draft.maxAmount) == null) Text("Invalid maximum amount", color = MaterialTheme.colors.error, fontSize = 11.sp)
            }
        },
        dismissButton = { TextButton(onClick = { draft = TxnFilters() }) { Text("RESET") } },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("APPLY") } }
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .18f))
    ) { Text(label, color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
}

@Composable
private fun ResponsiveTransactionRow(txn: TransactionEntity, onClick: () -> Unit) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val title = txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction"
    val subtitle = listOfNotNull(txn.rawCategoryName, txn.rawPaymentType).distinct().joinToString(" • ")
    val date = DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone))

    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Transfer", tint = MaterialTheme.colors.onSurface.copy(alpha = .55f), modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.ifBlank { "Transaction" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = .62f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 122.dp)) {
            Text(MoneyFormat.rupeesNoDecimals(amount.abs()), color = if (amount.isNegative) MMRedExpense else MMGreenIncome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(date, fontSize = 10.sp, color = MaterialTheme.colors.onSurface.copy(alpha = .58f), maxLines = 1, softWrap = false)
        }
    }
}
