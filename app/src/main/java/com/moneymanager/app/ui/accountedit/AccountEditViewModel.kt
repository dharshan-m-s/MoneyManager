package com.moneymanager.app.ui.accountedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
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
    /** Informational engine-derived balance, shown alongside the editable checkpoint. */
    val calculatedBalance: String = "",
    val manualBalanceEnabled: Boolean = false,
    /** True only after the user actually types a new balance. Merely opening the editor, or
     *  editing the name/status fields, must never replace the existing checkpoint. */
    val balanceDirty: Boolean = false,
    val manualModeDirty: Boolean = false,
    val manualModeWasEnabled: Boolean = false,
    val saving: Boolean = false,
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
    private val backupManager: BackupManager,
    private val database: MoneyManagerDatabase
) : ViewModel() {
    private val _state = MutableStateFlow(AccountEditState())
    val state: StateFlow<AccountEditState> = _state

    fun load(id: Long) {
        viewModelScope.launch {
            val a = accountDao.findById(id) ?: run { _state.value = _state.value.copy(error = "Account not found"); return@launch }
            // The derived (engine) balance is shown next to the editable checkpoint so the user
            // can see how the ledger value differs from their reconciliation. Movement values
            // for cards are the outstanding, but this editor only handles cash-like balances.
            val derived = accountingService.derivedBalancesFor(a).autoBalanceMinorUnits
            _state.value = AccountEditState(
                accountId = a.id,
                nickname = a.nickname,
                institutionName = a.institutionName,
                sourceAccountId = a.sourceAccountId,
                accountType = a.accountType,
                businessPersonal = a.businessPersonal,
                // Show the EFFECTIVE current balance, never the older checkpoint value. The
                // checkpoint is an offset applied to the ledger; the effective balance already
                // includes every movement after (and around) that checkpoint.
                currentBalance = Money(a.currentBalanceMinorUnits).toBigDecimal().stripTrailingZeros().toPlainString(),
                calculatedBalance = Money(derived).toBigDecimal().stripTrailingZeros().toPlainString(),
                manualBalanceEnabled = a.manualBalanceOverrideMinorUnits != null,
                manualModeWasEnabled = a.manualBalanceOverrideMinorUnits != null,
                balanceDirty = false,
                manualModeDirty = false,
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
    fun currentBalance(v: String) { _state.value = _state.value.copy(currentBalance = v, balanceDirty = true) }
    fun manualBalanceEnabled(v: Boolean) {
        _state.value = _state.value.copy(manualBalanceEnabled = v, manualModeDirty = true)
    }
    fun active(v: Boolean) { _state.value = _state.value.copy(active = v) }
    fun hide(v: Boolean) { _state.value = _state.value.copy(hide = v) }
    fun hideTransactions(v: Boolean) { _state.value = _state.value.copy(hideTransactions = v) }
    fun save() {
        viewModelScope.launch {
            val s = _state.value
            if (s.saving) return@launch
            val id = s.accountId ?: return@launch
            val a = accountDao.findById(id) ?: run {
                _state.value = s.copy(error = "Account not found")
                return@launch
            }
            val supportsManualBalance = s.accountType == AccountType.BANK ||
                s.accountType == AccountType.CASH || s.accountType == AccountType.WALLET ||
                s.accountType == AccountType.DEBIT_CARD || s.accountType == AccountType.PREPAID_CARD
            val manualEnabled = supportsManualBalance && s.manualBalanceEnabled

            // Explicitly turning manual mode off is the ONLY action that discards a durable
            // checkpoint. Every other save - a name/institution/visibility edit, or a change of
            // account type - preserves the existing checkpoint columns untouched, so a metadata
            // edit can never silently destroy the user's reconciliation.
            val manualTurnedOff = s.manualModeDirty && !s.manualBalanceEnabled
            val shouldWriteCheckpoint = manualEnabled &&
                (s.balanceDirty || s.manualModeDirty || !s.manualModeWasEnabled)
            val entered = if (shouldWriteCheckpoint) {
                runCatching { Money.fromRupees(java.math.BigDecimal(s.currentBalance.trim())).minorUnits }
                    .getOrElse {
                        _state.value = s.copy(error = "Invalid current balance")
                        return@launch
                    }
            } else null

            val sourceId = s.sourceAccountId.ifBlank { a.sourceAccountId }
            val duplicate = accountDao.findBySourceAccountId(sourceId)
            if (duplicate != null && duplicate.id != a.id) {
                _state.value = s.copy(error = "Another account already uses this Account ID")
                return@launch
            }

            val derived = accountingService.derivedBalancesFor(a).autoBalanceMinorUnits
            val now = System.currentTimeMillis()
            val updated = a.copy(
                nickname = s.nickname.ifBlank { s.institutionName.ifBlank { a.institutionName } },
                institutionName = s.institutionName.ifBlank { a.institutionName },
                sourceAccountId = sourceId,
                accountType = s.accountType,
                businessPersonal = s.businessPersonal,
                // The entered amount is a live reconciliation checkpoint, not an invented
                // opening transaction. It is stored as an offset against the derived ledger
                // balance so later (including backdated) movements apply exactly once.
                manualBalanceOverrideMinorUnits = when {
                    manualTurnedOff -> null
                    shouldWriteCheckpoint -> entered
                    else -> a.manualBalanceOverrideMinorUnits
                },
                manualBalanceOverrideAtEpochMillis = when {
                    manualTurnedOff -> null
                    shouldWriteCheckpoint -> now
                    else -> a.manualBalanceOverrideAtEpochMillis
                },
                manualBalanceAdjustmentMinorUnits = when {
                    manualTurnedOff -> null
                    shouldWriteCheckpoint -> com.moneymanager.app.domain.accounting.AccountingEngine
                        .checkpointAdjustment(entered!!, derived)
                    else -> a.manualBalanceAdjustmentMinorUnits
                },
                active = s.active,
                hide = s.hide,
                hideAccountTxns = s.hideTransactions,
                updatedAtEpochMillis = now
            )
            _state.value = s.copy(saving = true, error = null)
            try {
                // The metadata/checkpoint write and the derived-balance recalculation must commit
                // together: a crash between them would otherwise leave the checkpoint stored but
                // currentBalanceMinorUnits stale until the next recalculation.
                database.withTransaction {
                    accountDao.update(updated)
                    accountingService.recalculateAccounts(setOf(updated.id))
                }
                _state.value = _state.value.copy(saved = true, saving = false, error = null)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saved = false,
                    saving = false,
                    error = "Could not save: ${e.message ?: "unknown error"}"
                )
            }
        }
    }
}
