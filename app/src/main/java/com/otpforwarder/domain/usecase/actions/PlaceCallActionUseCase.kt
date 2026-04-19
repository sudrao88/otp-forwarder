package com.otpforwarder.domain.usecase.actions

import android.util.Log
import com.otpforwarder.domain.model.Recipient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places a silent outbound call to the given [Recipient] via [TelecomSystem].
 *
 * Works from the foreground service without an activity because `placeCall`
 * just hands the dial intent to the telephony stack and returns; the call
 * continues independent of the caller. A missing `CALL_PHONE` permission is
 * reported as a failure rather than throwing — per-action isolation lets the
 * rest of the rule's actions continue.
 */
fun interface PlaceCallAction {
    operator fun invoke(recipient: Recipient): PlaceCallResult
}

data class PlaceCallResult(
    val recipient: Recipient,
    val success: Boolean,
    val reason: String?
)

@Singleton
class PlaceCallActionUseCase @Inject constructor(
    private val telecom: TelecomSystem
) : PlaceCallAction {

    override fun invoke(recipient: Recipient): PlaceCallResult {
        if (!telecom.hasCallPhonePermission()) {
            Log.w(TAG, "CALL_PHONE permission not granted; skipping call to ${recipient.name}")
            return PlaceCallResult(recipient, success = false, reason = REASON_NO_PERMISSION)
        }
        return runCatching {
            telecom.placeCall(recipient.phoneNumber)
            PlaceCallResult(recipient, success = true, reason = null)
        }.getOrElse { t ->
            Log.e(TAG, "placeCall failed for ${recipient.name}", t)
            PlaceCallResult(recipient, success = false, reason = t.message ?: REASON_UNKNOWN)
        }
    }

    private companion object {
        const val TAG = "PlaceCall"
        const val REASON_NO_PERMISSION = "CALL_PHONE not granted"
        const val REASON_UNKNOWN = "placeCall failed"
    }
}
