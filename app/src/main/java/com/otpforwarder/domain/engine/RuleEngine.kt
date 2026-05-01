package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ForwardingRuleRepository
) {

    suspend fun evaluate(sms: IncomingSms): List<ForwardingRule> =
        ruleRepository.getEnabledRulesWithDetails()
            .filter { evaluateConditions(it.conditions, sms) }

    private fun evaluateConditions(conditions: List<RuleCondition>, sms: IncomingSms): Boolean {
        if (conditions.isEmpty()) return true
        var result = conditions[0].matches(sms)
        for (i in 1..conditions.lastIndex) {
            val c = conditions[i]
            result = when (c.connector) {
                Connector.AND -> result && c.matches(sms)
                Connector.OR -> result || c.matches(sms)
            }
        }
        return result
    }
}
