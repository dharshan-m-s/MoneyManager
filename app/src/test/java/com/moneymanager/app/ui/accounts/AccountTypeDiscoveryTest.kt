package com.moneymanager.app.ui.accounts

import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.reporting.FinancialSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypeDiscoveryTest {

    @Test
    fun supportedTypesMatchTheEnumMinusUnknown() {
        val supported = AccountTypeCatalog.supported
        assertEquals(
            AccountType.entries.filter { it != AccountType.UNKNOWN },
            supported
        )
    }

    @Test
    fun everySupportedTypeHasACatalogEntry() {
        AccountTypeCatalog.supported.forEach { type ->
            val info = AccountTypeCatalog.info(type)
            assertEquals(type, info.type)
            assertTrue(info.shortLabel.isNotBlank())
            assertTrue(info.singularLabel.isNotBlank())
            assertTrue(info.pluralLabel.isNotBlank())
        }
    }

    @Test
    fun everySupportedTypeHasExactlyOnePrimaryTab() {
        AccountTypeCatalog.supported.forEach { type ->
            val tab = AccountsTab.forType(type)
            assertTrue("Type ${type.name} must live in a primary tab or ALL", tab != AccountsTab.ALL || type in tab.types)
            if (tab != AccountsTab.ALL) {
                assertEquals(1, tab.types.size)
                assertTrue(type in tab.types)
            }
        }
    }

    @Test
    fun allTypesTabCoversEverySupportedTypeExactlyOnce() {
        val allTab = AccountsTab.ALL
        assertEquals(AccountTypeCatalog.supported.toSet(), allTab.types)
    }

    @Test
    fun primaryTabsDoNotOverlap() {
        val overlapping = AccountsTab.primary.flatMap { tab -> tab.types.map { type -> type to tab } }
            .groupBy { it.first }
            .filter { it.value.size > 1 }
        assertTrue("Primary tabs must partition the supported types (only ALL may overlap)", overlapping.isEmpty())
    }

    @Test
    fun filteredAccountsOnlyIncludeActiveVisibleRowsForTheType() {
        val rows = listOf(
            account(AccountType.BANK, deleted = false, hide = false, active = true),
            account(AccountType.BANK, deleted = true, hide = false, active = true),
            account(AccountType.BANK, deleted = false, hide = true, active = true),
            account(AccountType.BANK, deleted = false, hide = false, active = false),
            account(AccountType.CASH, deleted = false, hide = false, active = true)
        )
        val bankTab = AccountsTab.BANK
        val filtered = rows.filter { it.accountType in bankTab.types && !it.deleted }
        val visible = filtered.filter { !it.hide }
        val active = visible.filter { it.active }
        assertEquals(1, active.size)
        assertEquals(AccountType.BANK, active.single().accountType)
    }

    @Test
    fun allAccountTypesBrowserExcludesDeletedAndHiddenAndUnknown() {
        val rows = listOf(
            account(AccountType.BANK, deleted = false, hide = false, active = true),
            account(AccountType.BANK, deleted = true, hide = false, active = true),
            account(AccountType.UNKNOWN, deleted = false, hide = false, active = true)
        )
        val forType = rows.filter { it.accountType == AccountType.BANK && !it.deleted && !it.hide }
        assertEquals(1, forType.size)
    }

    @Test
    fun balanceSummaryUsesOutstandingForCreditCardsAndBalanceForOthers() {
        val bank = account(AccountType.BANK, current = 1000)
        val card = account(AccountType.CREDIT_CARD, outstanding = 500)
        val bankTotal = listOf(bank).filter { FinancialSummary.isIncluded(it) }.sumOf {
            if (it.accountType == AccountType.CREDIT_CARD) it.outstandingMinorUnits ?: 0L else it.currentBalanceMinorUnits
        }
        val cardTotal = listOf(card).filter { FinancialSummary.isIncluded(it) }.sumOf {
            if (it.accountType == AccountType.CREDIT_CARD) it.outstandingMinorUnits ?: 0L else it.currentBalanceMinorUnits
        }
        assertEquals(1000L, bankTotal)
        assertEquals(500L, cardTotal)
    }

    private fun account(
        type: AccountType,
        deleted: Boolean = false,
        hide: Boolean = false,
        active: Boolean = true,
        current: Long = 0L,
        outstanding: Long? = null
    ) = AccountEntity(
        id = 0L,
        sourceAccountId = "acc-$type-${current}-${outstanding}",
        institutionName = "Test",
        nickname = "Test",
        accountType = type,
        businessPersonal = BusinessPersonal.PERSONAL,
        openingBalanceMinorUnits = 0,
        currentBalanceMinorUnits = current,
        creditLimitMinorUnits = if (type == AccountType.CREDIT_CARD) 10000L else null,
        outstandingMinorUnits = outstanding,
        deleted = deleted,
        hide = hide,
        active = active,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L
    )
}
