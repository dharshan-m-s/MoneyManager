package com.moneymanager.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.local.dao.AccountDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.repository.AccountRepository
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
    private val backupManager: BackupManager,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BankLinkingState())
    val state: StateFlow<BankLinkingState> = _state

    fun save(bankName: String, accountLast4: String, debitCardLast4: String, isBusiness: Boolean) {
        save(bankName, accountLast4, debitCardLast4, isBusiness, AccountType.BANK)
    }

    fun save(bankName: String, accountLast4: String, debitCardLast4: String, isBusiness: Boolean, accountType: AccountType) {
        if (accountType == AccountType.BANK || accountType == AccountType.DEBIT_CARD) {
            val accountSuffix = accountLast4.filter(Char::isDigit).takeLast(4)
            if (accountSuffix.length < 4) {
                _state.value = _state.value.copy(error = "Enter the last 4 digits of the account number")
                return
            }
        } else if (accountType == AccountType.CREDIT_CARD) {
            // Credit card form has its own screen; this path is not used for credit card creation
            _state.value = _state.value.copy(error = "Use the credit card form to add credit cards")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            try {
                val now = System.currentTimeMillis()
                when (accountType) {
                    AccountType.BANK -> {
                        val accountSuffix = accountLast4.filter(Char::isDigit).takeLast(4)
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
                    }
                    AccountType.WALLET -> {
                        val suffix = accountLast4.filter(Char::isDigit).takeLast(4).ifEmpty { "0000" }
                        val sourceId = "WALLET-${bankName.filter { it.isLetterOrDigit() }.uppercase().take(6)}-$suffix"
                        if (accountDao.findBySourceAccountId(sourceId) == null) {
                            accountDao.insert(
                                AccountEntity(
                                    sourceAccountId = sourceId,
                                    institutionName = bankName.ifBlank { "Wallet" },
                                    nickname = bankName.ifBlank { "Wallet" },
                                    accountType = AccountType.WALLET,
                                    businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                                    openingBalanceMinorUnits = 0L,
                                    currentBalanceMinorUnits = 0L,
                                    active = true,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now
                                )
                            )
                        }
                    }
                    AccountType.PREPAID_CARD -> {
                        val suffix = accountLast4.filter(Char::isDigit).takeLast(4).ifEmpty { "0000" }
                        val sourceId = "PREPAID-${bankName.filter { it.isLetterOrDigit() }.uppercase().take(6)}-$suffix"
                        if (accountDao.findBySourceAccountId(sourceId) == null) {
                            accountDao.insert(
                                AccountEntity(
                                    sourceAccountId = sourceId,
                                    institutionName = bankName.ifBlank { "Prepaid" },
                                    nickname = bankName.ifBlank { "Prepaid Card" },
                                    accountType = AccountType.PREPAID_CARD,
                                    businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                                    openingBalanceMinorUnits = 0L,
                                    currentBalanceMinorUnits = 0L,
                                    active = true,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now
                                )
                            )
                        }
                    }
                    AccountType.LOAN -> {
                        val suffix = accountLast4.filter(Char::isDigit).takeLast(4).ifEmpty { "0000" }
                        val sourceId = "LOAN-${bankName.filter { it.isLetterOrDigit() }.uppercase().take(6)}-$suffix"
                        if (accountDao.findBySourceAccountId(sourceId) == null) {
                            accountDao.insert(
                                AccountEntity(
                                    sourceAccountId = sourceId,
                                    institutionName = bankName.ifBlank { "Loan" },
                                    nickname = bankName.ifBlank { "Loan" },
                                    accountType = AccountType.LOAN,
                                    businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                                    openingBalanceMinorUnits = 0L,
                                    currentBalanceMinorUnits = 0L,
                                    active = true,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now
                                )
                            )
                        }
                    }
                    AccountType.CASH -> {
                        accountRepository.ensureCashAccount(now)
                    }
                    AccountType.INVESTMENT -> {
                        val suffix = accountLast4.filter(Char::isDigit).takeLast(4).ifEmpty { "0000" }
                        val sourceId = "INV-${bankName.filter { it.isLetterOrDigit() }.uppercase().take(6)}-$suffix"
                        if (accountDao.findBySourceAccountId(sourceId) == null) {
                            accountDao.insert(
                                AccountEntity(
                                    sourceAccountId = sourceId,
                                    institutionName = bankName.ifBlank { "Investment" },
                                    nickname = bankName.ifBlank { "Investment" },
                                    accountType = AccountType.INVESTMENT,
                                    businessPersonal = if (isBusiness) BusinessPersonal.BUSINESS else BusinessPersonal.PERSONAL,
                                    openingBalanceMinorUnits = 0L,
                                    currentBalanceMinorUnits = 0L,
                                    active = true,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now
                                )
                            )
                        }
                    }
                    else -> Unit
                }
                _state.value = _state.value.copy(saving = false, saved = true)
                backupManager.scheduleAfterWrite()
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "Could not save account")
            }
        }
    }
}
