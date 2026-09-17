package com.moneymanager.app.ui.creditcardform

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CreditCardFormState(
    val editingAccountId: Long? = null,
    val loaded: Boolean = true,
    val billerName: String = "",
    val cardNumber: String = "",
    val nickname: String = "",
    val outstanding: String = "",
    val creditLimit: String = "",
    val billingCycleStartDay: Int = 1,
    val billingCycleSet: Boolean = true,
    val dueDateEpochMillis: Long? = null,
    val billAmount: String = "",
    val autoPay: Boolean = false,
    val inactive: Boolean = false,
    val hideAccount: Boolean = false,
    val isBusiness: Boolean = false,
    val autoGenerateBills: Boolean = true,
    val billerNameError: String? = null,
    val cardNumberError: String? = null,
    val outstandingError: String? = null,
    val creditLimitError: String? = null,
    val billAmountError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
) {
    val maskedNumber: String
        get() {
            val digits = cardNumber.filter { it.isDigit() }
            val last4 = when {
                digits.length >= 4 -> digits.takeLast(4)
                digits.isNotEmpty() -> digits
                else -> null
            }
            return last4?.let { "•••• $it" } ?: "No number yet"
        }
}

private val dueDateZone = ZoneId.of("Asia/Kolkata")
private val formDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@HiltViewModel
class CreditCardFormViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val billDao: BillDao,
    private val transactionRepository: TransactionRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditCardFormState())
    val ui: StateFlow<CreditCardFormState> = _ui

    fun loadForEdit(accountId: Long) {
        viewModelScope.launch {
            val account = accountDao.findById(accountId) ?: return@launch
            _ui.value = CreditCardFormState(
                editingAccountId = account.id,
                billerName = account.institutionName,
                cardNumber = account.sourceAccountId,
                nickname = account.nickname,
                outstanding = account.outstandingMinorUnits?.let { plain(Money(it)) }.orEmpty(),
                creditLimit = account.creditLimitMinorUnits?.let { plain(Money(it)) }.orEmpty(),
                billingCycleStartDay = account.billingCycleStartDay ?: 1,
                billingCycleSet = account.billingCycleStartDay != null,
                dueDateEpochMillis = account.dueDate,
                billAmount = account.billAmountMinorUnits?.let { plain(Money(it)) }.orEmpty(),
                autoPay = account.autoPay,
                inactive = !account.active,
                hideAccount = account.hide,
                isBusiness = account.businessPersonal == BusinessPersonal.BUSINESS
            )
        }
    }

    fun onField(update: CreditCardFormState.() -> CreditCardFormState) {
        val previous = _ui.value
        val next = previous.update()
        _ui.value = next.copy(
            billerNameError = next.billerNameError.takeIf { next.billerName == previous.billerName },
            cardNumberError = next.cardNumberError.takeIf { next.cardNumber == previous.cardNumber },
            outstandingError = next.outstandingError.takeIf { next.outstanding == previous.outstanding },
            creditLimitError = next.creditLimitError.takeIf { next.creditLimit == previous.creditLimit },
            billAmountError = next.billAmountError.takeIf { next.billAmount == previous.billAmount },
            error = null
        )
    }

    fun onBillAmountChange(raw: String) = onField { copy(billAmount = sanitiseAmount(raw)) }
    fun onOutstandingChange(raw: String) = onField { copy(outstanding = sanitiseAmount(raw)) }
    fun onCreditLimitChange(raw: String) = onField { copy(creditLimit = sanitiseAmount(raw)) }

    private fun sanitiseAmount(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }.replace(",", "")
        val parts = cleaned.split('.')
        return if (parts.size <= 1) parts[0].take(12) else parts[0].take(12) + "." + parts[1].take(2)
    }

    private fun plain(money: Money): String =
        money.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun parseAmount(value: String): Long? =
        runCatching { Money.fromRupees(BigDecimal(value.trim())).minorUnits }.getOrNull()

    fun save() {
        val s = _ui.value
        val billerError = if (s.billerName.isBlank()) "Enter the biller / bank name" else null
        val cardDigits = s.cardNumber.filter { it.isDigit() }
        val cardError = when {
            s.cardNumber.isBlank() -> "Enter the card number or reference id"
            cardDigits.length >= 4 && cardDigits.length !in 4..19 -> "That card number looks too short"
            else -> null
        }
        val outstandingError = if (s.outstanding.isNotBlank() && parseAmount(s.outstanding) == null) "Enter a valid amount" else null
        val limitError = if (s.creditLimit.isNotBlank() && parseAmount(s.creditLimit) == null) "Enter a valid amount" else null
        val billError = if (s.billAmount.isNotBlank() && parseAmount(s.billAmount) == null) "Enter a valid amount" else null
        if (billerError != null || cardError != null || outstandingError != null || limitError != null || billError != null) {
            _ui.value = s.copy(
                billerNameError = billerError,
                cardNumberError = cardError,
                outstandingError = outstandingError,
                creditLimitError = limitError,
                billAmountError = billError
            )
            return
        }
        _ui.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val outstanding = parseAmount(s.outstanding) ?: 0L
                val limit = parseAmount(s.creditLimit)
                val available = limit?.let { it - outstanding }
                val billAmount = parseAmount(s.billAmount)
                val business = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL
                val active = !s.inactive

                val cardId: Long
                if (s.editingAccountId != null) {
                    val existing = accountDao.findById(s.editingAccountId) ?: return@launch
                    cardId = existing.id
                    accountDao.update(
                        existing.copy(
                            institutionName = s.billerName.trim(),
                            sourceAccountId = s.cardNumber.trim(),
                            nickname = s.nickname.ifBlank { s.billerName.trim() },
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = s.billingCycleStartDay,
                            dueDate = s.dueDateEpochMillis,
                            billAmountMinorUnits = billAmount,
                            autoPay = s.autoPay,
                            active = active,
                            hide = s.hideAccount,
                            businessPersonal = business,
                            updatedAtEpochMillis = now
                        )
                    )
                } else {
                    val clash = accountDao.findBySourceAccountId(s.cardNumber.trim())
                    if (clash != null) {
                        _ui.value = _ui.value.copy(
                            saving = false,
                            cardNumberError = "Another account already uses this number"
                        )
                        return@launch
                    }
                    cardId = accountDao.insert(
                        AccountEntity(
                            sourceAccountId = s.cardNumber.trim(),
                            institutionName = s.billerName.trim(),
                            nickname = s.nickname.ifBlank { s.billerName.trim() },
                            accountType = AccountType.CREDIT_CARD,
                            businessPersonal = business,
                            openingBalanceMinorUnits = 0,
                            currentBalanceMinorUnits = 0,
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = s.billingCycleStartDay,
                            dueDate = s.dueDateEpochMillis,
                            billAmountMinorUnits = billAmount,
                            autoPay = s.autoPay,
                            active = active,
                            hide = s.hideAccount,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now
                        )
                    )
                }

                upsertLinkedBill(
                    cardId = cardId,
                    billerName = s.billerName.trim(),
                    cardNumber = s.cardNumber.trim(),
                    nickname = s.nickname.ifBlank { s.billerName.trim() },
                    dueEpoch = s.dueDateEpochMillis,
                    billAmount = billAmount,
                    autoPay = s.autoPay,
                    business = business,
                    active = active,
                    autoGenerateBills = s.autoGenerateBills,
                    now = now
                )

                _ui.value = _ui.value.copy(saving = false, saved = true)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                Log.e("CreditCardFormVM", "Failed to save credit card", e)
                _ui.value = _ui.value.copy(
                    saving = false,
                    error = "Could not save: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    /** Keeps exactly one linked credit-card biller in sync with this card (never duplicates). */
    private suspend fun upsertLinkedBill(
        cardId: Long,
        billerName: String,
        cardNumber: String,
        nickname: String,
        dueEpoch: Long?,
        billAmount: Long?,
        autoPay: Boolean,
        business: BusinessPersonal,
        active: Boolean,
        autoGenerateBills: Boolean,
        now: Long
    ) {
        val existing = billDao.findByLinkedAccountId(cardId)
        val nextDue = dueEpoch ?: existing?.nextDueDateEpochMillis
        val amount = billAmount ?: existing?.estimatedAmountMinorUnits ?: 0L
        if (nextDue == null && amount == 0L && !autoGenerateBills) return
        if (existing == null) {
            billDao.insert(
                BillEntity(
                    billerName = billerName,
                    billerType = BillerType.CREDIT_CARD,
                    accountReferenceId = cardNumber,
                    nickname = nickname,
                    linkedAccountId = cardId,
                    billingCycle = BillingCycle.MONTHLY,
                    nextDueDateEpochMillis = nextDue ?: (now + 30L * 24 * 60 * 60 * 1000),
                    estimatedAmountMinorUnits = amount,
                    autoPay = autoPay,
                    businessPersonal = business,
                    active = active,
                    autoGenerateBills = autoGenerateBills,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
        } else {
            billDao.update(
                existing.copy(
                    billerName = billerName,
                    accountReferenceId = cardNumber,
                    nickname = nickname,
                    nextDueDateEpochMillis = nextDue ?: existing.nextDueDateEpochMillis,
                    estimatedAmountMinorUnits = amount,
                    autoPay = autoPay,
                    businessPersonal = business,
                    active = active,
                    autoGenerateBills = autoGenerateBills,
                    updatedAtEpochMillis = now
                )
            )
        }
    }

    fun delete() {
        val id = _ui.value.editingAccountId ?: return
        viewModelScope.launch {
            try {
                transactionRepository.deleteCreditCardAccount(id)
                _ui.value = _ui.value.copy(deleted = true)
            } catch (e: Exception) {
                Log.e("CreditCardFormVM", "Failed to delete credit card", e)
                _ui.value = _ui.value.copy(error = "Could not delete: ${e.message ?: "unknown error"}")
            }
        }
    }

    /** Formats an epoch for display in the form's due-date row. */
    fun dueDateLabel(epochMillis: Long?): String? = epochMillis?.let {
        formDateFormatter.format(Instant.ofEpochMilli(it).atZone(dueDateZone))
    }
}
