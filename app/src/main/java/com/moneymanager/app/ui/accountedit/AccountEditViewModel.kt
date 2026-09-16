package com.moneymanager.app.ui.accountedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountEditState(
    val accountId: Long? = null,
    val nickname: String = "",
    val institutionName: String = "",
    val sourceAccountId: String = "",
    val accountType: AccountType = AccountType.BANK,
    val businessPersonal: BusinessPersonal = BusinessPersonal.PERSONAL,
    val currentBalance: String = "",
    val manualBalanceEnabled: Boolean = false,
    val active: Boolean = true,
    val hide: Boolean = false,
    val hideTransactions: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val accountingService: AccountingService,
    private val backupManager: BackupManager
) : ViewModel() {
    private val _state = MutableStateFlow(AccountEditState())
    val state: StateFlow<AccountEditState> = _state

    fun load(id: Long) {
        viewModelScope.launch {
            val a = accountDao.findById(id) ?: run { _state.value = _state.value.copy(error = "Account not found"); return@launch }
            _state.value = AccountEditState(
                accountId = a.id,
                nickname = a.nickname,
                institutionName = a.institutionName,
                sourceAccountId = a.sourceAccountId,
                accountType = a.accountType,
                businessPersonal = a.businessPersonal,
                currentBalance = Money(a.manualBalanceOverrideMinorUnits ?: a.currentBalanceMinorUnits).toBigDecimal().toPlainString(),
                manualBalanceEnabled = a.manualBalanceOverrideMinorUnits != null,
                active = a.active,
                hide = a.hide,
                hideTransactions = a.hideAccountTxns
            )
        }
    }
    fun nickname(v: String) { _state.value = _state.value.copy(nickname = v) }
    fun institutionName(v: String) { _state.value = _state.value.copy(institutionName = v) }
    fun sourceAccountId(v: String) { _state.value = _state.value.copy(sourceAccountId = v) }
    fun accountType(v: AccountType) { _state.value = _state.value.copy(accountType = v) }
    fun businessPersonal(v: BusinessPersonal) { _state.value = _state.value.copy(businessPersonal = v) }
    fun currentBalance(v: String) { _state.value = _state.value.copy(currentBalance = v) }
    fun manualBalanceEnabled(v: Boolean) { _state.value = _state.value.copy(manualBalanceEnabled = v) }
    fun active(v: Boolean) { _state.value = _state.value.copy(active = v) }
    fun hide(v: Boolean) { _state.value = _state.value.copy(hide = v) }
    fun hideTransactions(v: Boolean) { _state.value = _state.value.copy(hideTransactions = v) }
    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val id = s.accountId ?: return@launch
            val a = accountDao.findById(id) ?: return@launch
            val supportsManualBalance = s.accountType == AccountType.BANK ||
                s.accountType == AccountType.CASH || s.accountType == AccountType.WALLET ||
                s.accountType == AccountType.DEBIT_CARD || s.accountType == AccountType.PREPAID_CARD
            val current = if (supportsManualBalance && s.manualBalanceEnabled) {
                runCatching { Money.fromRupees(java.math.BigDecimal(s.currentBalance.trim())).minorUnits }
                    .getOrElse {
                        _state.value = s.copy(error = "Invalid current balance")
                        return@launch
                    }
            } else a.currentBalanceMinorUnits
            val sourceId = s.sourceAccountId.ifBlank { a.sourceAccountId }
            val duplicate = accountDao.findBySourceAccountId(sourceId)
            if (duplicate != null && duplicate.id != a.id) {
                _state.value = s.copy(error = "Another account already uses this Account ID")
                return@launch
            }
            val updated = a.copy(
                nickname = s.nickname.ifBlank { a.institutionName },
                institutionName = s.institutionName.ifBlank { a.institutionName },
                sourceAccountId = sourceId,
                accountType = s.accountType,
                businessPersonal = s.businessPersonal,
                // Keep the historical opening amount as metadata. The value the user enters here
                // is a live balance checkpoint, not an invented opening transaction.
                manualBalanceOverrideMinorUnits = if (supportsManualBalance && s.manualBalanceEnabled) current else null,
                manualBalanceOverrideAtEpochMillis = if (supportsManualBalance && s.manualBalanceEnabled) System.currentTimeMillis() else null,
                active = s.active,
                hide = s.hide,
                hideAccountTxns = s.hideTransactions,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
            accountDao.update(updated)
            accountingService.recalculateAccounts(setOf(updated.id))
            _state.value = s.copy(saved = true, error = null)
            backupManager.scheduleAfterWrite()
        }
    }
}
