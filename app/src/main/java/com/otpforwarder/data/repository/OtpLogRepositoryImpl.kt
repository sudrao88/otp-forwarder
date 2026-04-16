package com.otpforwarder.data.repository

import com.otpforwarder.data.local.OtpLogDao
import com.otpforwarder.data.mapper.OtpLogEntry
import com.otpforwarder.data.mapper.toDomain
import com.otpforwarder.data.mapper.toEntity
import com.otpforwarder.domain.repository.OtpLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OtpLogRepositoryImpl @Inject constructor(
    private val otpLogDao: OtpLogDao
) : OtpLogRepository {

    override fun getRecentLogs(sinceTimestamp: Long): Flow<List<OtpLogEntry>> =
        otpLogDao.getRecentLogs(sinceTimestamp).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun insertLog(entry: OtpLogEntry): Long =
        otpLogDao.insertLog(entry.toEntity())

    override suspend fun updateLog(entry: OtpLogEntry) =
        otpLogDao.updateLog(entry.toEntity())

    override suspend fun pruneOldLogs(cutoffTimestamp: Long) =
        otpLogDao.pruneOldLogs(cutoffTimestamp)

    override suspend fun getLogById(id: Long): OtpLogEntry? =
        otpLogDao.getLogById(id)?.toDomain()
}
