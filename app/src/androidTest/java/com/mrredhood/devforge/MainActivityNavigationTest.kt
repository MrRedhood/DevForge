package com.mrredhood.devforge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatProviderCanBeChangedWithoutOpeningSettings() {
        composeRule.onNodeWithText("Google Gemini", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Anthropic Claude", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Anthropic Claude", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun filesShowsWorkspaceCreationUi() {
        composeRule.onNodeWithText("Files", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("New workspace", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
    }

    @Test
    fun backNavigatesOneDestinationAtATimeAndConfirmsExitAtRoot() {
        composeRule.onNodeWithText("Files", useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Create a workspace", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("DevForge AI", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Exit DevForge?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).assertIsDisplayed()
    }
}
