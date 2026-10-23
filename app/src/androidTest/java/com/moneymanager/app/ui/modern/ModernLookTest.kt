package com.moneymanager.app.ui.modern

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.appearance.AppearanceSelector
import com.moneymanager.app.ui.appearance.MmAppearance
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import com.moneymanager.app.ui.modern.components.MmCard
import com.moneymanager.app.ui.modern.components.MmPill
import com.moneymanager.app.ui.modern.components.MmSegmentedControl
import com.moneymanager.app.ui.modern.components.MmTransactionRow
import com.moneymanager.app.ui.modern.components.MmGroupPosition
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.ModernTheme
import com.moneymanager.app.ui.modern.navigation.AddAction
import com.moneymanager.app.ui.modern.navigation.ModernAddActionSurface
import com.moneymanager.app.ui.modern.navigation.ModernMorePopover
import com.moneymanager.app.ui.navigation.Destination
import com.moneymanager.app.ui.components.MmTransactionRow as DefaultTransactionRow
import com.moneymanager.app.ui.theme.MmColors as DefaultColors
import com.moneymanager.app.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Device-level checks for the second appearance:
 *  - both options render in Settings and the active one is indicated,
 *  - selecting one calls back and persists through the caller,
 *  - switching the appearance updates the visible tree immediately (no restart),
 *  - the shared transaction row renders in BOTH looks, so the old UI stays usable.
 */
class ModernLookTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectorShowsBothLooksAndIndicatesTheActiveOne() {
        compose.setContent {
            MoneyManagerTheme(darkTheme = true) {
                AppearanceSelector(selected = MmAppearance.DEFAULT, onSelect = {})
            }
        }
        compose.onNodeWithText("Default").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Modern").assertIsDisplayed()
        compose.onNodeWithText("The original Money Manager experience.").assertIsDisplayed()
        compose.onNodeWithText(
            "A calm, clean layout with a floating dock and clear amounts."
        ).assertIsDisplayed()
    }

    @Test
    fun tappingModernCallsBackWithModern() {
        var picked: MmAppearance? = null
        compose.setContent {
            MoneyManagerTheme(darkTheme = false) {
                AppearanceSelector(selected = MmAppearance.DEFAULT, onSelect = { picked = it })
            }
        }
        compose.onNode(hasText("Modern") and hasClickAction()).performClick()
        assertEquals(MmAppearance.MODERN, picked)
    }

    @Test
    fun morePopoverOpensAnExistingSecondaryDestination() {
        var selected: Destination? = null
        compose.setContent {
            ModernTheme(darkTheme = false) {
                ModernMorePopover(onSelect = { selected = it })
            }
        }
        compose.onNodeWithText("Bills").performClick()
        assertEquals(Destination.Bills, selected)
    }

    @Test
    fun addSurfaceDispatchesTheSelectedAction() {
        var selected = -1
        compose.setContent {
            ModernTheme(darkTheme = false) {
                ModernAddActionSurface(
                    actions = listOf(
                        AddAction("Expense", androidx.compose.material.icons.Icons.AutoMirrored.Filled.TrendingDown, MmColors.expense, "Add expense"),
                        AddAction("Income", androidx.compose.material.icons.Icons.AutoMirrored.Filled.TrendingUp, MmColors.income, "Add income"),
                        AddAction("Transfer", androidx.compose.material.icons.Icons.Filled.SwapHoriz, MmColors.accent, "Transfer between accounts")
                    ),
                    onSelect = { selected = it }
                )
            }
        }
        compose.onNodeWithText("Transfer").performClick()
        assertEquals(2, selected)
    }

    @Test
    fun switchingAppearanceUpdatesTheVisibleTreeImmediately() {
        var appearance by mutableStateOf(MmAppearance.DEFAULT)
        compose.setContent {
            if (appearance == MmAppearance.DEFAULT) {
                MoneyManagerTheme(darkTheme = true) { Text("Default content") }
            } else {
                ModernTheme(darkTheme = true) { Text("Modern content") }
            }
        }
        compose.onNodeWithText("Default content").assertIsDisplayed()
        compose.runOnIdle { appearance = MmAppearance.MODERN }
        compose.onNodeWithText("Modern content").assertIsDisplayed()
    }

    @Test
    fun modernDesignSystemRendersInBothModes() {
        var dark by mutableStateOf(false)
        compose.setContent {
            ModernTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp)) {
                    Text("Primary text", color = MmColors.textPrimary)
                    Text("Secondary text", color = MmColors.textSecondary)
                    MmCard {
                        Text("Card surface", color = MmColors.textPrimary)
                        MmPill("Income", tint = MmColors.income, filled = true)
                        MmPill("Expense", tint = MmColors.expense, filled = true)
                    }
                    MmSegmentedControl(
                        options = listOf("All", "Expense", "Income"),
                        selectedIndex = 0,
                        onSelect = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Primary text").assertIsDisplayed()
        // Income is intentionally present in both the semantic pill and the segmented
        // control; assert the complete rendered state instead of assuming a unique label.
        assertEquals(2, compose.onAllNodesWithText("Income").fetchSemanticsNodes().size)
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Primary text").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Income").fetchSemanticsNodes().size)
    }

    @Test
    fun theCanonicalTransactionRowRendersInBothLooks() {
        val epoch = 1_700_000_000_000L
        val amount = Money(250_000)
        compose.setContent {
            Column {
                MoneyManagerTheme(darkTheme = true) {
                    DefaultTransactionRow(
                        title = "Salary",
                        subtitle = "Income",
                        dateEpochMillis = epoch,
                        amount = amount,
                        onClick = {},
                        showDivider = false
                    )
                }
                ModernTheme(darkTheme = true) {
                    MmTransactionRow(
                        title = "Salary",
                        subtitle = "Income",
                        dateEpochMillis = epoch,
                        amount = amount,
                        onClick = {},
                        showDivider = false,
                        groupPosition = MmGroupPosition.SINGLE
                    )
                }
            }
        }
        assertEquals(2, compose.onAllNodes(hasText("Salary")).fetchSemanticsNodes().size)
    }

}
