package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates an [Otp] against the stored forwarding rules and produces the set of
 * (rule, recipients) pairs that should be notified.
 *
 * Matching proceeds in three stages:
 *  1. Type match — delegated to the DAO, which applies `otpType = ALL OR otpType = :type`,
 *     drops disabled rules and inactive recipients, and orders results by specificity
 *     (typed rules before `ALL`) then ascending priority.
 *  2. Filter match — optional sender and body regexes are evaluated with AND semantics.
 *     Invalid regexes are treated as non-matching.
 *  3. Recipient deduplication — a recipient that appears in multiple matching rules
 *     is credited only to the first (highest-priority) rule, so we never send the same
 *     OTP twice to the same phone number.
 */
@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository
) {

    suspend fun evaluate(otp: Otp): List<Pair<ForwardingRule, List<Recipient>>> {
        val candidates = ruleRepository.getMatchingRulesWithRecipients(otp.type)
        val filtered = candidates.filter { (rule, _) -> matchesFilters(rule, otp) }
        return deduplicateRecipients(filtered)
    }

    private fun matchesFilters(rule: ForwardingRule, otp: Otp): Boolean {
        val senderOk = rule.senderFilter?.let { matchesRegex(it, otp.sender) } ?: true
        val bodyOk = rule.bodyFilter?.let { matchesRegex(it, otp.originalMessage) } ?: true
        return senderOk && bodyOk
    }

    private fun matchesRegex(pattern: String, input: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(input) }.getOrDefault(false)

    private fun deduplicateRecipients(
        rulesInPriorityOrder: List<Pair<ForwardingRule, List<Recipient>>>
    ): List<Pair<ForwardingRule, List<Recipient>>> {
        val seen = mutableSetOf<Long>()
        val result = mutableListOf<Pair<ForwardingRule, List<Recipient>>>()
        for ((rule, recipients) in rulesInPriorityOrder) {
            val fresh = recipients.filter { seen.add(it.id) }
            if (fresh.isNotEmpty()) result += rule to fresh
        }
        return result
    }
}
