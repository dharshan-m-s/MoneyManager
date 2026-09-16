package com.moneymanager.app.ui.accountdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.moneymanager.app.ui.components.DashboardFab
import com.moneymanager.app.ui.components.AccountsFab
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.theme.MMGrayDivider
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMGreenIncome
import com.moneymanager.app.ui.theme.MMRedExpense
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onEditAccount: (Long) -> Unit = {},
    onAddAccountIncome: () -> Unit = {},
    onAddAccountSpend: () -> Unit = {},
    onAddCashIncome: () -> Unit = {},
    onAddCashSpend: () -> Unit = {},
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by remember(accountId) { viewModel.stateFor(accountId) }.collectAsState()
    val account = state.account

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material.FloatingActionButton(
                    onClick = onAddAccountIncome,
                    backgroundColor = MMGreenIncome,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Icon(Icons.Filled.CallReceived, contentDescription = "Add income", tint = MMWhite)
                }
                DashboardFab(onAddTransaction = onAddAccountSpend)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(MMGreenDark).padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MMWhite)
                    }
                    Text(
                        account?.nickname ?: "Account",
                        color = MMWhite, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (account != null) {
                        IconButton(onClick = { onEditAccount(account.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit account", tint = MMWhite)
                        }
                    }
                }
                if (account != null) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(account.sourceAccountId, color = MMWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                MoneyFormat.rupeesNoDecimals(Money(account.currentBalanceMinorUnits)),
                                color = MMWhite,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh balance",
                                tint = MMWhite,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (account.manualBalanceOverrideMinorUnits != null) {
                            Text(
                                "Manual balance checkpoint",
                                color = MMWhite.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                        if (account.lastReportedAtEpochMillis != null && account.lastReportedBalanceMinorUnits != null) {
                            Text(
                                "Last Reported (" + DateTimeFormatter.ofPattern("dd MMM yyyy").format(
                                    Instant.ofEpochMilli(account.lastReportedAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
                                ) + "): " + MoneyFormat.rupeesWithDecimals(Money(account.lastReportedBalanceMinorUnits)),
                                color = MMWhite.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (state.monthGroups.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No transactions for this account yet.", color = MMGrayText, fontSize = 13.sp)
                    Text("Tap + to add one.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                LazyColumn {
                    state.monthGroups.forEach { group ->
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(group.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MMGreenDark)
                                Row {
                                    Text("+${MoneyFormat.rupeesNoDecimals(Money(group.income))}", color = MMGreenIncome, fontSize = 12.sp)
                                    Text(
                                        "  -${MoneyFormat.rupeesNoDecimals(Money(group.expense))}",
                                        color = MMRedExpense,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        if (group.transactions.isEmpty()) {
                            item {
                                Text(
                                    "No transactions for this period.",
                                    fontSize = 12.sp,
                                    color = MMGrayText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(group.transactions) { txn -> AccountTxnRow(txn) }
                        }
                    }
                    if (state.canLoadMore) {
                        item {
                            androidx.compose.material.TextButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load older transactions")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTxnRow(txn: TransactionEntity) {
    val amount = Money(txn.creditMinorUnits - txn.debitMinorUnits)
    val isTransfer = txn.txnSubType == TxnSubType.TRANSFER_IN || txn.txnSubType == TxnSubType.TRANSFER_OUT
    val dateStr = DateTimeFormatter.ofPattern("dd MMM").format(
        Instant.ofEpochMilli(txn.occurredAtEpochMillis).atZone(ZoneId.of("Asia/Kolkata"))
    )
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTransfer) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Transfer", tint = MMGrayText, modifier = Modifier.padding(end = 8.dp))
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(txn.merchantReceiverSender ?: txn.rawCategoryName ?: "Transaction", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(txn.rawCategoryName ?: "", fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 92.dp, max = 118.dp)) {
                Text(
                    MoneyFormat.rupeesNoDecimals(amount.abs()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (amount.isNegative) MMRedExpense else MMGreenIncome
                )
                Text(dateStr, fontSize = 11.sp, color = MMGrayText)
            }
        }
        Divider(color = MMGrayDivider)
    }
}
