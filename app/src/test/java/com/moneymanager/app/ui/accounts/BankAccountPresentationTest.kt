package com.moneymanager.app.ui.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.moneymanager.app.domain.model.AccountType

class BankAccountPresentationTest {
    @Test
    fun issuerMatchingIncludesEveryAccountForFederalBankVariants() {
        val federalAccounts = listOf("Federal Bank", "FEDERAL BANK LTD.", "Federal")
        assertTrue(federalAccounts.all { bankIssuerMatches(it, "Federal Bank") })
        assertFalse(bankIssuerMatches("HDFC Bank", "Federal Bank"))
    }

    @Test
    fun accountNumberIsMaskedToItsLastFourDigits() {
        assertEquals("•••• 1234", maskedBankAccountNumber("FED-ACCT-001234"))
        assertEquals("Account number unavailable", maskedBankAccountNumber("FED-ACCT"))
    }

    @Test
    fun linkedDebitCardDisplaysTheLiveBankBalance() {
        assertEquals(125_000L, debitCardDisplayBalance(AccountType.DEBIT_CARD, 0L, 125_000L))
    }

    @Test
    fun standaloneDebitCardKeepsItsOwnBalance() {
        assertEquals(4_500L, debitCardDisplayBalance(AccountType.DEBIT_CARD, 4_500L, null))
    }

    @Test
    fun nonDebitAccountNeverUsesLinkedBankDisplayBalance() {
        assertEquals(4_500L, debitCardDisplayBalance(AccountType.BANK, 4_500L, 125_000L))
    }

    @Test
    fun separateFederalAccountsRemainDistinctByTheirMaskedNumbers() {
        val choices = listOf(
            maskedBankAccountNumber("FED-001234"),
            maskedBankAccountNumber("FED-005678"),
            maskedBankAccountNumber("FED-009876")
        )
        assertEquals(3, choices.distinct().size)
    }
}
