package com.otpforwarder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpLogDao {

    @Query("SELECT * FROM otp_log WHERE forwardedAt > :sinceTimestamp ORDER BY forwardedAt DESC")
    fun getRecentLogs(sinceTimestamp: Long): Flow<List<OtpLogEntity>>

    @Insert
    suspend fun insertLog(log: OtpLogEntity): Long

    @Update
    suspend fun updateLog(log: OtpLogEntity)

    @Query("DELETE FROM otp_log WHERE forwardedAt < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long)

    @Query("SELECT * FROM otp_log WHERE id = :id")
    suspend fun getLogById(id: Long): OtpLogEntity?
}
