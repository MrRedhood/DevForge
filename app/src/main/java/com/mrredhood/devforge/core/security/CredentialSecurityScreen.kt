package com.mrredhood.devforge.core.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CredentialSecurityScreen(
    viewModel: CredentialSecurityViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val state = viewModel.state

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Credential protection", style = MaterialTheme.typography.titleLarge)
            Text(
                "Protect stored AI provider keys and the GitHub credential with strong biometric authentication.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    !state.biometricAvailable -> "Strong biometric authentication is unavailable on this device."
                    state.protectionEnabled && state.unlocked -> "Protection is enabled and credentials are currently unlocked."
                    state.protectionEnabled -> "Protection is enabled and protected credentials are locked."
                    else -> "Protection is disabled. Credentials remain encrypted by Android Keystore."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.protectionEnabled) {
                Button(
                    onClick = { activity?.let(viewModel::enableProtection) },
                    enabled = state.biometricAvailable && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable biometric protection") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { activity?.let(viewModel::unlock) },
                        enabled = activity != null && !state.unlocked,
                        modifier = Modifier.weight(1f),
                    ) { Text("Unlock") }
                    OutlinedButton(
                        onClick = viewModel::lock,
                        enabled = state.unlocked,
                        modifier = Modifier.weight(1f),
                    ) { Text("Lock now") }
                }
                OutlinedButton(
                    onClick = { activity?.let(viewModel::disableProtection) },
                    enabled = activity != null && state.unlocked,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Disable biometric protection") }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
