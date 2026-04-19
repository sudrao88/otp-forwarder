package com.otpforwarder.ui.screen.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.otpforwarder.util.PermissionHelper

@Composable
fun PermissionOnboardingScreen(
    onGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }
    var showOpenSettings by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        if (hasAllRequired(context, permissions)) {
            onGranted()
        }
        onPauseOrDispose {}
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasAllRequired(context, permissions)) {
            onGranted()
        } else {
            val activity = context as? Activity
            showOpenSettings = activity == null ||
                permissions.any { (perm, _) -> !activity.shouldShowRationale(perm) && !isGranted(context, perm) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "OTP Forwarder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "This app needs permission to read and send SMS to forward your OTPs.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    permissions.forEach { (_, label) ->
                        Text(
                            text = "\u2022 $label",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (showOpenSettings) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Some permissions are blocked. Enable them in system settings to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { launcher.launch(permissions.map { it.first }.toTypedArray()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }
            if (showOpenSettings) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { PermissionHelper.openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Settings")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun requiredPermissions(): List<Pair<String, String>> = buildList {
    add(Manifest.permission.RECEIVE_SMS to "Receive SMS")
    add(Manifest.permission.SEND_SMS to "Send SMS")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS to "Notifications")
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasAllRequired(context: Context, permissions: List<Pair<String, String>>): Boolean =
    permissions.all { (perm, _) -> isGranted(context, perm) }

private fun Activity.shouldShowRationale(permission: String): Boolean =
    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
