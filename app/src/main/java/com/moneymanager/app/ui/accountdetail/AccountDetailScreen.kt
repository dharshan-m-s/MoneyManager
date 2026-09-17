package com.moneymanager.app.ui.accountdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zone = ZoneId.of("Asia/Kolkata")
private val CardBlue = Color(0xFF07579A)

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onEditAccount: (Long) -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by remember(accountId) { viewModel.stateFor(accountId) }.collectAsState()
    val account = state.account

    if (account?.accountType == AccountType.CREDIT_CARD) {
        CreditCardDetailContent(
            account = account,
            linkedBill = state.linkedBill,
            transactions = state.monthGroups.flatMap { it.transactions }.distinctBy { it.id },
            canLoadMore = state.canLoadMore,
            onLoadMore = viewModel::loadMore,
            onBack = onBack,
            onEdit = { onEditAccount(account.id) },
            onTransactionClick = onTransactionClick,
            onAddSpend = onAddAccountSpend
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccountSpend, backgroundColor = MMGreen) {
                Icon(Icons.Filled.Add, "Add transaction", tint = MMWhite)
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            Row(Modifier.fillMaxWidth().background(MMGreenDark).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
                Text(account?.nickname ?: "Account", color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                account?.let { IconButton(onClick = { onEditAccount(it.id) }) { Icon(Icons.Filled.Edit, "Edit account", tint = MMWhite) } }
            }
            if (account == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Account not found", color = MMGrayText) }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().background(MMGreenDark).padding(horizontal = 22.dp, vertical = 12.dp)) {
                        Text(account.sourceAccountId, color = MMWhite.copy(alpha = .70f), fontSize = 11.sp)
                        Text(MoneyFormat.rupeesNoDecimals(Money(account.currentBalanceMinorUnits)), color = MMWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        account.lastReportedAtEpochMillis?.let { at ->
                            account.lastReportedBalanceMinorUnits?.let { value ->
                                Text("Last Reported (${DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(at).atZone(zone))}): ${MoneyFormat.rupeesWithDecimals(Money(value))}", color = MMWhite.copy(alpha = .72f), fontSize = 11.sp)
                            }
                        }
                    }
                    LazyColumn {
                        state.monthGroups.forEach { group ->
                            item {
                                Text(group.label, color = MMGreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 10.dp))
                            }
                            items(group.transactions, key = { it.id }) { txn ->
                                GenericTransactionRow(txn, onTransactionClick)
                            }
                        }
                        if (state.canLoadMore) item { TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Load older transactions") } }
                    }
                }
            }
        }
    }
}

private enum class CardTab { VIEW, MONTHLY, BILLING }

