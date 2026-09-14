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

enum class AccountsTab(val label: String, val types: Set<AccountType>) {
    BANK("BANK A/C", setOf(AccountType.BANK)),
    CREDIT_CARDS("CREDIT CARDS", setOf(AccountType.CREDIT_CARD)),
    WALLETS("WALLETS", setOf(AccountType.WALLET)),
    LOANS("LOANS & DUES", setOf(AccountType.LOAN))
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountDao: AccountDao
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
