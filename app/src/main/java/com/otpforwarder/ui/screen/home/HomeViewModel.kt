package com.otpforwarder.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otpforwarder.data.settings.SettingsRepository
import com.otpforwarder.domain.model.ReceivedSms
import com.otpforwarder.domain.model.ReceivedSmsStatus
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import com.otpforwarder.domain.usecase.ProcessIncomingSmsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val receivedSmsRepository: ReceivedSmsRepository,
    private val processIncomingSms: ProcessIncomingSmsUseCase,
    private val clock: Clock
) : ViewModel() {

    private val tick = MutableStateFlow(0L)

    private val cutoffs = channelFlow {
        while (true) {
            send(clock.instant().minus(WINDOW).toEpochMilli())
            delay(CUTOFF_REFRESH_MS)
        }
    }

    private val _retryEvents = MutableSharedFlow<RetryEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val retryEvents: SharedFlow<RetryEvent> = _retryEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = combine(
        settings.masterEnabled,
        combine(tick, cutoffs) { _, cutoff -> cutoff }
            .flatMapLatest { cutoff -> receivedSmsRepository.getRecent(cutoff) }
    ) { enabled, entries ->
        HomeUiState(
            masterEnabled = enabled,
            entries = entries.sortedByDescending { it.receivedAt }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_IN_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    fun setMasterEnabled(enabled: Boolean) {
        settings.setMasterEnabled(enabled)
    }

    fun retry(entry: ReceivedSms) {
        viewModelScope.launch {
            val outcome = runCatching { processIncomingSms(entry.sender, entry.body) }
            val event = outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is ProcessIncomingSmsUseCase.Result.Forwarded -> RetryEvent.Succeeded
                        is ProcessIncomingSmsUseCase.Result.NoMatchingRule -> RetryEvent.NoMatch
                    }
                },
                onFailure = { RetryEvent.Failed }
            )
            _retryEvents.tryEmit(event)
            tick.update { it + 1 }
        }
    }

    fun clearFeed() {
        viewModelScope.launch {
            receivedSmsRepository.deleteAll()
            tick.update { it + 1 }
        }
    }

    data class HomeUiState(
        val masterEnabled: Boolean = true,
        val entries: List<ReceivedSms> = emptyList()
    ) {
        val matchedCount: Int = entries.count { isMatched(it.processingStatus) }
        val unmatchedCount: Int = entries.count { it.processingStatus == ReceivedSmsStatus.NO_MATCH }
    }

    sealed interface RetryEvent {
        data object Succeeded : RetryEvent
        data object Failed : RetryEvent
        data object NoMatch : RetryEvent
    }

    companion object {
        private val WINDOW: Duration = Duration.ofDays(30)
        private const val CUTOFF_REFRESH_MS = 60_000L
        private const val STATE_IN_TIMEOUT_MS = 5_000L

        fun isMatched(status: String): Boolean = when (status) {
            ReceivedSmsStatus.FORWARDED,
            ReceivedSmsStatus.PARTIAL,
            ReceivedSmsStatus.FAILED,
            ReceivedSmsStatus.SKIPPED -> true
            else -> false
        }
    }
}
