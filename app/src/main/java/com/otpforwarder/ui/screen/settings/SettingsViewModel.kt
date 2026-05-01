package com.otpforwarder.ui.screen.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.domain.classification.GeminiAvailability
import com.otpforwarder.domain.classification.GeminiRuntime
import com.otpforwarder.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val geminiRuntime: GeminiRuntime
) : ViewModel() {

    private val permissionState = MutableStateFlow(readPermissionState())
    private val geminiState = MutableStateFlow(GeminiAvailability.Unknown)

    val uiState: StateFlow<SettingsUiState> = combine(
        permissionState,
        geminiState,
        geminiRuntime.downloadProgress
    ) { perms, gemini, progress ->
        SettingsUiState(
            permissions = perms,
            gemini = gemini,
            downloadProgress = progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        refreshGeminiAvailability()
    }

    fun refreshPermissionState() {
        permissionState.value = readPermissionState()
    }

    fun refreshGeminiAvailability() {
        viewModelScope.launch {
            geminiState.value = geminiRuntime.status()
        }
    }

    fun downloadGemini() {
        viewModelScope.launch {
            geminiRuntime.startDownload()
            geminiState.value = geminiRuntime.status()
        }
    }

    fun openAppSettings() {
        permissionHelper.openAppSettings()
    }

    fun openNotificationPolicySettings() {
        permissionHelper.openNotificationPolicySettings()
    }

    private fun readPermissionState(): PermissionState = PermissionState(
        receiveSms = permissionHelper.hasReceiveSms(),
        sendSms = permissionHelper.hasSendSms(),
        notifications = permissionHelper.hasPostNotifications(),
        notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        callPhone = permissionHelper.hasCallPhone(),
        notificationPolicy = permissionHelper.hasNotificationPolicyAccess()
    )

    data class SettingsUiState(
        val permissions: PermissionState = PermissionState(),
        val gemini: GeminiAvailability = GeminiAvailability.Unknown,
        val downloadProgress: Float? = null
    ) {
        val canDownloadGemini: Boolean
            get() = gemini == GeminiAvailability.Downloadable && downloadProgress == null
        val isGeminiReady: Boolean get() = gemini == GeminiAvailability.Ready
    }

    data class PermissionState(
        val receiveSms: Boolean = false,
        val sendSms: Boolean = false,
        val notifications: Boolean = true,
        val notificationsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        val callPhone: Boolean = false,
        val notificationPolicy: Boolean = false
    )
}

internal fun GeminiAvailability.label(progress: Float?): String = when (this) {
    GeminiAvailability.Unknown -> "Checking…"
    GeminiAvailability.Unsupported -> "Unavailable (device or region not supported)"
    GeminiAvailability.Downloadable -> "Available — model not downloaded"
    GeminiAvailability.Downloading -> {
        val pct = progress?.let { "${(it * 100).toInt()}%" } ?: "in progress"
        "Downloading model ($pct)"
    }
    GeminiAvailability.Ready -> "Ready"
}
