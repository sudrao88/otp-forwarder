package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.engine.RuleEngine
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.ReceivedSms
import com.otpforwarder.domain.model.ReceivedSmsStatus
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import com.otpforwarder.domain.repository.RecipientRepository
import com.otpforwarder.domain.usecase.actions.ExecuteRuleActionsUseCase
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates a single inbound SMS through the full pipeline:
 *   record (PENDING) → detect (optional) → classify (only when detected) →
 *   match rules → dispatch actions → finalise feed row.
 *
 * Every SMS produces exactly one [ReceivedSms] row, regardless of whether a
 * rule matched. The home screen reads this feed; matched rows surface rule +
 * recipient details, unmatched rows still appear so silent misses are visible.
 */
@Singleton
class ProcessIncomingSmsUseCase @Inject constructor(
    private val detector: OtpDetector,
    private val classifier: OtpClassifier,
    private val ruleEngine: RuleEngine,
    private val executeRuleActions: ExecuteRuleActionsUseCase,
    private val recipientRepository: RecipientRepository,
    private val receivedSmsRepository: ReceivedSmsRepository,
    private val clock: Clock
) {

    suspend operator fun invoke(
        sender: String,
        body: String,
        receivedAt: Instant = Instant.now(clock)
    ): Result {
        val pending = ReceivedSms(
            id = 0,
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            otpCode = null,
            otpType = null,
            confidence = null,
            classifierTier = null,
            processingStatus = ReceivedSmsStatus.PENDING,
            matchedRuleNames = emptyList(),
            forwardedRecipients = emptyList(),
            summary = "",
            processedAt = null
        )
        val rowId = receivedSmsRepository.insert(pending)

        val detected = detector.detect(sender, body)
        val otp = if (detected != null) {
            val (type, tier) = classifier.classify(sender, body)
            detected.copy(type = type, classifierTier = tier)
        } else {
            null
        }
        val sms = IncomingSms(sender = sender, body = body, otp = otp, receivedAt = receivedAt)

        val matchingRules = ruleEngine.evaluate(sms)

        val now = Instant.now(clock)

        if (matchingRules.isEmpty()) {
            receivedSmsRepository.update(
                pending.copy(
                    id = rowId,
                    otpCode = otp?.code,
                    otpType = otp?.type,
                    confidence = otp?.confidence,
                    classifierTier = otp?.classifierTier,
                    processingStatus = ReceivedSmsStatus.NO_MATCH,
                    summary = noMatchSummary(otp),
                    processedAt = now
                )
            )
            return Result.NoMatchingRule(sms)
        }

        val recipientsById = recipientRepository.getActiveRecipients().associateBy { it.id }
        val alreadySentTo = mutableSetOf<Long>()
        val forwardedRecipients = linkedSetOf<String>()
        val perRuleStatuses = mutableListOf<String>()
        val perRuleSummaries = mutableListOf<String>()
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
                outcomes.isEmpty() -> ReceivedSmsStatus.FAILED
                anySuccess && anyFailed -> ReceivedSmsStatus.PARTIAL
                anySuccess -> ReceivedSmsStatus.FORWARDED
                anyFailed -> ReceivedSmsStatus.FAILED
                else -> ReceivedSmsStatus.SKIPPED
            }
            perRuleStatuses += status
            perRuleSummaries += "${rule.name}: ${outcomes.joinToString("; ") { it.summary }}"
        }

        val rolledUpStatus = rollUpStatus(perRuleStatuses)
        receivedSmsRepository.update(
            pending.copy(
                id = rowId,
                otpCode = otp?.code,
                otpType = otp?.type,
                confidence = otp?.confidence,
                classifierTier = otp?.classifierTier,
                processingStatus = rolledUpStatus,
                matchedRuleNames = matchingRules.map { it.name },
                forwardedRecipients = forwardedRecipients.toList(),
                summary = perRuleSummaries.joinToString(" | "),
                processedAt = now
            )
        )

        return Result.Forwarded(sms, matchingRules.size, forwardedRecipients.toList())
    }

    private fun noMatchSummary(otp: com.otpforwarder.domain.model.Otp?): String =
        if (otp != null) "No matching rule (detected ${otp.type.name} code)"
        else "No matching rule"

    private fun rollUpStatus(perRule: List<String>): String {
        val anyForwarded = perRule.any { it == ReceivedSmsStatus.FORWARDED }
        val anyPartial = perRule.any { it == ReceivedSmsStatus.PARTIAL }
        val anyFailed = perRule.any { it == ReceivedSmsStatus.FAILED }
        val anySkipped = perRule.any { it == ReceivedSmsStatus.SKIPPED }
        return when {
            anyPartial -> ReceivedSmsStatus.PARTIAL
            anyForwarded && anyFailed -> ReceivedSmsStatus.PARTIAL
            anyForwarded -> ReceivedSmsStatus.FORWARDED
            anyFailed -> ReceivedSmsStatus.FAILED
            anySkipped -> ReceivedSmsStatus.SKIPPED
            else -> ReceivedSmsStatus.FAILED
        }
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
}
