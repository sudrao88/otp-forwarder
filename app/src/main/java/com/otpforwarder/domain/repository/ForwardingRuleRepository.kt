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
}
