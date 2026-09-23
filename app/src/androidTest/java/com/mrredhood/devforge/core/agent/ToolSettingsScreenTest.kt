package com.mrredhood.devforge.core.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test

class ToolSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsProviderNeutralToolCatalog() {
        composeRule.setContent { ToolSettingsScreen() }
        composeRule.onNodeWithText("AI Tools", useUnmergedTree = true).assertIsDisplayed()
        val toolList = composeRule.onNodeWithTag("tool-settings-list", useUnmergedTree = true)
        toolList.performScrollToIndex(17)
        composeRule.onNodeWithText("Web search", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Scrape URL", useUnmergedTree = true).assertIsDisplayed()
        toolList.performScrollToIndex(15)
        composeRule.onNodeWithText("Delete path", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Disabled by default for the main AI. Enable it when needed; deletion still requires approval.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
