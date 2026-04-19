package com.otpforwarder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {

    @Query("SELECT * FROM recipients ORDER BY name ASC")
    fun getAllRecipients(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE id = :id")
    suspend fun getRecipientById(id: Long): RecipientEntity?

    @Query("SELECT * FROM recipients WHERE isActive = 1")
    suspend fun getActiveRecipients(): List<RecipientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: RecipientEntity): Long

    @Update
    suspend fun updateRecipient(recipient: RecipientEntity)

    @Delete
    suspend fun deleteRecipient(recipient: RecipientEntity)

    @Query(
        """
        SELECT DISTINCT r.* FROM recipients r
        INNER JOIN action_recipient_cross_ref x ON x.recipientId = r.id
        INNER JOIN rule_actions a ON a.id = x.actionId
        WHERE a.ruleId = :ruleId
        """
    )
    suspend fun getRecipientsForRule(ruleId: Long): List<RecipientEntity>
}
