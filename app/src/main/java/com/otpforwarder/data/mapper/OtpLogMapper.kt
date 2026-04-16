package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.OtpLogEntity
import com.otpforwarder.domain.model.OtpType
import java.time.Instant

data class OtpLogEntry(
    val id: Long,
    val code: String,
    val otpType: OtpType,
    val sender: String,
    val originalMessage: String,
    val detectedAt: Instant,
    val confidence: Double,
    val ruleName: String,
    val recipientNames: List<String>,
    val status: String,
    val forwardedAt: Instant
)

fun OtpLogEntity.toDomain(): OtpLogEntry = OtpLogEntry(
    id = id,
    code = code,
    otpType = OtpType.valueOf(otpType),
    sender = sender,
    originalMessage = originalMessage,
    detectedAt = Instant.ofEpochMilli(detectedAt),
    confidence = confidence,
    ruleName = ruleName,
    recipientNames = recipientNames.split(",").map { it.trim() },
    status = status,
    forwardedAt = Instant.ofEpochMilli(forwardedAt)
)

fun OtpLogEntry.toEntity(): OtpLogEntity = OtpLogEntity(
    id = id,
    code = code,
    otpType = otpType.name,
    sender = sender,
    originalMessage = originalMessage,
    detectedAt = detectedAt.toEpochMilli(),
    confidence = confidence,
    ruleName = ruleName,
    recipientNames = recipientNames.joinToString(", "),
    status = status,
    forwardedAt = forwardedAt.toEpochMilli()
)
