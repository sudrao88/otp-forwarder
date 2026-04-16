package com.otpforwarder.data.repository

import com.otpforwarder.data.local.ForwardingRuleDao
import com.otpforwarder.data.local.RuleRecipientCrossRef
import com.otpforwarder.data.mapper.toDomain
import com.otpforwarder.data.mapper.toEntity
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ForwardingRuleRepositoryImpl @Inject constructor(
    private val forwardingRuleDao: ForwardingRuleDao
) : ForwardingRuleRepository {

    override fun getAllRulesWithRecipients(): Flow<List<Pair<ForwardingRule, List<Recipient>>>> =
        forwardingRuleDao.getAllRulesWithRecipients().map { list ->
            list.map { it.toDomain() }
        }

    override fun getAllRules(): Flow<List<ForwardingRule>> =
        forwardingRuleDao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getRuleById(id: Long): ForwardingRule? =
        forwardingRuleDao.getRuleById(id)?.toDomain()

    override suspend fun getRuleWithRecipientsById(id: Long): Pair<ForwardingRule, List<Recipient>>? =
        forwardingRuleDao.getRuleWithRecipientsById(id)?.toDomain()

    override suspend fun getMatchingRulesWithRecipients(otpType: OtpType): List<Pair<ForwardingRule, List<Recipient>>> =
        forwardingRuleDao.getMatchingRulesWithRecipients(otpType.name).map { it.toDomain() }

    override suspend fun insertRule(rule: ForwardingRule, recipientIds: List<Long>): Long {
        val ruleId = forwardingRuleDao.insertRule(rule.toEntity())
        recipientIds.forEach { recipientId ->
            forwardingRuleDao.insertRuleRecipientCrossRef(
                RuleRecipientCrossRef(ruleId = ruleId, recipientId = recipientId)
            )
        }
        return ruleId
    }

    override suspend fun updateRule(rule: ForwardingRule, recipientIds: List<Long>) {
        forwardingRuleDao.updateRule(rule.toEntity())
        forwardingRuleDao.deleteRuleRecipientCrossRefs(rule.id)
        recipientIds.forEach { recipientId ->
            forwardingRuleDao.insertRuleRecipientCrossRef(
                RuleRecipientCrossRef(ruleId = rule.id, recipientId = recipientId)
            )
        }
    }

    override suspend fun deleteRule(rule: ForwardingRule) =
        forwardingRuleDao.deleteRule(rule.toEntity())

    override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> =
        forwardingRuleDao.getRuleIdsForRecipient(recipientId)
}
