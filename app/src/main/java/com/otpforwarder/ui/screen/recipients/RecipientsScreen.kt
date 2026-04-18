package com.otpforwarder.ui.screen.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otpforwarder.domain.model.Recipient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsScreen(
    onAddRecipient: () -> Unit,
    onEditRecipient: (Long) -> Unit,
    viewModel: RecipientsViewModel = hiltViewModel()
) {
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recipients") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipient) {
                Icon(Icons.Default.Add, contentDescription = "Add recipient")
            }
        }
    ) { inner ->
        if (recipients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recipients yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recipients, key = { it.recipient.id }) { item ->
                    RecipientCard(
                        recipient = item.recipient,
                        ruleNames = item.ruleNames,
                        onToggle = { viewModel.setActive(item.recipient, it) },
                        onClick = { onEditRecipient(item.recipient.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientCard(
    recipient: Recipient,
    ruleNames: List<String>,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recipient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = recipient.isActive, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = recipient.phoneNumber,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Rules: ${ruleNames.ifEmpty { listOf("none") }.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
