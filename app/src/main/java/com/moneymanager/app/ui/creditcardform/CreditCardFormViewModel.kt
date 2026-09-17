package com.moneymanager.app.ui.creditcardform

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** UI state for the dedicated credit-card editor. The account holds ledger/outstanding data;
 * the linked BillEntity holds reminders, due date, auto-pay and bill-generation settings. */
data class CreditCardFormState(
    val editingAccountId: Long? = null,
    val billerName: String = "",
    val cardNumber: String = "",
    val nickname: String = "",
    val outstanding: String = "",
    val creditLimit: String = "",
    val billingCycleStartDay: String = "1",
    val billAmount: String = "",
    val dueDateEpochMillis: Long? = null,
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val autoPay: Boolean = false,
    val inactive: Boolean = false,
    val isBusiness: Boolean = false,
    val autoGenerateBills: Boolean = true,
    val saved: Boolean = false,
    val error: String? = null,
    val deactivated: Boolean = false
)

@HiltViewModel
class CreditCardFormViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val billDao: BillDao,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditCardFormState())
    val ui: StateFlow<CreditCardFormState> = _ui

    private val zone = ZoneId.of("Asia/Kolkata")

    fun loadForEdit(accountId: Long) {
        viewModelScope.launch {
            val account = accountDao.findById(accountId) ?: return@launch
            val bill = billDao.findByLinkedAccountId(accountId)
            _ui.value = CreditCardFormState(
                editingAccountId = account.id,
                billerName = account.institutionName,
                cardNumber = account.sourceAccountId.removePrefix("x"),
                nickname = account.nickname,
                outstanding = account.outstandingMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                creditLimit = account.creditLimitMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: "",
                billingCycleStartDay = (account.billingCycleStartDay ?: 1).toString(),
                billAmount = account.billAmountMinorUnits?.let { Money(it).toBigDecimal().toPlainString() } ?: bill?.estimatedAmountMinorUnits?.let { Money(it).toBigDecimal().toPlainString() }.orEmpty(),
                dueDateEpochMillis = account.dueDate ?: bill?.nextDueDateEpochMillis,
                billingCycle = bill?.billingCycle ?: BillingCycle.MONTHLY,
                autoPay = account.autoPay || (bill?.autoPay == true),
                inactive = !account.active,
                isBusiness = account.businessPersonal == BusinessPersonal.BUSINESS,
                autoGenerateBills = bill?.autoGenerateBills ?: true
            )
        }
    }

    fun onField(update: CreditCardFormState.() -> CreditCardFormState) {
        _ui.value = _ui.value.update()
    }

    fun save() {
        val s = _ui.value
        if (s.billerName.isBlank() || s.cardNumber.isBlank()) {
            _ui.value = s.copy(error = "Biller name and card number are required")
            return
        }
        val startDay = s.billingCycleStartDay.toIntOrNull()
        if (startDay == null || startDay !in 1..31) {
            _ui.value = s.copy(error = "Billing cycle start day must be between 1 and 31")
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val outstanding = parseMoney(s.outstanding) ?: 0L
                val limit = parseMoney(s.creditLimit)
                val billAmount = parseMoney(s.billAmount) ?: 0L
                val available = limit?.minus(outstanding)
                val dueDate = normalizeDueDate(s.dueDateEpochMillis, now)
                val sourceId = "x${s.cardNumber.trim().removePrefix("x")}".takeIf { it.length > 1 } ?: s.cardNumber.trim()
                val business = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL

                val accountId = if (s.editingAccountId != null) {
                    val existing = accountDao.findById(s.editingAccountId) ?: return@launch
                    accountDao.update(
                        existing.copy(
                            institutionName = s.billerName.trim(),
                            sourceAccountId = sourceId,
                            nickname = s.nickname.ifBlank { s.billerName.trim() },
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = startDay,
                            dueDate = dueDate,
                            billAmountMinorUnits = billAmount,
                            autoPay = s.autoPay,
                            active = !s.inactive,
                            businessPersonal = business,
                            updatedAtEpochMillis = now
                        )
                    )
                    existing.id
                } else {
                    accountDao.insert(
                        AccountEntity(
                            sourceAccountId = sourceId,
                            institutionName = s.billerName.trim(),
                            nickname = s.nickname.ifBlank { s.billerName.trim() },
                            accountType = AccountType.CREDIT_CARD,
                            businessPersonal = business,
                            openingBalanceMinorUnits = 0,
                            currentBalanceMinorUnits = outstanding,
                            outstandingMinorUnits = outstanding,
                            creditLimitMinorUnits = limit,
                            availableLimitMinorUnits = available,
                            billingCycleStartDay = startDay,
                            dueDate = dueDate,
                            billAmountMinorUnits = billAmount,
                            autoPay = s.autoPay,
                            active = !s.inactive,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now
                        )
                    )
                }

                upsertLinkedBill(
                    accountId = accountId,
                    billerName = s.billerName.trim(),
                    cardNumber = sourceId,
                    nickname = s.nickname.ifBlank { s.billerName.trim() },
                    billingCycle = s.billingCycle,
                    dueMillis = dueDate,
                    amount = billAmount,
                    autoPay = s.autoPay,
                    businessPersonal = business,
                    active = !s.inactive,
                    autoGenerateBills = s.autoGenerateBills,
                    now = now
                )

                _ui.value = s.copy(saved = true, error = null)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                Log.e("CreditCardFormVM", "Failed to save credit card", e)
                _ui.value = s.copy(error = "Failed to save: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun deactivate() {
        val accountId = _ui.value.editingAccountId ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val account = accountDao.findById(accountId) ?: return@launch
                accountDao.update(account.copy(active = false, updatedAtEpochMillis = now))
                billDao.findByLinkedAccountId(accountId)?.let { bill ->
                    billDao.update(bill.copy(active = false, updatedAtEpochMillis = now))
                }
                _ui.value = _ui.value.copy(deactivated = true)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                Log.e("CreditCardFormVM", "Failed to deactivate credit card", e)
                _ui.value = _ui.value.copy(error = "Could not deactivate card: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private suspend fun upsertLinkedBill(
        accountId: Long,
        billerName: String,
        cardNumber: String,
        nickname: String,
        billingCycle: BillingCycle,
        dueMillis: Long,
        amount: Long,
        autoPay: Boolean,
        businessPersonal: BusinessPersonal,
        active: Boolean,
        autoGenerateBills: Boolean,
        now: Long
    ) {
        val existing = billDao.findByLinkedAccountId(accountId)
        if (existing == null) {
            val billId = billDao.insert(
                BillEntity(
                    billerName = billerName,
                    billerType = BillerType.CREDIT_CARD,
                    accountReferenceId = cardNumber,
                    nickname = nickname,
                    linkedAccountId = accountId,
                    billingCycle = billingCycle,
                    nextDueDateEpochMillis = dueMillis,
                    estimatedAmountMinorUnits = amount,
                    autoPay = autoPay,
                    reminderEnabled = true,
                    reminderDaysBefore = 3,
                    businessPersonal = businessPersonal,
                    active = active,
                    autoGenerateBills = autoGenerateBills,
                    autoGenerateLeadDays = 10,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            billDao.insertInstance(
                BillInstanceEntity(
                    billId = billId,
                    dueDateEpochMillis = dueMillis,
                    amountDueMinorUnits = amount
                )
            )
        } else {
            billDao.update(
                existing.copy(
                    billerName = billerName,
                    billerType = BillerType.CREDIT_CARD,
                    accountReferenceId = cardNumber,
                    nickname = nickname,
                    linkedAccountId = accountId,
                    billingCycle = billingCycle,
                    nextDueDateEpochMillis = dueMillis,
                    estimatedAmountMinorUnits = amount,
                    autoPay = autoPay,
                    businessPersonal = businessPersonal,
                    active = active,
                    autoGenerateBills = autoGenerateBills,
                    autoGenerateLeadDays = 10,
                    updatedAtEpochMillis = now
                )
            )
            billDao.findNextUnpaidInstance(existing.id)?.let { instance ->
                billDao.updateInstance(
                    instance.copy(
                        dueDateEpochMillis = dueMillis,
                        amountDueMinorUnits = amount
                    )
                )
            } ?: billDao.insertInstance(
                BillInstanceEntity(
                    billId = existing.id,
                    dueDateEpochMillis = dueMillis,
                    amountDueMinorUnits = amount
                )
            )
        }
    }

    private fun parseMoney(value: String): Long? = runCatching {
        Money.fromRupees(BigDecimal(value.trim())).minorUnits
    }.getOrNull()?.takeIf { it >= 0L }

    private fun normalizeDueDate(value: Long?, now: Long): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val selected = value?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        if (selected == null) {
            return today.withDayOfMonth(minOf(24, today.lengthOfMonth())).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val monthStart = selected.withDayOfMonth(minOf(selected.dayOfMonth, selected.lengthOfMonth()))
        return monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
