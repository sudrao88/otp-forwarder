package com.otpforwarder.ui.screen.recipients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipientScreen(
    onBack: () -> Unit,
    onAddNewRule: (recipientId: Long) -> Unit,
    viewModel: EditRecipientViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Recipient" else "Add Recipient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(
                            onClick = viewModel::requestDelete,
                            enabled = !state.inFlight
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete recipient")
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = viewModel::setPhone,
                label = { Text("Phone Number") },
                isError = state.phoneError != null,
                supportingText = { state.phoneError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Assigned Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.allRules.isEmpty()) {
                Text(
                    text = "No rules yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.allRules.forEach { rule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rule.id in state.selectedRuleIds,
                        onCheckedChange = { viewModel.toggleRule(rule.id) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${rule.otpType.name} \u00B7 priority ${rule.priority}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            TextButton(
                onClick = {
                    viewModel.save { id -> onAddNewRule(id) }
                },
                enabled = !state.inFlight
            ) {
                Text("+ Add new rule")
            }

            Button(
                onClick = { viewModel.save { onBack() } },
                enabled = !state.inFlight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Recipient")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete recipient?") },
            text = { Text("This will remove ${state.name.ifBlank { "this recipient" }}. Rules that forward to them will stay, but will no longer deliver to this number.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }
}
