package com.otpforwarder.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionState()
        viewModel.refreshGeminiAvailability()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Permissions")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PermissionRow(
                        label = "Receive SMS",
                        granted = state.permissions.receiveSms,
                        onClick = viewModel::openAppSettings
                    )
                    HorizontalDivider()
                    PermissionRow(
                        label = "Send SMS",
                        granted = state.permissions.sendSms,
                        onClick = viewModel::openAppSettings
                    )
                    if (state.permissions.notificationsSupported) {
                        HorizontalDivider()
                        PermissionRow(
                            label = "Notifications",
                            granted = state.permissions.notifications,
                            onClick = viewModel::openAppSettings
                        )
                    }
                }
            }

            SectionTitle("Classification")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val activeTier = if (state.gemini.isReady) "Gemini Nano" else "Keyword Scoring"
                    KeyValueRow("Active", activeTier)
                    Spacer(Modifier.height(4.dp))
                    KeyValueRow("Gemini Nano", state.gemini.label)
                }
            }

            SectionTitle("About")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    KeyValueRow("Version", "1.0.0")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OTP Forwarder processes SMS entirely on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!granted) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "\u2713 Granted" else "\u2717 Denied",
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
