package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.ui.theme.MMGreen
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MMGrayText
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AccountSelectionViewModel @Inject constructor(
    accountDao: AccountDao
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeActive()
        .map { accounts ->
            accounts
                .filter { it.accountType != AccountType.CASH && !it.deleted && !it.hide }
                .sortedWith(
                    compareBy<AccountEntity> { accountTypeOrder(it.accountType) }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.nickname.ifBlank { it.institutionName } }
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun accountTypeOrder(type: AccountType): Int = when (type) {
        AccountType.BANK -> 0
        AccountType.DEBIT_CARD -> 1
        AccountType.CREDIT_CARD -> 2
        AccountType.WALLET -> 3
        AccountType.PREPAID_CARD -> 4
        AccountType.INVESTMENT -> 5
        AccountType.LOAN -> 6
        AccountType.UNKNOWN -> 7
        AccountType.CASH -> 8
    }
}

@Composable
fun AccountSelectionScreen(
    title: String,
    onBack: () -> Unit,
    onAccountSelected: (Long) -> Unit,
    viewModel: AccountSelectionViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var query by remember { mutableStateOf("") }
    val filteredAccounts = remember(accounts, query) {
        val q = query.trim()
        accounts.filter { account ->
            q.isBlank() || listOf(
                account.nickname,
                account.institutionName,
                account.sourceAccountId,
                account.accountType.name
            ).joinToString(" ").contains(q, ignoreCase = true)
        }
    }
    val grouped = filteredAccounts.groupBy { it.accountType }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = "Choose the account used for this transaction",
            onBack = onBack
        )

        if (accounts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                elevation = 2.dp
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("No accounts available", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Add a bank account, card, wallet, investment or loan account first.",
                        color = MMGrayText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search accounts, cards or wallets") },
                    shape = RoundedCornerShape(14.dp)
                )

                if (filteredAccounts.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MMGrayText, modifier = Modifier.size(34.dp))
                        Text("No account matches", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        Text("Try a bank name, nickname or account/card identifier.", color = MMGrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                    ) {
                        grouped.entries.sortedBy { typeOrder(it.key) }.forEach { (type, rows) ->
                            item(key = "header-${type.name}") {
                                Text(
                                    accountTypeLabel(type),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MMGreenDark,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                            items(rows, key = { it.id }) { account ->
                                AccountChoiceRow(account, onClick = { onAccountSelected(account.id) })
                            }
                            item(key = "spacer-${type.name}") { Spacer(Modifier.size(4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun typeOrder(type: AccountType): Int = when (type) {
    AccountType.BANK -> 0
    AccountType.DEBIT_CARD -> 1
    AccountType.CREDIT_CARD -> 2
    AccountType.WALLET -> 3
    AccountType.PREPAID_CARD -> 4
    AccountType.INVESTMENT -> 5
    AccountType.LOAN -> 6
    AccountType.UNKNOWN -> 7
    AccountType.CASH -> 8
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.BANK -> "BANK ACCOUNTS"
    AccountType.DEBIT_CARD -> "DEBIT CARDS"
    AccountType.CREDIT_CARD -> "CREDIT CARDS"
    AccountType.WALLET -> "WALLETS"
    AccountType.PREPAID_CARD -> "PREPAID CARDS"
    AccountType.INVESTMENT -> "INVESTMENTS"
    AccountType.LOAN -> "LOANS"
    AccountType.UNKNOWN -> "OTHER ACCOUNTS"
    AccountType.CASH -> "CASH"
}

@Composable
private fun AccountChoiceRow(account: AccountEntity, onClick: () -> Unit) {
    val title = account.nickname.ifBlank { account.institutionName }.ifBlank { "Unnamed account" }
    val subtitle = listOf(
        account.institutionName.takeIf { it.isNotBlank() },
        account.sourceAccountId.takeIf { it.isNotBlank() }
    ).filterNotNull().distinct().joinToString(" • ")
    val balance = if (account.accountType == AccountType.CREDIT_CARD) {
        account.outstandingMinorUnits ?: 0L
    } else {
        account.currentBalanceMinorUnits
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, com.moneymanager.app.ui.theme.MMOutline.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.small,
                elevation = 0.dp,
                backgroundColor = MMGreen.copy(alpha = .10f)
            ) {
                Icon(
                    accountIcon(account.accountType),
                    contentDescription = null,
                    tint = MMGreen,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(Modifier.weight(1f).padding(start = 13.dp, end = 10.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank() && subtitle != title) {
                    Text(subtitle, fontSize = 11.sp, color = MMGrayText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    accountTypeLabel(account.accountType).lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    color = MMGrayText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(balance)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (account.accountType == AccountType.CREDIT_CARD) com.moneymanager.app.ui.theme.MMRedExpense else MaterialTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = "Select account", tint = MMGrayText, modifier = Modifier.size(19.dp))
            }
        }
    }
}

private fun accountIcon(type: AccountType) = when (type) {
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
    AccountType.DEBIT_CARD -> Icons.Filled.CreditCard
    AccountType.WALLET -> Icons.Filled.AccountBalanceWallet
    AccountType.PREPAID_CARD -> Icons.Filled.Wallet
    AccountType.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
    AccountType.LOAN -> Icons.Filled.CreditCard
    AccountType.UNKNOWN -> Icons.Filled.AccountBalanceWallet
    AccountType.CASH -> Icons.Filled.AccountBalanceWallet
}
