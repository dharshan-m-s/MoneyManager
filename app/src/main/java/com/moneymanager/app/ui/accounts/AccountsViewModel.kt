package com.moneymanager.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One tab per supported [AccountType] (UNKNOWN is the importer's fallback bucket, not a type a
 * user can choose), plus an [ALL] overview. The Accounts screen shows only a compact subset of
 * these as chips; the full set lives behind the All account types browser.
 */
enum class AccountsTab(val label: String, val types: Set<AccountType>) {
    BANK("Bank accounts", setOf(AccountType.BANK)),
    DEBIT_CARDS("Debit cards", setOf(AccountType.DEBIT_CARD)),
    CREDIT_CARDS("Credit cards", setOf(AccountType.CREDIT_CARD)),
    WALLETS("Wallets", setOf(AccountType.WALLET)),
    PREPAID_CARDS("Prepaid cards", setOf(AccountType.PREPAID_CARD)),
    CASH("Cash", setOf(AccountType.CASH)),
    INVESTMENTS("Investments", setOf(AccountType.INVESTMENT)),
    LOANS("Loans and dues", setOf(AccountType.LOAN)),
    ALL("All account types", AccountType.entries.filter { it != AccountType.UNKNOWN }.toSet());

    companion object {
        /** Tabs offered directly on the Accounts screen. */
        val primary: List<AccountsTab> = listOf(
            AccountsTab.BANK,
            AccountsTab.CREDIT_CARDS,
            AccountsTab.WALLETS,
            AccountsTab.PREPAID_CARDS
        )

        /** The tab that owns an account type. Exactly one per supported type. */
        fun forType(type: AccountType): AccountsTab =
            entries.firstOrNull { it != ALL && type in it.types } ?: ALL
    }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountDao: AccountDao
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
