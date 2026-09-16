package com.moneymanager.app.ui.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMAmberDue
import com.moneymanager.app.ui.theme.MMCashForward
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CashScreen(
    onBack: () -> Unit,
    onEditCash: (Long) -> Unit = {},
    onAddCashIncome: (Long?) -> Unit,
    onAddCashSpend: (Long?) -> Unit,
    viewModel: CashViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(false) }
    val filtered = remember(state.transactions, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.transactions else state.transactions.filter {
            listOfNotNull(it.merchantReceiverSender, it.rawCategoryName, it.notes, it.rawPaymentType, it.rawDateString)
                .joinToString(" ").lowercase().contains(q)
        }
    }
    val currentMonth = LocalDate.now(ZoneId.of("Asia/Kolkata")).withDayOfMonth(1)
    val grouped = filtered.groupBy {
        Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().withDayOfMonth(1)
    }.toSortedMap(compareByDescending { it }).toMutableMap()
    if (!grouped.containsKey(currentMonth)) grouped[currentMonth] = emptyList()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(MMGreenDark).padding(bottom = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
                    Text("Cash In Hand", color = MMWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (state.primaryCashAccountId != null) {
                        val cashAccountId = state.primaryCashAccountId!!
                        IconButton(onClick = { onEditCash(cashAccountId) }) { Icon(Icons.Filled.Edit, "Edit cash balance", tint = MMWhite) }
                    }
                    IconButton(onClick = { showFilter = !showFilter }) { Icon(Icons.Filled.FilterList, "Filter cash", tint = MMWhite) }
                }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Current Month", color = MMWhite.copy(alpha = .70f), fontSize = 12.sp)
                    Text(MoneyFormat.rupeesNoDecimals(state.cashInHand), color = MMWhite, fontSize = 35.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Text("Live cash balance from your ledger", color = MMWhite.copy(alpha = .70f), fontSize = 11.sp)
                }
            }

            if (showFilter) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        label = { Text("Search cash transactions") }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    TextButton(onClick = { query = "" }) { Text("Clear") }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CashMetric(Icons.Filled.Add, MoneyFormat.rupeesNoDecimals(state.monthIncome), "Income", MMGreenIncome)
                CashMetric(Icons.Filled.Remove, MoneyFormat.rupeesNoDecimals(state.monthSpend), "Spend", MMRedExpense)
            }
            Divider(color = MMGrayDivider)

            if (grouped.isEmpty()) {
                EmptyCashState()
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 92.dp)
                ) {
                    grouped.entries.sortedByDescending { it.key }.forEach { (month, rows) ->
                        val monthSpend = rows.filter { it.debitMinorUnits > it.creditMinorUnits && !it.txnSubType.name.contains("CASH_FORWARD") }.sumOf { it.debitMinorUnits - it.creditMinorUnits }
                        val monthIncome = rows.filter { it.creditMinorUnits > it.debitMinorUnits && !it.txnSubType.name.contains("CASH_FORWARD") }.sumOf { it.creditMinorUnits - it.debitMinorUnits }
                        val forward = rows.firstOrNull { it.txnSubType.name == "CREDIT_CASH_FORWARD" }
                        val visibleRows = rows.filterNot { it.txnSubType.name.contains("CASH_FORWARD") }
                        item(key = "month-${month}") {
                            CashMonthHeader(month, Money(monthIncome), Money(monthSpend))
                            if (month == currentMonth && visibleRows.isEmpty()) {
                                EmptyMonthRow("No cash transactions for this month", "Please add your cash transactions manually")
                            }
                            forward?.let { CashForwardRow(Money(it.creditMinorUnits - it.debitMinorUnits)) }
                        }
                        items(visibleRows, key = { it.id }) { txn -> CashTxnRow(txn) }
                    }
                }
            }
        }

        com.moneymanager.app.ui.components.CashFab(
            onAddCashTransaction = { onAddCashSpend(state.primaryCashAccountId) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        )
    }
}

@Composable
private fun CashMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, amount: String, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = tint.copy(alpha = .12f), modifier = Modifier.size(30.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.padding(7.dp))
        }
        Column(Modifier.padding(start = 7.dp)) {
            Text(amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(label, fontSize = 11.sp, color = MMGrayText)
        }
    }
}

@Composable
private fun CashMonthHeader(month: LocalDate, income: Money, spend: Money) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colors.surface).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy")).uppercase(), color = MMGreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("+ ${MoneyFormat.rupeesNoDecimals(income)}", color = MMGreenIncome, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("− ${MoneyFormat.rupeesNoDecimals(spend)}", color = MMRedExpense, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
    }
    Divider(color = MMGrayDivider)
}

@Composable
private fun EmptyMonthRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MMGrayText)
        Text(subtitle, fontSize = 12.sp, color = MMGrayText, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun CashForwardRow(amount: Money) {
    Row(Modifier.fillMaxWidth().background(MMCashForward, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 11.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MMGreen.copy(alpha = .15f), modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.ArrowUpward, null, tint = MMGreen, modifier = Modifier.padding(10.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text("Forward Cash Balance", color = MMGreenDark, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Cash", color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(MoneyFormat.rupeesNoDecimals(amount.abs()), color = MMGreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Divider(color = MMGrayDivider)
}

@Composable
private fun EmptyCashState() {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MMGreen.copy(alpha = .10f), modifier = Modifier.size(62.dp)) {
            Icon(Icons.Filled.Wallet, null, tint = MMGreen, modifier = Modifier.padding(17.dp))
        }
        Text("No cash transactions for this month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text("Add a cash transaction or import your statement to see it here.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun CashTxnRow(txn: TransactionEntity) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val isIncome = amount.minorUnits > 0L
    val title = txn.merchantReceiverSender?.takeIf { it.isNotBlank() } ?: txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Unknown transaction"
    val category = txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Cash"
    val date = DateTimeFormatter.ofPattern("dd MMM").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")))
    val accent = if (isIncome) MMGreen else MMAmberDue
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Box(Modifier.widthIn(min = 5.dp).height(70.dp).background(accent))
        Surface(shape = CircleShape, color = accent.copy(alpha = .13f), modifier = Modifier.size(52.dp).padding(start = 8.dp)) {
            Icon(if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.Wallet, null, tint = accent, modifier = Modifier.padding(14.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(category, color = MMGrayText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Text("Cash", color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 17.dp).widthIn(min = 86.dp, max = 132.dp)) {
            Text(
                MoneyFormat.rupeesNoDecimals(amount.abs()), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (isIncome) MMGreenIncome else MaterialTheme.colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(date, color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
    Divider(color = MMGrayDivider)
}