package com.mrredhood.devforge.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stable primary destinations. Contextual surfaces such as Activity, Editor and
 * Diagnostics are deliberately kept out of permanent navigation for a focused mobile IA.
 */
enum class DevForgeDestination(
    val label: String,
    val icon: ImageVector,
) {
    Chat("Chat", Icons.Default.ChatBubble),
    Files("Files", Icons.Default.Description),
    Git("Git", Icons.Default.Source),
    Build("Build", Icons.Default.Build),
    Settings("Settings", Icons.Default.Settings),
}
