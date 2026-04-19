package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.OtpLogEntity
import com.otpforwarder.domain.model.ClassifierTier
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
    val classifierTier: ClassifierTier,
    val ruleName: String,
    val recipientNames: List<String>,
    val status: String,
    val forwardedAt: Instant
)

// Non-comma so action summaries like "Forwarded: Mom, Dad" round-trip intact.
private const val SUMMARY_SEPARATOR = "; "

fun OtpLogEntity.toDomain(): OtpLogEntry = OtpLogEntry(
    id = id,
    code = code,
    otpType = OtpType.valueOf(otpType),
    sender = sender,
    originalMessage = originalMessage,
    detectedAt = Instant.ofEpochMilli(detectedAt),
    confidence = confidence,
    classifierTier = ClassifierTier.valueOf(classifierTier),
    ruleName = ruleName,
    recipientNames = recipientNames.split(SUMMARY_SEPARATOR).map { it.trim() },
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
    classifierTier = classifierTier.name,
    ruleName = ruleName,
    recipientNames = recipientNames.joinToString(SUMMARY_SEPARATOR),
    status = status,
    forwardedAt = forwardedAt.toEpochMilli()
)
