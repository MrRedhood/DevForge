package com.mrredhood.devforge

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.espresso.Espresso.pressBack
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private fun waitForNode(description: String, timeoutMillis: Long = 10_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                composeRule
                    .onNodeWithContentDescription(description, useUnmergedTree = true)
                    .assertExists()
            }.isSuccess
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                composeRule
                    .onNodeWithText(text, useUnmergedTree = true)
                    .assertExists()
            }.isSuccess
        }
    }

    private fun closeChatIfOpen() {
        runCatching {
            composeRule
                .onNodeWithContentDescription("Close AI Chat", useUnmergedTree = true)
                .performClick()
        }
    }

    private fun resetToEditorHome() {
        closeChatIfOpen()
        repeat(4) {
            if (runCatching {
                    composeRule
                        .onNodeWithContentDescription("Editor navigation", useUnmergedTree = true)
                        .assertExists()
                }.isSuccess
            ) return
            pressBack()
        }
        waitForNode("Editor navigation")
    }

    @Test
    fun editorIsPrimaryBottomBarDestinationAndWorkspaceMenuIsWorkspaceFocused() {
        waitForNode("Editor navigation")
        composeRule.onNodeWithContentDescription("Editor navigation", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Workspace menu", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Workspaces", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("GitHub", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun chatProviderCanBeChangedWithoutOpeningSettings() {
        waitForNode("AI Chat")
        composeRule.onNodeWithContentDescription("AI Chat", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Google Gemini", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Anthropic Claude", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Anthropic Claude", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun chatFullscreenDoesNotExposeAgentControls() {
        waitForNode("AI Chat")
        composeRule.onNodeWithContentDescription("AI Chat", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("Agents", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Single main AI · plans, searches, edits and executes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("DevForge AI", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aiToolsIsAStandaloneManagementScreen() {
        resetToEditorHome()
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("more-screen-list", useUnmergedTree = true).performScrollToIndex(5)
        waitForText("AI & GitHub")
        composeRule.onNodeWithText("AI & GitHub", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("ai-github-hub-list", useUnmergedTree = true).performScrollToIndex(3)
        waitForText("AI Tools")
        composeRule.onNodeWithText("AI Tools", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Main AI execution controls", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("tool-settings-list", useUnmergedTree = true).performScrollToIndex(28)
        composeRule.onNodeWithText("Enable coding tools", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aiAndGitHubHubIsAvailableFromMore() {
        resetToEditorHome()
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("more-screen-list", useUnmergedTree = true).performScrollToIndex(5)
        waitForText("AI & GitHub")
        composeRule.onNodeWithText("AI & GitHub", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("GitHub", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("AI Models", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("AI Tools", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun extensionsScreenProvidesVisibleSearchAndInstallActions() {
        resetToEditorHome()
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("more-screen-list", useUnmergedTree = true).performScrollToIndex(4)
        waitForText("Extensions")
        composeRule.onNodeWithText("Extensions", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Extensions", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Search installed extensions", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Install ZIP / VSIX", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun settingsDoesNotExposeLegacyAiProviderEntry() {
        resetToEditorHome()
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("AI & GitHub", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("AI providers", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("AI Providers", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun moreContainsSingleSettingsEntryAndSettingsHidesPrimaryNavigation() {
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("Files navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        composeRule.onAllNodesWithText("Settings", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aiChatIsOpenedOnlyThroughFloatingAction() {
        waitForNode("AI Chat")
        composeRule.onNodeWithContentDescription("AI Chat", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("AI Chat", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun filesShowsWorkspaceCreationUi() {
        waitForNode("Files navigation")
        composeRule.onNodeWithContentDescription("Files navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Add workspace", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Name", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
    }

    @Test
    fun moreBackReturnsToMainMenuBeforeExitConfirmation() {
        waitForNode("More navigation")
        composeRule.onNodeWithContentDescription("More navigation", useUnmergedTree = true).performClick()

        pressBack()

        waitForNode("Editor navigation")
        composeRule.onNodeWithContentDescription("Editor navigation", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Exit DevForge?", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun backNavigatesOneDestinationAtATimeAndConfirmsExitAtRoot() {
        waitForNode("Files navigation")
        composeRule.onNodeWithContentDescription("Files navigation", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Create a workspace", useUnmergedTree = true).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Exit DevForge?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).assertIsDisplayed()
    }
}
