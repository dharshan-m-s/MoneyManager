package com.moneymanager.app.ui.navigation

import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountNamingMigrationTest {

    @Test
    fun everySelectableAccountTypeHasReadableNames() {
        AccountTypeCatalog.all.forEach { info ->
            assertTrue(info.singularLabel.isNotBlank())
            assertTrue(info.pluralLabel.isNotBlank())
        }
    }

    @Test
    fun accountTypeLabelsRemainDistinctForCommonTypes() {
        assertEquals("Bank account", AccountTypeCatalog.label(AccountType.BANK))
        assertEquals("Credit card", AccountTypeCatalog.label(AccountType.CREDIT_CARD))
        assertEquals("Debit card", AccountTypeCatalog.label(AccountType.DEBIT_CARD))
        assertEquals("Prepaid card", AccountTypeCatalog.label(AccountType.PREPAID_CARD))
    }
}
