package com.otpforwarder.data.repository

import com.otpforwarder.data.local.ReceivedSmsDao
import com.otpforwarder.data.mapper.toDomain
import com.otpforwarder.data.mapper.toEntity
import com.otpforwarder.domain.model.ReceivedSms
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReceivedSmsRepositoryImpl @Inject constructor(
    private val dao: ReceivedSmsDao
) : ReceivedSmsRepository {

    override fun getRecent(sinceTimestamp: Long): Flow<List<ReceivedSms>> =
        dao.getRecentReceivedSms(sinceTimestamp).map { rows -> rows.map { it.toDomain() } }

    override suspend fun insert(entry: ReceivedSms): Long =
        dao.insert(entry.toEntity())

    override suspend fun update(entry: ReceivedSms) =
        dao.update(entry.toEntity())

    override suspend fun getById(id: Long): ReceivedSms? =
        dao.getById(id)?.toDomain()

    override suspend fun pruneOlderThan(cutoffTimestamp: Long) =
        dao.pruneOlderThan(cutoffTimestamp)

    override suspend fun deleteAll() = dao.deleteAll()
}
