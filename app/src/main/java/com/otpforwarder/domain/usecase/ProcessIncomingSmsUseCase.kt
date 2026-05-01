package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.engine.RuleEngine
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.OtpLogEntry
import com.otpforwarder.domain.repository.OtpLogRepository
import com.otpforwarder.domain.repository.RecipientRepository
import com.otpforwarder.domain.usecase.actions.ExecuteRuleActionsUseCase
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single inbound SMS through the full pipeline:
 *   detect (optional) → classify (only when detected) → match rules → dispatch actions → log.
 *
 * Rules are evaluated against every SMS, OTP or not, so sender / body keyword
 * rules can fire on plain texts. Messages that match no rule produce no log
 * entry. Every matching rule produces its own log entry; forward sends are
 * deduped across rules via a shared `alreadySentTo` set so one recipient never
 * receives the same message twice.
 */
@Singleton
class ProcessIncomingSmsUseCase @Inject constructor(
    private val detector: OtpDetector,
    private val classifier: OtpClassifier,
    private val ruleEngine: RuleEngine,
    private val executeRuleActions: ExecuteRuleActionsUseCase,
    private val recipientRepository: RecipientRepository,
    private val otpLogRepository: OtpLogRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(sender: String, body: String): Result {
        val now = Instant.now(clock)
        val detected = detector.detect(sender, body)
        val otp = if (detected != null) {
            val (type, tier) = classifier.classify(sender, body)
            detected.copy(type = type, classifierTier = tier)
        } else {
            null
        }
        val sms = IncomingSms(sender = sender, body = body, otp = otp, receivedAt = now)

        val matchingRules = ruleEngine.evaluate(sms)
        if (matchingRules.isEmpty()) return Result.NoMatchingRule(sms)

        val recipientsById = recipientRepository.getActiveRecipients().associateBy { it.id }
        val alreadySentTo = mutableSetOf<Long>()
        val forwardedRecipients = linkedSetOf<String>()
        for (rule in matchingRules) {
            val outcomes = executeRuleActions(sms, rule.actions, recipientsById, alreadySentTo)
            outcomes.forEach { outcome ->
                if (outcome.status == ExecuteRuleActionsUseCase.ActionOutcome.Status.SUCCESS) {
                    forwardedRecipients += outcome.forwardedRecipientNames
                }
            }
            val anySuccess = outcomes.any { it.status == ExecuteRuleActionsUseCase.ActionOutcome.Status.SUCCESS }
            val anyFailed = outcomes.any { it.status == ExecuteRuleActionsUseCase.ActionOutcome.Status.FAILED }
            val status = when {
                outcomes.isEmpty() -> STATUS_FAILED
                anySuccess && anyFailed -> STATUS_PARTIAL
                anySuccess -> STATUS_SENT
                anyFailed -> STATUS_FAILED
                else -> STATUS_SKIPPED
            }
            otpLogRepository.insertLog(
                OtpLogEntry(
                    id = 0,
                    code = otp?.code,
                    otpType = otp?.type,
                    sender = sender,
                    originalMessage = body,
                    detectedAt = otp?.detectedAt ?: now,
                    confidence = otp?.confidence,
                    classifierTier = otp?.classifierTier,
                    ruleName = rule.name,
                    summaryLines = outcomes.map { it.summary },
                    status = status,
                    forwardedAt = now
                )
            )
        }
        return Result.Forwarded(sms, matchingRules.size, forwardedRecipients.toList())
    }

    sealed interface Result {
        /** No forwarding rule matched the SMS. */
        data class NoMatchingRule(val sms: IncomingSms) : Result

        /**
         * At least one rule matched. [recipients] lists the recipient names
         * that actually received the forwarded SMS (empty if every matching
         * rule fired only non-forward actions, e.g. ring loud / call).
         */
        data class Forwarded(
            val sms: IncomingSms,
            val ruleCount: Int,
            val recipients: List<String>
        ) : Result
    }

    companion object {
        const val STATUS_SENT = "Sent"
        const val STATUS_FAILED = "Failed"
        const val STATUS_PARTIAL = "Partial"
        const val STATUS_SKIPPED = "Skipped"
    }
}
