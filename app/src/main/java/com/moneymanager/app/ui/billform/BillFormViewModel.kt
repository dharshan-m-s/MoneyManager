package com.moneymanager.app.ui.billform

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillInstanceEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
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
import timber.log.Timber
import javax.inject.Inject

data class BillFormState(
    val editingBillId: Long? = null,
    val billerName: String = "",
    val billerType: BillerType = BillerType.OTHER,
    val accountReferenceId: String = "",
    val nickname: String = "",
    val linkedAccountId: Long? = null,
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val dueDay: String = "1",
    val dueDateEpochMillis: Long = 0L,
    val originalDueMonth: Int = 0,
    val originalDueYear: Int = 0,
    val estimatedAmount: String = "",
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: String = "3",
    val autoPay: Boolean = false,
    val inactive: Boolean = false,
    val isBusiness: Boolean = false,
    val autoGenerateBills: Boolean = true,
    val creditCards: List<AccountEntity> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BillFormViewModel @Inject constructor(
    private val billDao: BillDao,
    private val accountDao: AccountDao,
    private val backupManager: BackupManager
) : ViewModel() {
    private val _ui = MutableStateFlow(BillFormState())
    val ui: StateFlow<BillFormState> = _ui

    init {
        viewModelScope.launch {
            accountDao.observeByType(AccountType.CREDIT_CARD).collect { cards ->
                _ui.value = _ui.value.copy(creditCards = cards.filter { it.active && !it.deleted && !it.hide })
            }
        }
    }

    fun loadForEdit(billId: Long) {
        viewModelScope.launch {
            val b = billDao.findById(billId) ?: return@launch
            val zone = ZoneId.of("Asia/Kolkata")
            val dueDate = Instant.ofEpochMilli(b.nextDueDateEpochMillis).atZone(zone)
            val dueDay = dueDate.dayOfMonth
            _ui.value = _ui.value.copy(
                editingBillId = b.id,
                billerName = b.billerName,
                billerType = b.billerType,
                accountReferenceId = b.accountReferenceId ?: "",
                nickname = b.nickname,
                linkedAccountId = b.linkedAccountId,
                billingCycle = b.billingCycle,
                dueDay = dueDay.toString(),
                dueDateEpochMillis = b.nextDueDateEpochMillis,
                originalDueMonth = dueDate.monthValue,
                originalDueYear = dueDate.year,
                estimatedAmount = Money(b.estimatedAmountMinorUnits).toBigDecimal().toPlainString(),
                reminderEnabled = b.reminderEnabled,
                reminderDaysBefore = b.reminderDaysBefore.toString(),
                autoPay = b.autoPay,
                inactive = !b.active,
                isBusiness = b.businessPersonal == BusinessPersonal.BUSINESS,
                autoGenerateBills = b.autoGenerateBills,
                saved = false,
                error = null
            )
        }
    }

    fun onField(update: BillFormState.() -> BillFormState) { _ui.value = _ui.value.update() }

    fun setInitialBillerType(type: BillerType) {
        if (_ui.value.editingBillId == null) {
            _ui.value = _ui.value.copy(billerType = type)
        }
    }

    fun save() {
        val s = _ui.value
        if (s.billerName.isBlank()) { _ui.value = s.copy(error = "Biller name is required"); return }
        val day = s.dueDay.toIntOrNull()
        if (day == null || day !in 1..31) { _ui.value = s.copy(error = "Due day must be between 1 and 31"); return }
        val reminderDays = s.reminderDaysBefore.toIntOrNull()
        if (reminderDays == null || reminderDays !in 0..30) { _ui.value = s.copy(error = "Reminder days must be between 0 and 30"); return }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val zone = ZoneId.of("Asia/Kolkata")
                val today = LocalDate.now(zone)
                val dueMillis = if (s.editingBillId != null && s.originalDueMonth > 0 && s.originalDueYear > 0) {
                    val base = LocalDate.of(s.originalDueYear, s.originalDueMonth, 1)
                    val safeDay = day.coerceAtMost(base.lengthOfMonth())
                    var dueDate = base.withDayOfMonth(safeDay)
                    if (dueDate.isBefore(today)) {
                        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
                        dueDate = nextMonth.withDayOfMonth(day.coerceAtMost(nextMonth.lengthOfMonth()))
                    }
                    dueDate.atStartOfDay(zone).toInstant().toEpochMilli()
                } else {
                    val monthStart = today.withDayOfMonth(1)
                    val safeDay = day.coerceAtMost(monthStart.lengthOfMonth())
                    var dueDate = monthStart.withDayOfMonth(safeDay)
                    if (dueDate.isBefore(today)) {
                        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
                        dueDate = nextMonth.withDayOfMonth(day.coerceAtMost(nextMonth.lengthOfMonth()))
                    }
                    dueDate.atStartOfDay(zone).toInstant().toEpochMilli()
                }
                val amount = runCatching { BigDecimal(s.estimatedAmount.trim()) }
                    .getOrNull()?.let { Money.fromRupees(it).minorUnits } ?: 0L
                val linkedCard = if (s.billerType == BillerType.CREDIT_CARD) s.linkedAccountId else null

                if (s.editingBillId != null) {
                    val existing = billDao.findById(s.editingBillId) ?: return@launch
                    billDao.update(existing.copy(
                        billerName = s.billerName.trim(), billerType = s.billerType,
                        accountReferenceId = s.accountReferenceId.ifBlank { null },
                        nickname = s.nickname.ifBlank { s.billerName.trim() }, linkedAccountId = linkedCard,
                        billingCycle = s.billingCycle, nextDueDateEpochMillis = dueMillis,
                        estimatedAmountMinorUnits = amount, reminderEnabled = s.reminderEnabled,
                        reminderDaysBefore = reminderDays, autoPay = s.autoPay, active = !s.inactive,
                        businessPersonal = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                        autoGenerateBills = s.autoGenerateBills, updatedAtEpochMillis = now
                    ))
                } else {
                    val billId = billDao.insert(BillEntity(
                        billerName = s.billerName.trim(), billerType = s.billerType,
                        accountReferenceId = s.accountReferenceId.ifBlank { null },
                        nickname = s.nickname.ifBlank { s.billerName.trim() }, linkedAccountId = linkedCard,
                        billingCycle = s.billingCycle, nextDueDateEpochMillis = dueMillis,
                        estimatedAmountMinorUnits = amount, reminderEnabled = s.reminderEnabled,
                        reminderDaysBefore = reminderDays, autoPay = s.autoPay, active = !s.inactive,
                        businessPersonal = if (s.isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                        autoGenerateBills = s.autoGenerateBills, createdAtEpochMillis = now, updatedAtEpochMillis = now
                    ))
                    billDao.insertInstance(BillInstanceEntity(billId = billId, dueDateEpochMillis = dueMillis, amountDueMinorUnits = amount))
                }
                _ui.value = _ui.value.copy(saved = true, error = null)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save bill")
                _ui.value = _ui.value.copy(error = "Failed to save: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
