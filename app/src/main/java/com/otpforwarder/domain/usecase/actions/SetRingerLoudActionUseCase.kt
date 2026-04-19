package com.otpforwarder.domain.usecase.actions

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forces the phone into a loud ringer state.
 *
 * Exits Do Not Disturb first when possible (requires the Notification Policy
 * special access), then flips the ringer to normal mode and raises the ring
 * stream to its maximum. When Notification Policy access is not granted the
 * DND bypass step is silently skipped — the ringer change still runs.
 */
fun interface SetRingerLoudAction {
    operator fun invoke(): SetRingerLoudResult
}

data class SetRingerLoudResult(val ringerChanged: Boolean, val bypassedDnd: Boolean) {
    val success: Boolean get() = ringerChanged
}

@Singleton
class SetRingerLoudActionUseCase @Inject constructor(
    private val audioManager: AudioManager,
    private val notificationManager: NotificationManager
) : SetRingerLoudAction {

    override fun invoke(): SetRingerLoudResult {
        val bypassedDnd = tryBypassDnd()
        val ringerChanged = runCatching {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, max, 0)
        }.onFailure { Log.w(TAG, "Failed to raise ringer volume", it) }.isSuccess
        return SetRingerLoudResult(ringerChanged = ringerChanged, bypassedDnd = bypassedDnd)
    }

    private fun tryBypassDnd(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (!notificationManager.isNotificationPolicyAccessGranted) return false
        return runCatching {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }.onFailure { Log.w(TAG, "Failed to bypass DND", it) }.isSuccess
    }

    private companion object {
        const val TAG = "SetRingerLoud"
    }
}
