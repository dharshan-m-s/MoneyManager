package com.moneymanager.app.ui.navigation

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneymanager.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks visible destinations and their critical overview surfaces through repeated navigation. */
@RunWith(AndroidJUnit4::class)
class NavigationRenderingSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryDestinationsKeepTheirContentAcrossRepeatedNavigation() {
        assertVisible("screen.dashboard")
        assertVisible("dashboard.balance")

        composeRule.onNodeWithTag("dock.accounts").performClick()
        assertVisible("accounts.overview")
        composeRule.onNodeWithTag("accounts.tab.bank").assertIsSelected()
        selectAccountTab("accounts.tab.credit_cards")
        selectAccountTab("accounts.tab.wallets")
        selectAccountTab("accounts.tab.prepaid_cards")
        selectAccountTab("accounts.tab.all")

        repeat(5) {
            composeRule.onNodeWithTag("dock.accounts").performClick()
            assertVisible("screen.accounts")
            assertVisible("accounts.overview")

            composeRule.onNodeWithTag("dock.transactions").performClick()
            assertVisible("screen.transactions")

            composeRule.onNodeWithTag("dock.more").performClick()
            assertVisible("overlay.more")

            composeRule.onNodeWithTag("dock.home").performClick()
            assertVisible("screen.dashboard")
            assertVisible("dashboard.balance")
        }

        repeat(5) {
            composeRule.onNodeWithTag("dock.transactions").performClick()
            assertVisible("screen.transactions")

            composeRule.onNodeWithTag("dock.accounts").performClick()
            assertVisible("screen.accounts")
            assertVisible("accounts.overview")

            composeRule.onNodeWithTag("dock.more").performClick()
            assertVisible("overlay.more")

            composeRule.onNodeWithTag("dock.accounts").performClick()
            assertVisible("screen.accounts")

            composeRule.onNodeWithTag("dock.home").performClick()
            assertVisible("screen.dashboard")
        }
    }

    private fun assertVisible(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertExists()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun selectAccountTab(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }
}
