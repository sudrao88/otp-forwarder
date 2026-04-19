package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.OtpLogEntity
import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpLogEntry
import com.otpforwarder.domain.model.OtpType

// Non-comma so action summaries like "Forwarded: Mom, Dad" round-trip intact.
private const val SUMMARY_SEPARATOR = "; "

fun OtpLogEntity.toDomain(): OtpLogEntry = OtpLogEntry(
    id = id,
    code = code,
    otpType = OtpType.valueOf(otpType),
    sender = sender,
    originalMessage = originalMessage,
    detectedAt = detectedAt,
    confidence = confidence,
    classifierTier = ClassifierTier.valueOf(classifierTier),
    ruleName = ruleName,
    summaryLines = if (recipientNames.isBlank()) emptyList() else recipientNames.split(SUMMARY_SEPARATOR),
    status = status,
    forwardedAt = forwardedAt
)

fun OtpLogEntry.toEntity(): OtpLogEntity = OtpLogEntity(
    id = id,
    code = code,
    otpType = otpType.name,
    sender = sender,
    originalMessage = originalMessage,
    detectedAt = detectedAt,
    confidence = confidence,
    classifierTier = classifierTier.name,
    ruleName = ruleName,
    recipientNames = summaryLines.joinToString(SUMMARY_SEPARATOR),
    status = status,
    forwardedAt = forwardedAt
)
