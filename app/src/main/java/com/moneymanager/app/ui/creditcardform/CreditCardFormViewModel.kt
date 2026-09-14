package com.moneymanager.app.ui.creditcardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.runCatching
import timber.log.Timber
import javax.inject.Inject

/**
 * The PDF's "Credit Card Account" form uses full date pickers for DUE DATE and START DATE.
 * This is simplified to a day-of-month number (1-31) - a reasonable Phase 1 reduction since
 * the underlying billing-cycle math only needs the day, not a specific calendar date; a full
 * date-picker dialog can replace this input later without touching the data model.
 */
data class CreditCardFormState(
    val editingAccountId: Long? = null,
    val billerName: String = "",
    val cardNumber: String = "",
    val nickname: String = "",
    val outstanding: String = "",
    val creditLimit: String = "",
    val billingCycleStartDay: String = "1",
    val billAmount: String = "",
    val autoPay: Boolean = false,
    val inactive: Boolean = false,
    val isBusiness: Boolean = false,
    val autoGenerateBills: Boolean = true,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreditCardFormViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditCardFormState())
    val ui: StateFlow<CreditCardFormState> = _ui

    fun loadForEdit(accountId: Long) {
        viewModelScope.launch {
            val a = accountDao.findById(accountId) ?: return@launch
            _ui.value = CreditCardFormState(
                editingAccountId = a.id,
                billerName = a.institutionName,
                cardNumber = a.sourceAccountId,
                nickname = a.nickname,
                outstanding = a.outstandingMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                creditLimit = a.creditLimitMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                billingCycleStartDay = (a.billingCycleStartDay ?: 1).toString(),
                billAmount = a.billAmountMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                autoPay = a.autoPay,
                inactive = !a.active,
                isBusiness = a.businessPersonal == BusinessPersonal.BUSINESS
            )
        }
    }

    fun onField(update: CreditCardFormState.() -> CreditCardFormState) {
        _ui.value = _ui.value.update()
    }

    fun save() {
        val s = _ui.value
        if (s.billerName.isBlank() || s.cardNumber.isBlank()) {
            _ui.value = s.copy(error = "Biller name and card number are required"); return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val outstanding: Long = runCatching { BigDecimal(s.outstanding.trim()) }.getOrNull()?.let { Money.fromRupees(it).minorUnits } ?: 0L
                val limit: Long? = runCatching { BigDecimal(s.creditLimit.trim()) }.getOrNull()?.let { Money.fromRupees(it).minorUnits }
                val available: Long? = limit?.let { it - outstanding }

                if (s.editingAccountId != null) {
                    val existing = accountDao.findById(s.editingAccountId) ?: return@launch
                    accountDao.update(
                        existing.copy(
                            institutionName = s.billerName,
                            sourceAccountId = s.cardNumber,
                            nickname = s.nickname.ifBlank { s.billerName },
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = s.billingCycleStartDay.toIntOrNull(),
                            billAmountMinorUnits = runCatching { BigDecimal(s.billAmount.trim()) }.getOrNull()?.let { Money.fromRupees(it).minorUnits },
                            autoPay = s.autoPay,
                            active = !s.inactive,
                            businessPersonal = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                            updatedAtEpochMillis = now
                        )
                    )
                } else {
                    accountDao.insert(
                        AccountEntity(
                            sourceAccountId = s.cardNumber,
                            institutionName = s.billerName,
                            nickname = s.nickname.ifBlank { s.billerName },
                            accountType = AccountType.CREDIT_CARD,
                            businessPersonal = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                            openingBalanceMinorUnits = 0,
                            currentBalanceMinorUnits = 0,
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = s.billingCycleStartDay.toIntOrNull(),
                            billAmountMinorUnits = runCatching { BigDecimal(s.billAmount.trim()) }.getOrNull()?.let { Money.fromRupees(it).minorUnits },
                            autoPay = s.autoPay,
                            active = !s.inactive,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now
                        )
                    )
                }
                _ui.value = _ui.value.copy(saved = true)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save credit card")
                _ui.value = _ui.value.copy(error = "Failed to save: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
