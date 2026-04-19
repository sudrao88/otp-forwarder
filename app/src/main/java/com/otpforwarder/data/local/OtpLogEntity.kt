package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "otp_log",
    indices = [Index(value = ["forwardedAt"])]
)
data class OtpLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val otpType: String,
    val sender: String,
    val originalMessage: String,
    val detectedAt: Instant,
    val confidence: Double,
    val classifierTier: String,
    val ruleName: String,
    val recipientNames: String,
    val status: String,
    val forwardedAt: Instant
)
