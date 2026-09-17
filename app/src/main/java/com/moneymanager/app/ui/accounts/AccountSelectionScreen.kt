package com.moneymanager.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.moneymanager.app.domain.model.Money
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmEmptyState
import com.moneymanager.app.ui.components.MmIconBadge
import com.moneymanager.app.ui.components.MmSearchField
import com.moneymanager.app.ui.components.MoneyFormat
import com.moneymanager.app.ui.components.MoneyManagerTopBar
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmSpacing
import com.moneymanager.app.ui.theme.MmType
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
    var query by rememberSaveable { mutableStateOf("") }
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

    Column(Modifier.fillMaxSize().background(MmColors.background)) {
        MoneyManagerTopBar(
            title = title,
            subtitle = "Choose the account used for this transaction",
            onBack = onBack
        )

        if (accounts.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.AccountBalance,
                title = "No accounts yet",
                message = "Add a bank account, card, wallet, investment or loan account first."
            )
            return@Column
        }

        Column(Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.md)) {
            MmSearchField(
                value = query,
                onValueChange = { query = it },
                hint = "Search accounts, cards or wallets"
            )
        }

        if (filteredAccounts.isEmpty()) {
            MmEmptyState(
                icon = Icons.Filled.Search,
                title = "No account matches \"$query\"",
                message = "Try a bank name, nickname or account identifier.",
                actionLabel = "Clear search",
                onAction = { query = "" }
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = MmSpacing.xxl)) {
                grouped.entries.sortedBy { typeOrder(it.key) }.forEach { (type, rows) ->
                    item(key = "header-${type.name}") {
                        Text(
                            accountTypeLabel(type),
                            style = MmType.label,
                            color = MmColors.textSecondary,
                            modifier = Modifier.padding(
                                start = MmSpacing.xs,
                                top = MmSpacing.md,
                                bottom = MmSpacing.sm
                            )
                        )
                    }
                    items(rows, key = { it.id }) { account ->
                        AccountChoiceRow(account, onClick = { onAccountSelected(account.id) })
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
    AccountType.BANK -> "Bank accounts"
    AccountType.DEBIT_CARD -> "Debit cards"
    AccountType.CREDIT_CARD -> "Credit cards"
    AccountType.WALLET -> "Wallets"
    AccountType.PREPAID_CARD -> "Prepaid cards"
    AccountType.INVESTMENT -> "Investments"
    AccountType.LOAN -> "Loans"
    AccountType.UNKNOWN -> "Other accounts"
    AccountType.CASH -> "Cash"
}

@Composable
private fun AccountChoiceRow(account: AccountEntity, onClick: () -> Unit) {
    val title = account.nickname.ifBlank { account.institutionName }.ifBlank { "Unnamed account" }
    val subtitle = listOf(
        account.institutionName.takeIf { it.isNotBlank() && it != title },
        account.sourceAccountId.takeIf { it.isNotBlank() }
    ).filterNotNull().distinct().joinToString(" • ")
    val isCard = account.accountType == AccountType.CREDIT_CARD
    val balance = if (isCard) account.outstandingMinorUnits ?: 0L else account.currentBalanceMinorUnits

    MmCard(
        modifier = Modifier.padding(horizontal = MmSpacing.lg, vertical = MmSpacing.xs),
        onClick = onClick,
        contentPadding = PaddingValues(MmSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconBadge(icon = accountIcon(account.accountType), tint = MmColors.accent, size = 42.dp)
            Spacer(Modifier.width(MmSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle.ifBlank { accountTypeLabel(account.accountType) },
                    style = MmType.caption,
                    color = MmColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MmSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    MoneyFormat.rupeesNoDecimals(Money(balance)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCard) MmColors.expense else MmColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isCard) "Outstanding" else "Balance",
                    style = MmType.caption,
                    color = MmColors.textTertiary,
                    maxLines = 1
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MmColors.textTertiary,
                modifier = Modifier.padding(start = MmSpacing.xs)
            )
        }
    }
}

private fun accountIcon(type: AccountType): ImageVector = when (type) {
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
