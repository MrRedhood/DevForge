package com.mrredhood.devforge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun backNavigatesOneDestinationAtATimeAndConfirmsExitAtRoot() {
        composeRule.onNodeWithText("Files").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Workspace browser", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("DevForge AI", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Exit DevForge?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).assertIsDisplayed()
    }
}
