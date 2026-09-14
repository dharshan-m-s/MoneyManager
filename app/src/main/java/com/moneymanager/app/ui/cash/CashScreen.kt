package com.moneymanager.app.ui.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.*
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
    onTransactionClick: (Long) -> Unit = {},
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
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(PageCashStart, PageCashEnd),
                                startX = 0f,
                                endX = 1200f
                            )
                        )
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite)
                            }
                            Text(
                                "Cash In Hand",
                                color = MMWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                                modifier = Modifier.weight(1f)
                            )
                            state.primaryCashAccountId?.let { cashAccountId ->
                                IconButton(onClick = { onEditCash(cashAccountId) }) {
                                    Icon(Icons.Filled.Edit, "Edit cash balance", tint = MMWhite)
                                }
                            }
                            IconButton(onClick = { showFilter = !showFilter }) {
                                Icon(Icons.Filled.FilterList, "Filter cash", tint = MMWhite)
                            }
                        }
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                            Text("Current Month", color = MMWhite.copy(alpha = 0.75f), fontSize = 12.sp)
                            Text(
                                MoneyFormat.rupeesNoDecimals(state.cashInHand),
                                color = MMWhite,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text("Live cash balance from your ledger", color = MMWhite.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                    }
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
                    TextButton(onClick = { query = "" }) { Text("Clear", color = CashColors.accent) }
                }
            }

            // Metrics row with cyan accent
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CashMetricCard(Icons.Filled.Add, MoneyFormat.rupeesNoDecimals(state.monthIncome), "Income", MMGreenIncome, CashColors.accent)
                CashMetricCard(Icons.Filled.Remove, MoneyFormat.rupeesNoDecimals(state.monthSpend), "Spend", MMRedExpense, CashColors.accent)
            }
            Divider(color = MMGrayDivider)

            if (grouped.isEmpty()) {
                EmptyCashState()
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 92.dp, top = 4.dp)
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
                        items(visibleRows, key = { it.id }) { txn -> CashTxnRow(txn, onTransactionClick = onTransactionClick) }
                    }
                }
            }
        }

        com.moneymanager.app.ui.components.CashIncomeSpendFab(
            onAddCashIncome = { onAddCashIncome(state.primaryCashAccountId) },
            onAddCashSpend = { onAddCashSpend(state.primaryCashAccountId) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
        )
    }
}

@Composable
private fun CashMetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, amount: String, label: String, tint: Color, pageAccent: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pageAccent.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, pageAccent.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(amount, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint, letterSpacing = (-0.1).sp)
                Text(label, fontSize = 11.sp, color = MMGrayText)
            }
        }
    }
}

@Composable
private fun CashMonthHeader(month: LocalDate, income: Money, spend: Money) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMM yyyy")).uppercase(),
            color = CashColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
        Text("+ ${MoneyFormat.rupeesNoDecimals(income)}", color = MMGreenIncome, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("\u2212 ${MoneyFormat.rupeesNoDecimals(spend)}", color = MMRedExpense, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = 0.dp,
        backgroundColor = MMCashForward
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = CashColors.accent.copy(alpha = 0.15f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowUpward, null, tint = CashColors.accent, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Forward Cash Balance", color = CashColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Cash", color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(MoneyFormat.rupeesNoDecimals(amount.abs()), color = CashColors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyCashState() {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = CashColors.accent.copy(alpha = 0.1f), modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Wallet, null, tint = CashColors.accent, modifier = Modifier.size(32.dp))
            }
        }
        Text("No cash transactions for this month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text("Add a cash transaction or import your statement to see it here.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun CashTxnRow(txn: TransactionEntity, onTransactionClick: (Long) -> Unit = {}) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val isIncome = amount.minorUnits > 0L
    val title = txn.merchantReceiverSender?.takeIf { it.isNotBlank() } ?: txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Unknown transaction"
    val category = txn.rawCategoryName?.takeIf { it.isNotBlank() } ?: "Cash"
    val date = DateTimeFormatter.ofPattern("dd MMM").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata")))
    val accent = if (isIncome) CashColors.accent else MMAmberDue

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null) { onTransactionClick(txn.id) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.Wallet,
                        null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(category, color = MMGrayText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp).widthIn(min = 86.dp, max = 132.dp)) {
                Text(
                    MoneyFormat.rupeesNoDecimals(amount.abs()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) MMGreenIncome else MaterialTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.1).sp
                )
                Text(date, color = MMGrayText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
