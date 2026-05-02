package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "received_sms",
    indices = [Index(value = ["receivedAt"])]
)
data class ReceivedSmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val otpCode: String?,
    val otpType: String?,
    val confidence: Double?,
    val classifierTier: String?,
    val processingStatus: String,
    val matchedRuleNames: String,
    val forwardedRecipients: String,
    val summary: String,
    val processedAt: Instant?
)
