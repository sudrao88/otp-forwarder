package com.otpforwarder.domain.usecase.actions

import android.app.NotificationManager
import android.media.AudioManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow seam over the Android ringer/DND system services so
 * [SetRingerLoudActionUseCase] stays unit-testable on the JVM.
 */
interface RingerSystem {
    fun isDndActive(): Boolean
    fun canBypassDnd(): Boolean
    fun bypassDnd(): Boolean
    fun setRingerModeNormal(): Boolean
    fun raiseRingerVolumeToMax()
}

@Singleton
class AndroidRingerSystem @Inject constructor(
    private val audioManager: AudioManager,
    private val notificationManager: NotificationManager
) : RingerSystem {

    override fun isDndActive(): Boolean = runCatching {
        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    override fun canBypassDnd(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    override fun bypassDnd(): Boolean = runCatching {
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }.onFailure { Log.w(TAG, "Failed to bypass DND", it) }.isSuccess

    // Narrowed so that a later setStreamVolume failure doesn't erase a successful ringer-mode flip.
    override fun setRingerModeNormal(): Boolean = runCatching {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }.onFailure { Log.w(TAG, "Failed to set ringer mode", it) }.isSuccess

    override fun raiseRingerVolumeToMax() {
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, max, 0)
        }.onFailure { Log.w(TAG, "Failed to raise ringer volume", it) }
    }

    private companion object {
        const val TAG = "AndroidRingerSystem"
    }
}
