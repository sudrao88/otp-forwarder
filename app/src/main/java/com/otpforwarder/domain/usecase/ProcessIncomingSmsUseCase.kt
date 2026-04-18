package com.otpforwarder.domain.usecase

import com.otpforwarder.data.mapper.OtpLogEntry
import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.engine.RuleEngine
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.repository.OtpLogRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single inbound SMS through the full pipeline:
 *   detect → classify (tiered) → match rules → forward → log.
 *
 * Non-OTP messages short-circuit and produce no log entry. OTPs that match no
 * rule are likewise not logged (nothing was forwarded). Per matching rule, one
 * log entry is written with the aggregated per-recipient send status.
 */
@Singleton
class ProcessIncomingSmsUseCase @Inject constructor(
    private val detector: OtpDetector,
    private val classifier: OtpClassifier,
    private val ruleEngine: RuleEngine,
    private val forwardOtp: ForwardOtpUseCase,
    private val otpLogRepository: OtpLogRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(sender: String, body: String): Result {
        val detected = detector.detect(sender, body) ?: return Result.NotOtp
        val (type, tier) = classifier.classify(sender, body)
        val otp = detected.copy(type = type, classifierTier = tier)

        val plans = ruleEngine.evaluate(otp)
        if (plans.isEmpty()) return Result.NoMatchingRule(otp)

        val now = Instant.now(clock)
        for ((rule, recipients) in plans) {
            val successes = recipients.count { forwardOtp(otp, it) }
            val status = when (successes) {
                recipients.size -> STATUS_SENT
                0 -> STATUS_FAILED
                else -> STATUS_PARTIAL
            }
            otpLogRepository.insertLog(
                OtpLogEntry(
                    id = 0,
                    code = otp.code,
                    otpType = otp.type,
                    sender = otp.sender,
                    originalMessage = otp.originalMessage,
                    detectedAt = otp.detectedAt,
                    confidence = otp.confidence,
                    classifierTier = otp.classifierTier,
                    ruleName = rule.name,
                    recipientNames = recipients.map { it.name },
                    status = status,
                    forwardedAt = now
                )
            )
        }
        return Result.Forwarded(otp, plans.size)
    }

    sealed interface Result {
        /** SMS did not contain an OTP. */
        data object NotOtp : Result

        /** OTP detected but no forwarding rule matched. */
        data class NoMatchingRule(val otp: Otp) : Result

        /** OTP detected and forwarded for [ruleCount] rule(s). */
        data class Forwarded(val otp: Otp, val ruleCount: Int) : Result
    }

    companion object {
        const val STATUS_SENT = "Sent"
        const val STATUS_FAILED = "Failed"
        const val STATUS_PARTIAL = "Partial"
    }
}