@Composable
private fun CreditCardDetailContent(
    account: AccountEntity,
    linkedBill: BillEntity?,
    transactions: List<TransactionEntity>,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onAddSpend: () -> Unit
) {
    var tab by remember { mutableStateOf(CardTab.VIEW) }
    val now = LocalDate.now(zone)
    val cycleStartDay = (account.billingCycleStartDay ?: 1).coerceIn(1, 31)
    val cycleStart = if (now.dayOfMonth >= cycleStartDay) {
        now.withDayOfMonth(minOf(cycleStartDay, now.lengthOfMonth()))
    } else {
        val previous = now.minusMonths(1)
        previous.withDayOfMonth(minOf(cycleStartDay, previous.lengthOfMonth()))
    }
    val cycleEnd = cycleStart.plusMonths(1).minusDays(1)
    val monthStart = now.withDayOfMonth(1)
    val monthEnd = now.plusMonths(1).withDayOfMonth(1).minusDays(1)
    val visible = when (tab) {
        CardTab.VIEW -> transactions
        CardTab.MONTHLY -> transactions.filter { dateOf(it) in monthStart..monthEnd }
        CardTab.BILLING -> transactions.filter { dateOf(it) in cycleStart..cycleEnd }
    }.sortedByDescending { it.occurredAtEpochMillis }

    Scaffold(
        backgroundColor = MaterialTheme.colors.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSpend, backgroundColor = MMGreen, modifier = Modifier.navigationBarsPadding()) {
                Icon(Icons.Filled.Add, "Add transaction", tint = MMWhite)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().background(CardBlue).padding(bottom = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MMWhite) }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(account.nickname, color = MMWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("A/c – ${account.sourceAccountId.removePrefix("x")}", color = MMWhite.copy(alpha = .70f), fontSize = 11.sp)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit card", tint = MMWhite) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, "More", tint = MMWhite) }
                }
                Column(Modifier.padding(horizontal = 28.dp)) {
                    Text(MoneyFormat.rupeesNoDecimals(Money(account.outstandingMinorUnits ?: 0L)), color = MMWhite, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("Outstanding", color = MMWhite.copy(alpha = .72f), fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    val reported = account.lastReportedBalanceMinorUnits ?: account.outstandingMinorUnits
                    val reportedAt = account.lastReportedAtEpochMillis
                    if (reported != null && reportedAt != null) {
                        Text("Last Reported (${DateTimeFormatter.ofPattern("dd MMM yyyy").format(Instant.ofEpochMilli(reportedAt).atZone(zone))}): ${MoneyFormat.rupeesWithDecimals(Money(reported))}", color = MMWhite.copy(alpha = .76f), fontSize = 11.sp)
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Limit ${MoneyFormat.rupeesNoDecimals(Money(account.creditLimitMinorUnits ?: 0L))}", color = MMWhite.copy(alpha = .80f), fontSize = 11.sp)
                        Text("  •  Available ${MoneyFormat.rupeesNoDecimals(Money(account.availableLimitMinorUnits ?: ((account.creditLimitMinorUnits ?: 0L) - (account.outstandingMinorUnits ?: 0L))))}", color = MMWhite.copy(alpha = .80f), fontSize = 11.sp)
                    }
                }
            }

            CardTabs(tab) { tab = it }

            Row(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface).padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when (tab) {
                        CardTab.VIEW -> "All transactions"
                        CardTab.MONTHLY -> now.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                        CardTab.BILLING -> "${cycleStart.format(DateTimeFormatter.ofPattern("dd MMM"))} – ${cycleEnd.format(DateTimeFormatter.ofPattern("dd MMM"))}"
                    },
                    fontSize = 12.sp, color = MMGreenDark, fontWeight = FontWeight.Bold
                )
                val credit = visible.sumOf { it.creditMinorUnits }
                val debit = visible.sumOf { it.debitMinorUnits }
                Row {
                    Text("+ ${MoneyFormat.rupeesNoDecimals(Money(credit))}", color = MMGreenIncome, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("  − ${MoneyFormat.rupeesNoDecimals(Money(debit))}", color = MMRedExpense, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Divider(color = MMGrayDivider)

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transactions in this view", color = MMGrayText, fontSize = 13.sp) }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                    items(visible, key = { it.id }) { txn ->
                        GenericTransactionRow(txn, onTransactionClick)
                    }
                    if (canLoadMore) item { TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("Load older transactions") } }
                }
            }
        }
    }
}

@Composable
private fun CardTabs(selected: CardTab, onSelected: (CardTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colors.surface)) {
        listOf(CardTab.VIEW to "View", CardTab.MONTHLY to "Monthly", CardTab.BILLING to "Billing Cycle").forEach { (tab, label) ->
            val active = selected == tab
            Text(
                label,
                color = if (active) MMGreen else MaterialTheme.colors.onSurface.copy(alpha = .72f),
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f).clickable { onSelected(tab) }.background(if (active) MaterialTheme.colors.onSurface.copy(alpha = .06f) else Color.Transparent).padding(vertical = 14.dp)
            )
        }
    }
    Divider(color = MMGrayDivider)
}

@Composable
private fun GenericTransactionRow(txn: TransactionEntity, onClick: (Long) -> Unit) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val isTransfer = txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
    val title = txn.merchantReceiverSender?.takeIf { it.isNotBlank() } ?: txn.rawCategoryName ?: "Transaction"
    val subtitle = listOfNotNull(txn.rawCategoryName, txn.rawPaymentType).distinct().joinToString(" • ")
    val date = DateTimeFormatter.ofPattern("dd MMM").format(Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone))
    Row(
        Modifier.fillMaxWidth().clickable { onClick(txn.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = (if (amount.isNegative) MMRedExpense else MMGreenIncome).copy(alpha = .12f), modifier = Modifier.size(42.dp)) {
            Icon(if (isTransfer) Icons.Filled.SwapHoriz else Icons.Filled.CreditCard, null, tint = if (amount.isNegative) MMRedExpense else MMGreenIncome, modifier = Modifier.padding(11.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle.ifBlank { "Transaction" }, fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 122.dp)) {
            Text(MoneyFormat.rupeesNoDecimals(amount.abs()), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (amount.isNegative) MMRedExpense else MMGreenIncome)
            Text(date, fontSize = 10.sp, color = MMGrayText)
        }
    }
    Divider(color = MMGrayDivider)
}

private fun dateOf(txn: TransactionEntity): LocalDate = Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(zone).toLocalDate()
