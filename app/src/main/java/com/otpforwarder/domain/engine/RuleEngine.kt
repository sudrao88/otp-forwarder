package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository
) {

    suspend fun evaluate(otp: Otp): List<ForwardingRule> =
        ruleRepository.getEnabledRulesWithDetails()
            .filter { evaluateConditions(it.conditions, otp) }

    private fun evaluateConditions(conditions: List<RuleCondition>, otp: Otp): Boolean {
        if (conditions.isEmpty()) return true
        var result = conditions[0].matches(otp)
        for (i in 1..conditions.lastIndex) {
            val next = conditions[i].matches(otp)
            result = when (conditions[i].connector) {
                Connector.AND -> result && next
                Connector.OR -> result || next
            }
        }
        return result
    }

    private fun RuleCondition.matches(otp: Otp): Boolean = when (this) {
        is RuleCondition.OtpTypeIs -> type == OtpType.ALL || type == otp.type
        is RuleCondition.SenderMatches -> matchesRegex(pattern, otp.sender)
        is RuleCondition.BodyContains -> matchesRegex(pattern, otp.originalMessage)
    }

    private fun matchesRegex(pattern: String, input: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(input) }.getOrDefault(false)
}
