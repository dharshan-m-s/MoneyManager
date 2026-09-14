package com.moneymanager.app.ui.spendsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Drill-down list for one category AND one income/spend + included/excluded direction,
 * so both "Spend Areas" and the dedicated "Income Areas" screens share the same detail view.
 */
@Composable
fun CategoryTransactionsScreen(
    categoryId: Long,
    subType: TxnSubType,
    included: Boolean,
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: CategoryTransactionsViewModel = hiltViewModel()
) {
    LaunchedEffect(subType, included) { viewModel.configure(subType, included) }
    val rowsFlow = remember(categoryId) { viewModel.transactions(categoryId) }
    val rows by rowsFlow.collectAsState()
    var title by remember { mutableStateOf("Transactions") }
    LaunchedEffect(categoryId) { title = viewModel.categoryName(categoryId) }
    val isIncomeView = subType == TxnSubType.INCOME
    val gradient = if (isIncomeView) listOf(PageIncomeStart, PageIncomeEnd) else listOf(PageSpendStart, PageSpendEnd)
    val total = rows.sumOf { if (isIncomeView) it.creditMinorUnits - it.debitMinorUnits else it.debitMinorUnits - it.creditMinorUnits }
    val amountColor = if (isIncomeView) MMGreenIncome else MMRedExpense
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(gradient)).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MMWhite) }
                Column {
                    Text(title, color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("All history • ${rows.size} transactions", color = MMWhite.copy(alpha = .7f), fontSize = 10.sp)
                }
            }
        }
        Surface(elevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (isIncomeView) "Category income" else "Category spend", color = MMGrayText, fontSize = 11.sp)
                    Text(MoneyFormat.rupeesNoDecimals(Money(total)), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Text("Tap a transaction to inspect", color = MMGrayText, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transactions recorded in this category.", color = MMGrayText, fontSize = 13.sp) }
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, key = { it.id }) { txn ->
                val a = if (isIncomeView) txn.creditMinorUnits - txn.debitMinorUnits else txn.debitMinorUnits - txn.creditMinorUnits
                Row(
                    Modifier.fillMaxWidth().clickable { onTransactionClick(txn.id) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(txn.merchantReceiverSender ?: "Transaction", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(txn.notes ?: txn.rawPaymentType ?: "", fontSize = 10.sp, color = MMGrayText, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 118.dp)) {
                        Text(MoneyFormat.rupeesNoDecimals(Money(a)), color = amountColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Text(DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))), fontSize = 10.sp, color = MMGrayText, maxLines = 1)
                    }
                }
                Divider(color = MMGrayDivider)
            }
        }
    }
}