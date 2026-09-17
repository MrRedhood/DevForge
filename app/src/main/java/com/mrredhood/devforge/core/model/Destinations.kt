package com.mrredhood.devforge.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stable primary destinations. Contextual surfaces such as Activity, Editor and
 * Diagnostics remain out of permanent navigation; approvals and diffs are primary review surfaces.
 */
enum class DevForgeDestination(
    val label: String,
    val icon: ImageVector,
) {
    Chat("Chat", Icons.Default.ChatBubble),
    Files("Files", Icons.Default.Description),
    Git("Git", Icons.Default.Source),
    Diffs("Diffs", Icons.Default.Description),
    Build("Build", Icons.Default.Build),
    Approvals("Approvals", Icons.Default.Security),
    Settings("Settings", Icons.Default.Settings),
}
