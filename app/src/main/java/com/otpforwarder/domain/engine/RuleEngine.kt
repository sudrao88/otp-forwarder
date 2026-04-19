package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.RecipientRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1 stub: evaluates the new condition list with simple AND-of-all semantics.
 * Phase 2 will replace this with proper left-to-right AND/OR evaluation.
 *
 * Returns matching enabled rules paired with the recipients of their `ForwardSms`
 * actions (deduped per rule). Rules with no `ForwardSms` action are still returned
 * with an empty recipient list so callers can see the rule fired.
 */
@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository,
    private val recipientRepository: RecipientRepository
) {

    suspend fun evaluate(otp: Otp): List<Pair<ForwardingRule, List<Recipient>>> {
        val rules = ruleRepository.getEnabledRulesWithDetails()
        val matching = rules.filter { matchesAll(it, otp) }
        if (matching.isEmpty()) return emptyList()

        val recipientsById = recipientRepository.getActiveRecipients().associateBy { it.id }

        return matching.map { rule ->
            val recipientIds = rule.actions.filterIsInstance<RuleAction.ForwardSms>()
                .flatMap { it.recipientIds }
                .distinct()
            val recipients = recipientIds.mapNotNull { recipientsById[it] }
            rule to recipients
        }
    }

    private fun matchesAll(rule: ForwardingRule, otp: Otp): Boolean {
        if (rule.conditions.isEmpty()) return true
        return rule.conditions.all { it.matches(otp) }
    }

    private fun RuleCondition.matches(otp: Otp): Boolean = when (this) {
        is RuleCondition.OtpTypeIs -> type == OtpType.ALL || type == otp.type
        is RuleCondition.SenderMatches -> matchesRegex(pattern, otp.sender)
        is RuleCondition.BodyContains -> matchesRegex(pattern, otp.originalMessage)
    }

    private fun matchesRegex(pattern: String, input: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(input) }.getOrDefault(false)
}
