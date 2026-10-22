package com.moneymanager.app.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class TransferUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amountText: String = "",
    val notes: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    accountDao: AccountDao,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(TransferUiState())
    val ui: StateFlow<TransferUiState> = _ui

    init {
        viewModelScope.launch {
            accountDao.observeActive().collect { accounts ->
                _ui.value = _ui.value.copy(
                    accounts = accounts,
                    fromAccountId = _ui.value.fromAccountId ?: accounts.firstOrNull()?.id,
                    toAccountId = _ui.value.toAccountId ?: accounts.getOrNull(1)?.id
                )
            }
        }
    }

    /**
     * Pre-selects the destination, e.g. when the user taps "Pay this card". The source is
     * chosen as the first account that is not the destination, so the two sides can never
     * start out identical.
     */
    fun preselectDestination(accountId: Long) {
        val current = _ui.value
        if (current.toAccountId == accountId) return
        val from = current.fromAccountId?.takeIf { it != accountId }
            ?: current.accounts.firstOrNull { it.id != accountId }?.id
        _ui.value = current.copy(toAccountId = accountId, fromAccountId = from)
    }

    fun onFromSelected(id: Long) { _ui.value = _ui.value.copy(fromAccountId = id) }
    fun onToSelected(id: Long) { _ui.value = _ui.value.copy(toAccountId = id) }
    fun onAmountChange(text: String) { _ui.value = _ui.value.copy(amountText = text) }
    fun onNotesChange(text: String) { _ui.value = _ui.value.copy(notes = text) }

    fun save() {
        val s = _ui.value
        // A rapid double tap must not create two transfer pairs. Ignore every submit once one is
        // in flight or already completed.
        if (s.saving || s.saved) return
        val amount = runCatching { BigDecimal(s.amountText.trim()) }.getOrNull()
        if (amount == null || amount.signum() <= 0) {
            _ui.value = s.copy(error = "Enter a valid amount"); return
        }
        val from = s.fromAccountId
        val to = s.toAccountId
        if (from == null || to == null) {
            _ui.value = s.copy(error = "Select both accounts"); return
        }
        if (from == to) {
            _ui.value = s.copy(error = "Source and destination must be different accounts"); return
        }
        _ui.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                transactionRepository.createTransfer(
                    occurredAtEpochMillis = System.currentTimeMillis(),
                    fromAccountId = from,
                    toAccountId = to,
                    amount = Money.fromRupees(amount),
                    notes = s.notes.ifBlank { null }
                )
            }.onSuccess {
                _ui.value = _ui.value.copy(saved = true, saving = false)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    saving = false,
                    error = e.message ?: "Could not save the transfer"
                )
            }
        }
    }
}
