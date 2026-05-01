package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.detection.MapsLinkDetector
import com.otpforwarder.domain.model.IncomingSms
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reacts to a matched rule by surfacing a tap-to-navigate notification for the
 * Google Maps URL embedded in the inbound SMS.
 *
 * Detection is delegated to [MapsLinkDetector]; if the body contains no
 * recognised Maps URL the action is reported as skipped (the action can be
 * configured on a rule that also matches non-Maps SMS via other conditions —
 * skipping gracefully avoids surprising the user with an empty notification).
 *
 * Phase 3 ignores [com.otpforwarder.domain.model.RuleAction.OpenMapsNavigation.autoLaunch]:
 * the notification path is the only UX wired here. Phase 4 attaches a
 * full-screen intent when the flag is on.
 */
fun interface OpenMapsAction {
    operator fun invoke(sms: IncomingSms): OpenMapsResult
}

data class OpenMapsResult(
    val mapsUrl: String?,
    val posted: Boolean
) {
    val skipped: Boolean get() = mapsUrl == null
    val success: Boolean get() = mapsUrl != null && posted
}

@Singleton
class OpenMapsActionUseCase @Inject constructor(
    private val mapsNotifier: MapsNotifier
) : OpenMapsAction {

    override fun invoke(sms: IncomingSms): OpenMapsResult {
        val url = MapsLinkDetector.findMapsLink(sms.body)
            ?: return OpenMapsResult(mapsUrl = null, posted = false)
        val posted = mapsNotifier.notifyNavigation(sms.sender, url)
        return OpenMapsResult(mapsUrl = url, posted = posted)
    }
}
