package com.otpforwarder.domain.repository

import com.otpforwarder.domain.model.ForwardingRule
import kotlinx.coroutines.flow.Flow

interface ForwardingRuleRepository {
    fun getAllRulesWithDetails(): Flow<List<ForwardingRule>>
    suspend fun getRuleWithDetailsById(id: Long): ForwardingRule?
    suspend fun getEnabledRulesWithDetails(): List<ForwardingRule>
    suspend fun insertRule(rule: ForwardingRule): Long
    suspend fun updateRule(rule: ForwardingRule)
    suspend fun deleteRule(rule: ForwardingRule)
    suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long>

    /**
     * Atomically reconciles which rules forward to [recipientId]. Rules whose
     * id is in [ruleIds] gain the recipient in their first `ForwardSms` action
     * (one is added if none exists); rules whose id is absent have the
     * recipient removed from every `ForwardSms` action.
     */
    suspend fun setRuleAssignmentsForRecipient(recipientId: Long, ruleIds: Set<Long>)
}
