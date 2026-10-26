package com.moneymanager.app.ui.creditcardform

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.dao.BillDao
import com.moneymanager.app.data.local.db.MoneyManagerDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.BillEntity
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.repository.AccountRepository
import com.moneymanager.app.data.repository.AccountingService
import com.moneymanager.app.data.repository.BackupManager
import com.moneymanager.app.data.repository.CreditCardBillingService
import com.moneymanager.app.data.repository.CreditCardStatementService
import com.moneymanager.app.data.repository.TransactionRepository
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.domain.billing.BillSchedule
import com.moneymanager.app.domain.billing.CreditCardStatement
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
    /** True only after the user deliberately edits the outstanding field. Reopening and saving
     *  the card for another reason must not reset the durable outstanding checkpoint. */
    val outstandingEdited: Boolean = false,
    val creditLimit: String = "",
    /** True only after the user deliberately edits the limit field. It becomes a durable
     *  override so a later recalculation cannot re-derive the limit from statement
     *  snapshots and throw the user's number away. */
    val creditLimitEdited: Boolean = false,
    val billingCycleStartDay: Int = 1,
    val billingCycleSet: Boolean = true,
    /** The due date of the card's next statement. It is DERIVED from the billing cycle (the
     *  statement closes at the end of the cycle and falls due a fixed credit period later)
     *  until the user picks one of their own. */
    val dueDateEpochMillis: Long? = CreditCardStatement.defaultDueDate(1, System.currentTimeMillis()),
    /** True only when the user picks a new due date; preserves a 31st anchor when the loaded
     *  occurrence happens to be clamped to 28 February. */
    val dueDateEdited: Boolean = false,
    /** The estimate used for a cycle that has not closed yet. The real statement is computed
     *  from the card's own transactions - this is only the fallback. */
    val billAmount: String = "",
    /** True only after the user deliberately edits the estimate. */
    val estimateEdited: Boolean = false,
    /** What the card owes for the cycle running right now - computed, never typed. */
    val currentStatement: String = "",
    /** The cycle the statement above belongs to, e.g. "01 OCT - 31 OCT". */
    val currentCycleLabel: String = "",
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
    val error: String? = null,
    val deleteBlockedMessage: String? = null
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
    private val accountingService: AccountingService,
    private val billRecurrenceService: BillRecurrenceService,
    private val creditCardStatementService: CreditCardStatementService,
    private val creditCardBillingService: CreditCardBillingService,
    private val backupManager: BackupManager,
    private val accountRepository: AccountRepository,
    private val database: MoneyManagerDatabase
) : ViewModel() {

    private val _ui = MutableStateFlow(CreditCardFormState())
    val ui: StateFlow<CreditCardFormState> = _ui

    fun loadForEdit(accountId: Long) {
        viewModelScope.launch {
            val account = accountDao.findById(accountId) ?: return@launch
            val linkedBill = billDao.findByLinkedAccountId(account.id)
            val cycleStartDay = account.billingCycleStartDay ?: 1
            val now = System.currentTimeMillis()
            _ui.value = CreditCardFormState(
                editingAccountId = account.id,
                billerName = account.institutionName,
                cardNumber = account.sourceAccountId,
                nickname = account.nickname,
                outstanding = account.outstandingMinorUnits?.let { plain(Money(it)) }.orEmpty(),
                outstandingEdited = false,
                creditLimit = (account.creditLimitOverrideMinorUnits ?: account.creditLimitMinorUnits)
                    ?.let { plain(Money(it)) }.orEmpty(),
                creditLimitEdited = false,
                billingCycleStartDay = cycleStartDay,
                billingCycleSet = account.billingCycleStartDay != null,
                dueDateEpochMillis = linkedBill?.nextDueDateEpochMillis
                    ?: account.dueDate
                    ?: CreditCardStatement.defaultDueDate(cycleStartDay, now),
                dueDateEdited = false,
                billAmount = linkedBill?.estimatedAmountMinorUnits
                    ?.takeIf { it > 0L }
                    ?.let { plain(Money(it)) }
                    .orEmpty(),
                estimateEdited = false,
                currentStatement = plain(Money(creditCardStatementService.currentStatementMinorUnits(account))),
                currentCycleLabel = CreditCardStatement.currentCycle(cycleStartDay, now).label,
                autoPay = account.autoPay,
                autoGenerateBills = linkedBill?.autoGenerateBills ?: true,
                inactive = !account.active,
                hideAccount = account.hide,
                isBusiness = account.businessPersonal == BusinessPersonal.BUSINESS
            )
            recomputeDeleteBlockedMessage()
        }
    }

    fun onField(update: CreditCardFormState.() -> CreditCardFormState) {
        _ui.value = _ui.value.update().also { state ->
            _ui.value = state.copy(
                billerNameError = state.billerNameError?.takeIf { state.billerName == _ui.value.billerName },
                cardNumberError = state.cardNumberError?.takeIf { state.cardNumber == _ui.value.cardNumber },
                outstandingError = state.outstandingError?.takeIf { state.outstanding == _ui.value.outstanding },
                creditLimitError = state.creditLimitError?.takeIf { state.creditLimit == _ui.value.creditLimit },
                billAmountError = state.billAmountError?.takeIf { state.billAmount == _ui.value.billAmount },
                deleteBlockedMessage = null,
                error = null
            )
        }
    }

    fun onBillingCycleStartDayChange(day: Int) = onField {
        val next = copy(billingCycleStartDay = day, billingCycleSet = true)
        if (next.dueDateEdited) next
        else next.copy(
            dueDateEpochMillis = CreditCardStatement.defaultDueDate(
                day,
                System.currentTimeMillis()
            )
        )
    }

    fun onDueDateChange(epochMillis: Long) = onField { copy(dueDateEpochMillis = epochMillis, dueDateEdited = true) }
    fun onBillAmountChange(raw: String) = onField { copy(billAmount = sanitiseAmount(raw), estimateEdited = true) }
    fun onOutstandingChange(raw: String) = onField { copy(outstanding = sanitiseAmount(raw), outstandingEdited = true) }
    fun onCreditLimitChange(raw: String) = onField { copy(creditLimit = sanitiseAmount(raw), creditLimitEdited = true) }

    private fun sanitiseAmount(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }.replace(",", "")
        val parts = cleaned.split('.')
        return if (parts.size <= 1) parts[0].take(12) else parts[0].take(12) + "." + parts[1].take(2)
    }

    private fun plain(money: Money): String = money.toBigDecimal().stripTrailingZeros().toPlainString()

    private fun parseAmount(value: String): Long? = runCatching { Money.fromRupees(BigDecimal(value.trim())).minorUnits }.getOrNull()

    private fun recomputeDeleteBlockedMessage() {
        val id = _ui.value.editingAccountId ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                deleteBlockedMessage = accountRepository.checkDeletion(id).let { check ->
                    if (check.canDelete) null else check.reason?.ifBlank { null }
                }
            )
        }
    }

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

                var cardId: Long = -1L
                if (s.editingAccountId != null) {
                    val existing = accountDao.findById(s.editingAccountId)
                    if (existing == null) {
                        _ui.value = _ui.value.copy(saving = false, error = "This card no longer exists")
                        return@launch
                    }
                    cardId = existing.id
                    val derivedOutstanding = accountingService.derivedBalancesFor(existing).baseOutstandingMinorUnits
                    val effectiveOutstanding = if (s.outstandingEdited) outstanding else existing.outstandingMinorUnits ?: derivedOutstanding
                    val adjustment = if (s.outstandingEdited) {
                        com.moneymanager.app.domain.accounting.AccountingEngine.checkpointAdjustment(outstanding, derivedOutstanding)
                    } else existing.outstandingAdjustmentMinorUnits
                    database.withTransaction {
                        accountDao.update(
                            existing.copy(
                                institutionName = s.billerName.trim(),
                                sourceAccountId = s.cardNumber.trim(),
                                nickname = s.nickname.ifBlank { s.billerName.trim() },
                                outstandingMinorUnits = effectiveOutstanding,
                                outstandingCheckpointMinorUnits = if (s.outstandingEdited) outstanding else existing.outstandingCheckpointMinorUnits,
                                outstandingCheckpointAtEpochMillis = if (s.outstandingEdited) now else existing.outstandingCheckpointAtEpochMillis,
                                outstandingAdjustmentMinorUnits = adjustment,
                                creditLimitMinorUnits = limit,
                                creditLimitOverrideMinorUnits = if (s.creditLimitEdited) limit else existing.creditLimitOverrideMinorUnits,
                                availableLimitMinorUnits = limit?.let { it - effectiveOutstanding } ?: available,
                                billingCycleStartDay = s.billingCycleStartDay,
                                dueDate = s.dueDateEpochMillis ?: existing.dueDate,
                                autoPay = s.autoPay,
                                active = active,
                                hide = s.hideAccount,
                                businessPersonal = business,
                                updatedAtEpochMillis = now
                            )
                        )
                        accountingService.recalculateAccounts(setOf(cardId))
                    }
                } else {
                    val clash = accountDao.findBySourceAccountId(s.cardNumber.trim())
                    if (clash != null) {
                        _ui.value = _ui.value.copy(
                            saving = false,
                            cardNumberError = "Another account already uses this number"
                        )
                        return@launch
                    }
                    database.withTransaction {
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
                                outstandingCheckpointMinorUnits = outstanding,
                                outstandingCheckpointAtEpochMillis = now,
                                outstandingAdjustmentMinorUnits = outstanding,
                                creditLimitMinorUnits = limit,
                                creditLimitOverrideMinorUnits = limit,
                                availableLimitMinorUnits = available,
                                billingCycleStartDay = s.billingCycleStartDay,
                                dueDate = s.dueDateEpochMillis,
                                autoPay = s.autoPay,
                                active = active,
                                hide = s.hideAccount,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now
                            )
                        )
                        accountingService.recalculateAccounts(setOf(cardId))
                    }
                }

                val billId = upsertLinkedBill(
                    cardId = cardId,
                    billerName = s.billerName.trim(),
                    cardNumber = s.cardNumber.trim(),
                    nickname = s.nickname.ifBlank { s.billerName.trim() },
                    dueEpoch = s.dueDateEpochMillis,
                    dueDateWasEdited = s.dueDateEdited,
                    estimate = billAmount,
                    estimateWasEdited = s.estimateEdited,
                    autoPay = s.autoPay,
                    business = business,
                    active = active,
                    autoGenerateBills = s.autoGenerateBills,
                    now = now
                )
                billId?.let { billRecurrenceService.reconcileAfterEdit(it, now) }
                creditCardBillingService.refreshCard(cardId, now)

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

    private suspend fun upsertLinkedBill(
        cardId: Long,
        billerName: String,
        cardNumber: String,
        nickname: String,
        dueEpoch: Long?,
        dueDateWasEdited: Boolean,
        estimate: Long?,
        estimateWasEdited: Boolean,
        autoPay: Boolean,
        business: BusinessPersonal,
        active: Boolean,
        autoGenerateBills: Boolean,
        now: Long
    ): Long? {
        val existing = billDao.findByLinkedAccountId(cardId)
        if (existing == null && dueEpoch == null) return null

        if (existing == null) {
            val due = dueEpoch ?: return null
            val amount = estimate ?: accountDao.findById(cardId)?.let {
                creditCardStatementService.currentStatementMinorUnits(it)
            } ?: 0L
            return billDao.insert(
                BillEntity(
                    billerName = billerName,
                    billerType = BillerType.CREDIT_CARD,
                    accountReferenceId = cardNumber,
                    nickname = nickname,
                    linkedAccountId = cardId,
                    billingCycle = BillingCycle.MONTHLY,
                    nextDueDateEpochMillis = due,
                    scheduleAnchorDay = BillSchedule.anchorDay(due, null),
                    estimatedAmountMinorUnits = amount,
                    autoPay = autoPay,
                    businessPersonal = business,
                    active = active,
                    autoGenerateBills = autoGenerateBills,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
        }

        val nextDue = dueEpoch ?: existing.nextDueDateEpochMillis
        val anchor = if (dueDateWasEdited) {
            BillSchedule.anchorDay(nextDue, null)
        } else {
            existing.scheduleAnchorDay ?: BillSchedule.anchorDay(nextDue, null)
        }
        billDao.update(
            existing.copy(
                billerName = billerName,
                accountReferenceId = cardNumber,
                nickname = nickname,
                nextDueDateEpochMillis = nextDue,
                scheduleAnchorDay = anchor,
                estimatedAmountMinorUnits = if (estimateWasEdited) estimate ?: 0L else existing.estimatedAmountMinorUnits,
                autoPay = autoPay,
                businessPersonal = business,
                active = active,
                autoGenerateBills = autoGenerateBills,
                updatedAtEpochMillis = now
            )
        )
        return existing.id
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
