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
    fun getAllRulesWithRecipients(): Flow<List<RuleWithRecipients>>

    @Query("SELECT * FROM forwarding_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<ForwardingRuleEntity>>

    @Query("SELECT * FROM forwarding_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): ForwardingRuleEntity?

    @Transaction
    @Query("SELECT * FROM forwarding_rules WHERE id = :id")
    suspend fun getRuleWithRecipientsById(id: Long): RuleWithRecipients?

    @Transaction
    @Query("""
        SELECT r.* FROM forwarding_rules r
        INNER JOIN rule_recipient_cross_ref xref ON r.id = xref.ruleId
        INNER JOIN recipients rec ON xref.recipientId = rec.id
        WHERE r.isEnabled = 1
          AND rec.isActive = 1
          AND (r.otpType = 'ALL' OR r.otpType = :otpType)
        GROUP BY r.id
        ORDER BY CASE WHEN r.otpType = 'ALL' THEN 1 ELSE 0 END, r.priority ASC
    """)
    suspend fun getMatchingRulesWithRecipients(otpType: String): List<RuleWithRecipients>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRuleEntity): Long

    @Update
    suspend fun updateRule(rule: ForwardingRuleEntity)

    @Delete
    suspend fun deleteRule(rule: ForwardingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRuleRecipientCrossRef(crossRef: RuleRecipientCrossRef)

    @Query("DELETE FROM rule_recipient_cross_ref WHERE ruleId = :ruleId")
    suspend fun deleteRuleRecipientCrossRefs(ruleId: Long)

    @Query("SELECT ruleId FROM rule_recipient_cross_ref WHERE recipientId = :recipientId")
    suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long>
}
