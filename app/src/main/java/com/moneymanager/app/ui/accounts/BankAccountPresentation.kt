package com.moneymanager.app.ui.accounts

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType

/** Matches issuer names while tolerating suffixes such as Ltd. and punctuation. */
internal fun bankIssuerMatches(accountInstitutionName: String, issuer: String): Boolean {
    fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
    val accountIssuer = normalize(accountInstitutionName)
    val selectedIssuer = normalize(issuer)
    return accountIssuer.isNotBlank() && selectedIssuer.isNotBlank() &&
        (accountIssuer.contains(selectedIssuer) || selectedIssuer.contains(accountIssuer))
}

fun AccountEntity.matchesBankIssuer(issuer: String): Boolean =
    bankIssuerMatches(institutionName, issuer)

/** Account IDs are shown only as a masked suffix in account pickers. */
internal fun maskedBankAccountNumber(sourceAccountId: String): String {
    val digits = sourceAccountId.filter(Char::isDigit)
    return digits.takeLast(4).takeIf { it.length == 4 }?.let { "•••• $it" }
        ?: "Account number unavailable"
}

fun AccountEntity.maskedBankAccountNumber(): String = maskedBankAccountNumber(sourceAccountId)

/** A linked debit card displays its bank's live balance without owning a duplicate balance. */
internal fun debitCardDisplayBalance(
    accountType: AccountType,
    ownBalanceMinorUnits: Long,
    linkedBankBalanceMinorUnits: Long?
): Long = if (accountType == AccountType.DEBIT_CARD && linkedBankBalanceMinorUnits != null)
    linkedBankBalanceMinorUnits else ownBalanceMinorUnits
