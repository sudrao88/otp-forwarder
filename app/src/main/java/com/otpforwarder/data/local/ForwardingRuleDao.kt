package com.otpforwarder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingRuleDao {

    @Transaction
    @Query("SELECT * FROM forwarding_rules ORDER BY priority ASC")
    fun getAllRulesWithDetails(): Flow<List<RuleWithDetails>>

    @Query("SELECT * FROM forwarding_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<ForwardingRuleEntity>>

    @Query("SELECT * FROM forwarding_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): ForwardingRuleEntity?

    @Transaction
    @Query("SELECT * FROM forwarding_rules WHERE id = :id")
    suspend fun getRuleWithDetailsById(id: Long): RuleWithDetails?

    @Transaction
    @Query("SELECT * FROM forwarding_rules WHERE isEnabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledRulesWithDetails(): List<RuleWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRuleEntity): Long

    @Update
    suspend fun updateRule(rule: ForwardingRuleEntity)

    @Delete
    suspend fun deleteRule(rule: ForwardingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCondition(condition: RuleConditionEntity): Long

    @Query("DELETE FROM rule_conditions WHERE ruleId = :ruleId")
    suspend fun deleteConditionsForRule(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: RuleActionEntity): Long

    @Query("DELETE FROM rule_actions WHERE ruleId = :ruleId")
    suspend fun deleteActionsForRule(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionRecipientCrossRef(crossRef: ActionRecipientCrossRef)

    @Query(
        """
        SELECT DISTINCT a.ruleId
        FROM rule_actions a
        WHERE a.callRecipientId = :recipientId
        UNION
        SELECT DISTINCT a.ruleId
        FROM rule_actions a
        INNER JOIN action_recipient_cross_ref x ON x.actionId = a.id
        WHERE x.recipientId = :recipientId
        """
    )
    suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long>
}
