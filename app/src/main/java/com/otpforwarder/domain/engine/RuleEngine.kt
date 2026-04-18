package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates an [Otp] against the stored forwarding rules and returns every rule
 * that matches, paired with its full recipient list, in priority order.
 *
 * The engine does not deduplicate recipients across rules — callers that send
 * SMS should dedupe sends themselves so the same phone number isn't texted
 * twice, while every matching rule remains visible for logging.
 *
 *  1. Type match — delegated to the DAO, which applies `otpType = ALL OR otpType = :type`,
 *     drops disabled rules and inactive recipients, and orders results by specificity
 *     (typed rules before `ALL`) then ascending priority.
 *  2. Filter match — optional sender and body regexes are evaluated with AND semantics.
 *     Invalid regexes are treated as non-matching.
 */
@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository
) {

    suspend fun evaluate(otp: Otp): List<Pair<ForwardingRule, List<Recipient>>> {
        val candidates = ruleRepository.getMatchingRulesWithRecipients(otp.type)
        return candidates.filter { (rule, _) -> matchesFilters(rule, otp) }
    }

    private fun matchesFilters(rule: ForwardingRule, otp: Otp): Boolean {
        val senderOk = rule.senderFilter?.let { matchesRegex(it, otp.sender) } ?: true
        val bodyOk = rule.bodyFilter?.let { matchesRegex(it, otp.originalMessage) } ?: true
        return senderOk && bodyOk
    }

    private fun matchesRegex(pattern: String, input: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(input) }.getOrDefault(false)
}
