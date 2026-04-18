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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otpforwarder.data.mapper.OtpLogEntry
import com.otpforwarder.domain.usecase.ProcessIncomingSmsUseCase
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OTP Forwarder") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
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
                    }
                }
            )
        }
    ) { inner ->
        if (state.logs.isEmpty()) {
            EmptyHome(modifier = Modifier.padding(inner))
        } else {
            val grouped = state.logs.groupBy { dayBucket(it.forwardedAt) }
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
                        OtpLogCard(entry = entry, onRetry = { viewModel.retry(entry) })
                    }
                }
            }
        }
    }
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
                text = "No OTPs forwarded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Nothing in the last 12 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OtpLogCard(
    entry: OtpLogEntry,
    onRetry: () -> Unit,
) {
    val failed = entry.status == ProcessIncomingSmsUseCase.STATUS_FAILED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(status = entry.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.sender,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(entry.forwardedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Code: ${entry.code}",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${entry.otpType.name} → ${entry.recipientNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusLabel(entry.status, failed),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor(entry.status)
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
    ProcessIncomingSmsUseCase.STATUS_SENT -> MaterialTheme.colorScheme.primary
    ProcessIncomingSmsUseCase.STATUS_FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

private fun statusLabel(status: String, failed: Boolean): String = when {
    failed -> "\u2717 Failed (tap to retry)"
    status == ProcessIncomingSmsUseCase.STATUS_SENT -> "\u2713 Sent"
    status == ProcessIncomingSmsUseCase.STATUS_PARTIAL -> "\u25D1 Partial"
    else -> status
}
