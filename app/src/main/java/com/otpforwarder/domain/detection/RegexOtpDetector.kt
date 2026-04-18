package com.otpforwarder.domain.detection

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-layer regex-based OTP detector.
 *
 * Layer 1: cheap keyword pre-filter rejecting messages that clearly are not OTPs.
 * Layer 2: tiered regex extraction producing a confidence score:
 *  - 0.95 explicit OTP pattern (e.g. "OTP is 123456")
 *  - 0.75 contextual pattern  (e.g. "code is 123456")
 *  - 0.50 bare numeric code in a message that passed the keyword pre-filter
 */
@Singleton
class RegexOtpDetector(
    private val clock: Clock
) : OtpDetector {

    @Inject
    constructor() : this(Clock.systemUTC())

    override fun detect(sender: String, body: String): Otp? {
        if (!hasKeyword(body)) return null

        val (code, confidence) = extract(body) ?: return null

        return Otp(
            code = code,
            type = OtpType.UNKNOWN,
            sender = sender,
            originalMessage = body,
            detectedAt = Instant.now(clock),
            confidence = confidence,
            classifierTier = ClassifierTier.KEYWORD
        )
    }

    private fun hasKeyword(body: String): Boolean =
        KEYWORD_REGEX.containsMatchIn(body)

    private fun extract(body: String): Pair<String, Double>? {
        EXPLICIT_REGEX.find(body)?.groupValues?.get(1)?.let { return it to 0.95 }
        CONTEXTUAL_REGEX.find(body)?.groupValues?.get(1)?.let { return it to 0.75 }
        BARE_CODE_REGEX.find(body)?.value?.let { return it to 0.50 }
        return null
    }

    private companion object {
        private val KEYWORD_REGEX = Regex(
            "\\b(otp|code|verify|verification|pin|one[-\\s]?time|passcode)\\b",
            RegexOption.IGNORE_CASE
        )

        /** "OTP is 123456", "OTP: 123456", "Your one-time password is 482910" */
        private val EXPLICIT_REGEX = Regex(
            "\\b(?:otp|one[-\\s]?time\\s*(?:password|passcode|pin|code))\\b[^0-9\\n]{0,20}(\\d{4,8})\\b",
            RegexOption.IGNORE_CASE
        )

        /** "code is 482910", "verification code 482910", "PIN: 4821", "Verify 482910" */
        private val CONTEXTUAL_REGEX = Regex(
            "\\b(?:verification\\s*code|verification|verify|code|passcode|pin)\\b[^0-9\\n]{0,20}(\\d{4,8})\\b",
            RegexOption.IGNORE_CASE
        )

        /** First standalone numeric run of 4-8 digits (requires keyword pre-filter). */
        private val BARE_CODE_REGEX = Regex("\\b\\d{4,8}\\b")
    }
}
