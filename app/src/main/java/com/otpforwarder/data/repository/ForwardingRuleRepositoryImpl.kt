package com.otpforwarder.data.repository

import androidx.room.withTransaction
import com.otpforwarder.data.local.ActionRecipientCrossRef
import com.otpforwarder.data.local.AppDatabase
import com.otpforwarder.data.local.ForwardingRuleDao
import com.otpforwarder.data.mapper.toDomain
import com.otpforwarder.data.mapper.toEntity
import com.otpforwarder.data.mapper.toRuleEntity
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ForwardingRuleRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val forwardingRuleDao: ForwardingRuleDao
) : ForwardingRuleRepository {

    override fun getAllRulesWithDetails(): Flow<List<ForwardingRule>> =
        forwardingRuleDao.getAllRulesWithDetails().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getRuleWithDetailsById(id: Long): ForwardingRule? =
        forwardingRuleDao.getRuleWithDetailsById(id)?.toDomain()

    override suspend fun getEnabledRulesWithDetails(): List<ForwardingRule> =
        forwardingRuleDao.getEnabledRulesWithDetails().map { it.toDomain() }

    override suspend fun insertRule(rule: ForwardingRule): Long {
        require(rule.actions.isNotEmpty()) { "Forwarding rule must declare at least one action" }
        return database.withTransaction {
            val ruleId = forwardingRuleDao.insertRule(rule.toRuleEntity())
            writeConditionsAndActions(ruleId, rule)
            ruleId
        }
    }

    override suspend fun updateRule(rule: ForwardingRule) {
        require(rule.actions.isNotEmpty()) { "Forwarding rule must declare at least one action" }
        database.withTransaction {
            forwardingRuleDao.updateRule(rule.toRuleEntity())
            forwardingRuleDao.deleteConditionsForRule(rule.id)
            forwardingRuleDao.deleteActionsForRule(rule.id)
            writeConditionsAndActions(rule.id, rule)
        }
    }

    override suspend fun deleteRule(rule: ForwardingRule) =
        forwardingRuleDao.deleteRule(rule.toRuleEntity())

    override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> =
        forwardingRuleDao.getRuleIdsForRecipient(recipientId)

    private suspend fun writeConditionsAndActions(ruleId: Long, rule: ForwardingRule) {
        rule.conditions.forEachIndexed { index, condition ->
            forwardingRuleDao.insertCondition(condition.toEntity(ruleId, index))
        }
        rule.actions.forEachIndexed { index, action ->
            val newActionId = forwardingRuleDao.insertAction(action.toEntity(ruleId, index))
            if (action is RuleAction.ForwardSms) {
                action.recipientIds.distinct().forEach { recipientId ->
                    forwardingRuleDao.insertActionRecipientCrossRef(
                        ActionRecipientCrossRef(actionId = newActionId, recipientId = recipientId)
                    )
                }
            }
        }
    }
}
