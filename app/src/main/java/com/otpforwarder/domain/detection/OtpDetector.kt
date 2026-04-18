package com.otpforwarder.domain.detection

import com.otpforwarder.domain.model.Otp

/**
 * Extracts an OTP code from an SMS message body if present.
 *
 * The returned [Otp] has a placeholder [Otp.type] of `UNKNOWN` —
 * classification is performed separately by [com.otpforwarder.domain.classification.OtpClassifier].
 */
interface OtpDetector {
    fun detect(sender: String, body: String): Otp?
}
