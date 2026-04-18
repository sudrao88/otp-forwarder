package com.otpforwarder.ui.screen.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.data.settings.SettingsRepository
import com.otpforwarder.domain.classification.GeminiOtpClassifier
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
    private val settings: SettingsRepository,
    private val permissionHelper: PermissionHelper,
    private val geminiClassifier: GeminiOtpClassifier
) : ViewModel() {

    private val permissionState = MutableStateFlow(readPermissionState())
    private val geminiAvailability = MutableStateFlow(GeminiAvailability.Unknown)

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.includeOriginalMessage,
        permissionState,
        geminiAvailability
    ) { includeOriginal, perms, gemini ->
        SettingsUiState(
            permissions = perms,
            gemini = gemini,
            includeOriginalMessage = includeOriginal,
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
            geminiAvailability.value = if (geminiClassifier.isAvailable()) {
                GeminiAvailability.Available
            } else {
                GeminiAvailability.Unavailable
            }
        }
    }

    fun setIncludeOriginalMessage(enabled: Boolean) {
        settings.setIncludeOriginalMessage(enabled)
    }

    fun openAppSettings() {
        permissionHelper.openAppSettings()
    }

    private fun readPermissionState(): PermissionState = PermissionState(
        receiveSms = permissionHelper.hasReceiveSms(),
        sendSms = permissionHelper.hasSendSms(),
        notifications = permissionHelper.hasPostNotifications(),
        notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    )

    data class SettingsUiState(
        val permissions: PermissionState = PermissionState(),
        val gemini: GeminiAvailability = GeminiAvailability.Unknown,
        val includeOriginalMessage: Boolean = true
    )

    data class PermissionState(
        val receiveSms: Boolean = false,
        val sendSms: Boolean = false,
        val notifications: Boolean = true,
        val notificationsSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    )

    enum class GeminiAvailability {
        Unknown, Available, Unavailable;

        val label: String get() = when (this) {
            Unknown -> "Checking…"
            Available -> "Available"
            Unavailable -> "Unavailable (device not supported)"
        }

        val isReady: Boolean get() = this == Available
    }
}
