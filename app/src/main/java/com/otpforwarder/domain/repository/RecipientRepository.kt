package com.otpforwarder.domain.repository

import com.otpforwarder.domain.model.Recipient
import kotlinx.coroutines.flow.Flow

interface RecipientRepository {
    fun getAllRecipients(): Flow<List<Recipient>>
    suspend fun getRecipientById(id: Long): Recipient?
    suspend fun getActiveRecipients(): List<Recipient>
    suspend fun getRecipientsForRule(ruleId: Long): List<Recipient>
    suspend fun insertRecipient(recipient: Recipient): Long
    suspend fun updateRecipient(recipient: Recipient)
    suspend fun deleteRecipient(recipient: Recipient)
}
