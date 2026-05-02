package com.otpforwarder.domain.repository

import com.otpforwarder.domain.model.ReceivedSms
import kotlinx.coroutines.flow.Flow

interface ReceivedSmsRepository {
    fun getRecent(sinceTimestamp: Long): Flow<List<ReceivedSms>>
    suspend fun insert(entry: ReceivedSms): Long
    suspend fun update(entry: ReceivedSms)
    suspend fun getById(id: Long): ReceivedSms?
    suspend fun pruneOlderThan(cutoffTimestamp: Long)
    suspend fun deleteAll()
}
