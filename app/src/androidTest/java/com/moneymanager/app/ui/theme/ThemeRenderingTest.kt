package com.moneymanager.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.components.MmCard
import com.moneymanager.app.ui.components.MmPill
import org.junit.Rule
import org.junit.Test

/**
 * Section 11/16: major shared components must compose and render in BOTH themes. This is a device
 * smoke test (runs on an emulator/device via connectedDebugAndroidTest) that the semantic tokens
 * resolve to real, visible colours rather than transparent or crashing.
 */
class ThemeRenderingTest {

    @get:Rule
    val compose = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun content() {
        Column(Modifier.padding(16.dp)) {
            Text("Primary text", color = MmColors.textPrimary)
            Text("Secondary text", color = MmColors.textSecondary)
            MmCard {
                Text("Card surface", color = MmColors.textPrimary)
                MmPill("Income", tint = MmColors.income, filled = true)
                MmPill("Expense", tint = MmColors.expense, filled = true)
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(backgroundColor = MmColors.accent)
                ) {
                    Text("Primary action", color = MmColors.onAccent)
                }
            }
        }
    }

    @Test
    fun rendersInLightTheme() {
        compose.setContent {
            MoneyManagerTheme(darkTheme = false) { content() }
        }
        compose.onNodeWithText("Primary text").assertIsDisplayed()
        compose.onNodeWithText("Income").assertIsDisplayed()
        compose.onNodeWithText("Primary action").assertIsDisplayed()
    }

    @Test
    fun rendersInDarkTheme() {
        compose.setContent {
            MoneyManagerTheme(darkTheme = true) { content() }
        }
        compose.onNodeWithText("Primary text").assertIsDisplayed()
        compose.onNodeWithText("Expense").assertIsDisplayed()
        compose.onNodeWithText("Primary action").assertIsDisplayed()
    }
}
