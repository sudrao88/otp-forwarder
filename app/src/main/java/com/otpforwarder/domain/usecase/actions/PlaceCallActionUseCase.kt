package com.otpforwarder.domain.usecase.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.otpforwarder.domain.model.Recipient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places a silent outbound call to the given [Recipient] via [TelecomManager].
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
    @ApplicationContext private val context: Context,
    private val telecomManager: TelecomManager
) : PlaceCallAction {

    override fun invoke(recipient: Recipient): PlaceCallResult {
        if (!hasCallPhonePermission()) {
            Log.w(TAG, "CALL_PHONE permission not granted; skipping call to ${recipient.name}")
            return PlaceCallResult(recipient, success = false, reason = REASON_NO_PERMISSION)
        }
        return runCatching {
            val uri = Uri.fromParts("tel", recipient.phoneNumber, null)
            telecomManager.placeCall(uri, null)
            PlaceCallResult(recipient, success = true, reason = null)
        }.getOrElse { t ->
            Log.e(TAG, "placeCall failed for ${recipient.name}", t)
            PlaceCallResult(recipient, success = false, reason = t.message ?: REASON_UNKNOWN)
        }
    }

    private fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PlaceCall"
        const val REASON_NO_PERMISSION = "CALL_PHONE not granted"
        const val REASON_UNKNOWN = "placeCall failed"
    }
}
