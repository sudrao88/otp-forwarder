package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otp_log")
data class OtpLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val otpType: String,
    val sender: String,
    val originalMessage: String,
    val detectedAt: Long,
    val confidence: Double,
    val ruleName: String,
    val recipientNames: String,
    val status: String,
    val forwardedAt: Long
)
