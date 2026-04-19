package com.otpforwarder.domain.usecase.actions

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

data class SetRingerLoudResult(
    val ringerChanged: Boolean,
    val bypassedDnd: Boolean,
    val dndWasActive: Boolean = false
) {
    // Truthful: if DND was active and we couldn't bypass it, the ringer flip was silenced anyway.
    val success: Boolean get() = ringerChanged && (!dndWasActive || bypassedDnd)
}

@Singleton
class SetRingerLoudActionUseCase @Inject constructor(
    private val ringerSystem: RingerSystem
) : SetRingerLoudAction {

    override fun invoke(): SetRingerLoudResult {
        val dndWasActive = ringerSystem.isDndActive()
        val bypassedDnd = ringerSystem.canBypassDnd() && ringerSystem.bypassDnd()
        val ringerChanged = ringerSystem.setRingerModeNormal()
        ringerSystem.raiseRingerVolumeToMax()
        return SetRingerLoudResult(ringerChanged, bypassedDnd, dndWasActive)
    }
}
