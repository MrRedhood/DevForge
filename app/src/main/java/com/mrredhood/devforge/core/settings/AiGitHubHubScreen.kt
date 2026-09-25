package com.mrredhood.devforge.core.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrredhood.devforge.core.ai.AISettingsScreen
import com.mrredhood.devforge.core.agent.ToolSettingsScreen
import com.mrredhood.devforge.core.build.BuildViewModel
import com.mrredhood.devforge.core.github.GitHubRepositoryCreationScreen
import com.mrredhood.devforge.core.github.GitHubRepositoryScreen

private enum class AiGitHubSection {
    HOME,
    GITHUB,
    CREATE_REPOSITORY,
    MODELS,
    TOOLS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGitHubHubScreen(
    buildViewModel: BuildViewModel,
    onBack: () -> Unit,
) {
    var sectionName by rememberSaveable { mutableStateOf(AiGitHubSection.HOME.name) }
    val section = runCatching { AiGitHubSection.valueOf(sectionName) }.getOrDefault(AiGitHubSection.HOME)

    fun closeChild() {
        sectionName = AiGitHubSection.HOME.name
    }

    BackHandler(enabled = section != AiGitHubSection.HOME) { closeChild() }

    when (section) {
        AiGitHubSection.HOME -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI & GitHub", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("←", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("ai-github-hub-list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Choose one area. Each opens its own dedicated screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    HubTile(
                        icon = Icons.Default.Source,
                        title = "GitHub",
                        subtitle = "Save a GitHub access token and check its connection status.",
                    ) { sectionName = AiGitHubSection.GITHUB.name }
                }
                item {
                    HubTile(
                        icon = Icons.Default.Source,
                        title = "Create GitHub repository",
                        subtitle = "Create a new repository directly in GitHub from DevForge.",
                    ) { sectionName = AiGitHubSection.CREATE_REPOSITORY.name }
                }
                item {
                    HubTile(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI Models",
                        subtitle = "Save an AI provider API key and test the connection.",
                    ) { sectionName = AiGitHubSection.MODELS.name }
                }
                item {
                    HubTile(
                        icon = Icons.Default.Code,
                        title = "AI Tools",
                        subtitle = "Tools used by the single main AI, including workspace file and folder operations.",
                    ) { sectionName = AiGitHubSection.TOOLS.name }
                }
            }
        }

        AiGitHubSection.GITHUB -> ChildSurface("GitHub", ::closeChild) {
            GitHubRepositoryScreen(
                buildViewModel = buildViewModel,
                onBack = ::closeChild,
            )
        }

        AiGitHubSection.CREATE_REPOSITORY -> ChildSurface("Create GitHub repository", ::closeChild) {
            GitHubRepositoryCreationScreen(
                onBack = ::closeChild,
                onCreated = { closeChild() },
            )
        }

        AiGitHubSection.MODELS -> ChildSurface("AI Models", ::closeChild) {
            AISettingsScreen()
        }

        AiGitHubSection.TOOLS -> ToolSettingsScreen(onClose = ::closeChild)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildSurface(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(padding),
        ) {
            content()
        }
    }
}

@Composable
private fun HubTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
