package com.otpforwarder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceivedSmsDao {

    @Query("SELECT * FROM received_sms WHERE receivedAt > :sinceTimestamp ORDER BY receivedAt DESC")
    fun getRecentReceivedSms(sinceTimestamp: Long): Flow<List<ReceivedSmsEntity>>

    @Insert
    suspend fun insert(entity: ReceivedSmsEntity): Long

    @Update
    suspend fun update(entity: ReceivedSmsEntity)

    @Query("SELECT * FROM received_sms WHERE id = :id")
    suspend fun getById(id: Long): ReceivedSmsEntity?

    @Query("DELETE FROM received_sms WHERE receivedAt < :cutoffTimestamp")
    suspend fun pruneOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM received_sms")
    suspend fun deleteAll()
}
