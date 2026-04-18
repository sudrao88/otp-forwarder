package com.otpforwarder.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.data.mapper.OtpLogEntry
import com.otpforwarder.data.settings.SettingsRepository
import com.otpforwarder.domain.usecase.ProcessIncomingSmsUseCase
import com.otpforwarder.domain.repository.OtpLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val otpLogRepository: OtpLogRepository,
    private val processIncomingSms: ProcessIncomingSmsUseCase,
    private val clock: Clock
) : ViewModel() {

    private val tick = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        settings.masterEnabled,
        tick.flatMapLatest {
            otpLogRepository.getRecentLogs(clock.instant().minus(WINDOW).toEpochMilli())
        }
    ) { enabled, logs ->
        HomeUiState(
            masterEnabled = enabled,
            logs = logs.sortedByDescending { it.forwardedAt }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_IN_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            otpLogRepository.pruneOldLogs(clock.instant().minus(WINDOW).toEpochMilli())
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        settings.setMasterEnabled(enabled)
    }

    fun retry(entry: OtpLogEntry) {
        viewModelScope.launch {
            // Re-run the full pipeline against the original SMS. On success the
            // original failed log stays in place; a new log entry reflects the
            // retry outcome. The "tick" refresh keeps the list in sync.
            runCatching { processIncomingSms(entry.sender, entry.originalMessage) }
            tick.value = tick.value + 1
        }
    }

    data class HomeUiState(
        val masterEnabled: Boolean = true,
        val logs: List<OtpLogEntry> = emptyList()
    )

    companion object {
        private val WINDOW: Duration = Duration.ofHours(12)
        private const val STATE_IN_TIMEOUT_MS = 5_000L
    }
}
