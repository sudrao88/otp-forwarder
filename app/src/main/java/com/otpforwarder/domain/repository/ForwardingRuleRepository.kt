package com.otpforwarder.domain.repository

import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import kotlinx.coroutines.flow.Flow

interface ForwardingRuleRepository {
    fun getAllRulesWithRecipients(): Flow<List<Pair<ForwardingRule, List<Recipient>>>>
    fun getAllRules(): Flow<List<ForwardingRule>>
    suspend fun getRuleById(id: Long): ForwardingRule?
    suspend fun getRuleWithRecipientsById(id: Long): Pair<ForwardingRule, List<Recipient>>?
    suspend fun getMatchingRulesWithRecipients(otpType: OtpType): List<Pair<ForwardingRule, List<Recipient>>>
    suspend fun insertRule(rule: ForwardingRule, recipientIds: List<Long>): Long
    suspend fun updateRule(rule: ForwardingRule, recipientIds: List<Long>)
    suspend fun deleteRule(rule: ForwardingRule)
    suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long>
}
