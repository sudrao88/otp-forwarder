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
import kotlinx.coroutines.flow.first
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

    override suspend fun setRuleAssignmentsForRecipient(
        recipientId: Long,
        ruleIds: Set<Long>
    ) {
        database.withTransaction {
            val allRules = forwardingRuleDao.getAllRulesWithDetails().first().map { it.toDomain() }
            for (rule in allRules) {
                val shouldHave = rule.id in ruleIds
                val has = rule.actions.any { action ->
                    action is RuleAction.ForwardSms && recipientId in action.recipientIds
                }
                if (shouldHave == has) continue
                val newActions = mutateForwardActions(rule.actions, recipientId, shouldHave)
                val updated = rule.copy(actions = newActions)
                require(updated.actions.isNotEmpty()) {
                    "Forwarding rule must declare at least one action"
                }
                forwardingRuleDao.updateRule(updated.toRuleEntity())
                forwardingRuleDao.deleteConditionsForRule(updated.id)
                forwardingRuleDao.deleteActionsForRule(updated.id)
                writeConditionsAndActions(updated.id, updated)
            }
        }
    }

    private fun mutateForwardActions(
        actions: List<RuleAction>,
        recipientId: Long,
        add: Boolean
    ): List<RuleAction> {
        if (!add) {
            return actions.map { action ->
                if (action is RuleAction.ForwardSms) {
                    action.copy(recipientIds = action.recipientIds.filter { it != recipientId })
                } else action
            }
        }
        var inserted = false
        val updated = actions.map { action ->
            if (!inserted && action is RuleAction.ForwardSms) {
                inserted = true
                action.copy(recipientIds = (action.recipientIds + recipientId).distinct())
            } else action
        }
        return if (inserted) updated else updated + RuleAction.ForwardSms(listOf(recipientId))
    }

    private suspend fun writeConditionsAndActions(ruleId: Long, rule: ForwardingRule) {
        rule.conditions.forEachIndexed { index, condition ->
            forwardingRuleDao.insertCondition(condition.toEntity(ruleId, index))
        }
        rule.actions.forEachIndexed { index, action ->
            val newActionId = forwardingRuleDao.insertAction(action.toEntity(ruleId, index))
            if (action is RuleAction.ForwardSms) {
                val refs = action.recipientIds.distinct().map { recipientId ->
                    ActionRecipientCrossRef(actionId = newActionId, recipientId = recipientId)
                }
                if (refs.isNotEmpty()) forwardingRuleDao.insertAllCrossRefs(refs)
            }
        }
    }
}
