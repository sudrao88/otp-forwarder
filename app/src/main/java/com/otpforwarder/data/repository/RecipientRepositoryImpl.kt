package com.otpforwarder.data.repository

import com.otpforwarder.data.local.RecipientDao
import com.otpforwarder.data.mapper.toDomain
import com.otpforwarder.data.mapper.toEntity
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.RecipientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecipientRepositoryImpl @Inject constructor(
    private val recipientDao: RecipientDao
) : RecipientRepository {

    override fun getAllRecipients(): Flow<List<Recipient>> =
        recipientDao.getAllRecipients().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getRecipientById(id: Long): Recipient? =
        recipientDao.getRecipientById(id)?.toDomain()

    override suspend fun getActiveRecipients(): List<Recipient> =
        recipientDao.getActiveRecipients().map { it.toDomain() }

    override suspend fun getRecipientsForRule(ruleId: Long): List<Recipient> =
        recipientDao.getRecipientsForRule(ruleId).map { it.toDomain() }

    override suspend fun insertRecipient(recipient: Recipient): Long =
        recipientDao.insertRecipient(recipient.toEntity())

    override suspend fun updateRecipient(recipient: Recipient) =
        recipientDao.updateRecipient(recipient.toEntity())

    override suspend fun deleteRecipient(recipient: Recipient) =
        recipientDao.deleteRecipient(recipient.toEntity())
}
