package com.otpforwarder.domain.repository

import com.otpforwarder.domain.model.OtpLogEntry
import kotlinx.coroutines.flow.Flow

interface OtpLogRepository {
    fun getRecentLogs(sinceTimestamp: Long): Flow<List<OtpLogEntry>>
    suspend fun insertLog(entry: OtpLogEntry): Long
    suspend fun updateLog(entry: OtpLogEntry)
    suspend fun pruneOldLogs(cutoffTimestamp: Long)
    suspend fun getLogById(id: Long): OtpLogEntry?
}
