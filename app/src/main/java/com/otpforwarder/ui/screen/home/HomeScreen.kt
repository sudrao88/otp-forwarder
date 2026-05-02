package com.otpforwarder.ui.screen.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otpforwarder.domain.model.ReceivedSms
import com.otpforwarder.domain.model.ReceivedSmsStatus
import com.otpforwarder.ui.util.DayBucket
import com.otpforwarder.ui.util.dayBucket
import com.otpforwarder.ui.util.formatRelativeTime
import com.otpforwarder.ui.util.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.retryEvents.collect { event ->
            val message = when (event) {
                HomeViewModel.RetryEvent.Succeeded -> "Retry succeeded"
                HomeViewModel.RetryEvent.Failed -> "Retry failed — will try again automatically"
                HomeViewModel.RetryEvent.NoMatch -> "Retry produced no forwarding match"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OTP Forwarder") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (state.masterEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = state.masterEnabled,
                            onCheckedChange = viewModel::setMasterEnabled
                        )
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear feed") },
                                enabled = state.entries.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        if (state.entries.isEmpty()) {
            EmptyHome(modifier = Modifier.padding(inner))
        } else {
            val grouped = state.entries.groupBy { dayBucket(it.receivedAt) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "summary") {
                    FeedSummary(matched = state.matchedCount, unmatched = state.unmatchedCount)
                }
                DayBucket.entries.forEach { bucket ->
                    val entries = grouped[bucket].orEmpty()
                    if (entries.isEmpty()) return@forEach
                    item(key = "header-${bucket.name}") {
                        Text(
                            text = bucket.label(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        ReceivedSmsCard(entry = entry, onRetry = { viewModel.retry(entry) })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear feed?") },
            text = { Text("This deletes every recorded message. Forwarding rules are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearFeed()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FeedSummary(matched: Int, unmatched: Int) {
    Text(
        text = "$matched forwarded · $unmatched not matched",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun EmptyHome(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Incoming SMS will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReceivedSmsCard(
    entry: ReceivedSms,
    onRetry: () -> Unit,
) {
    val matched = HomeViewModel.isMatched(entry.processingStatus)
    val failed = entry.processingStatus == ReceivedSmsStatus.FAILED
    val containerColor = when {
        matched -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status = entry.processingStatus)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(entry.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.otpCode != null || entry.otpType != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (entry.otpCode != null) append("Code ${entry.otpCode}")
                        if (entry.otpType != null) {
                            if (isNotEmpty()) append(" · ")
                            append(entry.otpType.name)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusLabel(entry, failed),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor(entry.processingStatus)
            )
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusColor(status))
    )
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    ReceivedSmsStatus.FORWARDED -> MaterialTheme.colorScheme.primary
    ReceivedSmsStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
    ReceivedSmsStatus.FAILED, ReceivedSmsStatus.ERROR -> MaterialTheme.colorScheme.error
    ReceivedSmsStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
    ReceivedSmsStatus.NO_MATCH -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.outline
}

private fun statusLabel(entry: ReceivedSms, failed: Boolean): String = when {
    failed -> "✗ Failed (tap to retry)"
    entry.processingStatus == ReceivedSmsStatus.FORWARDED ->
        if (entry.forwardedRecipients.isEmpty()) "✓ Rule fired"
        else "✓ Sent to ${entry.forwardedRecipients.joinToString(", ")}"
    entry.processingStatus == ReceivedSmsStatus.PARTIAL ->
        "◑ Partial — ${entry.forwardedRecipients.joinToString(", ")}"
    entry.processingStatus == ReceivedSmsStatus.SKIPPED -> "⏭ Skipped"
    entry.processingStatus == ReceivedSmsStatus.NO_MATCH -> "No matching rule"
    entry.processingStatus == ReceivedSmsStatus.PENDING -> "Processing…"
    entry.processingStatus == ReceivedSmsStatus.ERROR -> "Pipeline error"
    else -> entry.processingStatus
}
