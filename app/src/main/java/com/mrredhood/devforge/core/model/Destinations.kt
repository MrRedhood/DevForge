package com.mrredhood.devforge.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stable primary destinations. Contextual surfaces such as Activity, Editor and
 * Diagnostics remain out of permanent navigation; approvals, diffs and automations are primary review/control surfaces.
 */
enum class DevForgeDestination(
    val label: String,
    val icon: ImageVector,
) {
    Files("Files", Icons.Default.Description),
    Git("Git", Icons.Default.Source),
    Diffs("Diffs", Icons.Default.Description),
    Build("Build", Icons.Default.Build),
    LiveActions("Live Actions", Icons.Default.Refresh),
    Connections("AI & GitHub", Icons.Default.Source),
    Terminal("Terminal", Icons.Default.Terminal),
    Approvals("Approvals", Icons.Default.Security),
    Settings("Settings", Icons.Default.Settings),
    More("More", Icons.Default.Menu),
}
