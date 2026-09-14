package com.moneymanager.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class BankLinkingState(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)

@HiltViewModel
class BankLinkingViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val backupManager: BackupManager
) : ViewModel() {
    private val _state = MutableStateFlow(BankLinkingState())
    val state: StateFlow<BankLinkingState> = _state

    fun save(bankName: String, accountLast4: String, debitCardLast4: String, isBusiness: Boolean) {
        val accountSuffix = accountLast4.filter(Char::isDigit).takeLast(4)
        if (accountSuffix.length < 4) {
            _state.value = _state.value.copy(error = "Enter the last 4 digits of the account number")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val accountSourceId = "${bankName.take(4).uppercase()}-$accountSuffix"
                val existing = accountDao.findBySourceAccountId(accountSourceId)
                val parentId = existing?.id ?: accountDao.insert(
                    AccountEntity(
                        sourceAccountId = accountSourceId,
                        institutionName = bankName,
                        nickname = bankName,
                        accountType = AccountType.BANK,
                        businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                        openingBalanceMinorUnits = 0L,
                        currentBalanceMinorUnits = 0L,
                        active = true,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )

                if (existing != null) {
                    accountDao.update(existing.copy(
                        institutionName = bankName,
                        businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                        active = true,
                        updatedAtEpochMillis = now
                    ))
                }

                val debitSuffix = debitCardLast4.filter(Char::isDigit).takeLast(4)
                if (debitSuffix.length == 4) {
                    val debitSourceId = "${bankName.take(4).uppercase()}-DC-$debitSuffix"
                    if (accountDao.findBySourceAccountId(debitSourceId) == null) {
                        accountDao.insert(
                            AccountEntity(
                                sourceAccountId = debitSourceId,
                                institutionName = bankName,
                                nickname = "$bankName Debit Card",
                                accountType = AccountType.DEBIT_CARD,
                                businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                                openingBalanceMinorUnits = 0L,
                                currentBalanceMinorUnits = 0L,
                                parentAccountId = parentId,
                                active = true,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now
                            )
                        )
                    }
                }
                _state.value = _state.value.copy(saving = false, saved = true)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "Could not save account")
            }
        }
    }
}
