package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.ReceivedSmsEntity
import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.ReceivedSms

private const val LIST_SEPARATOR = "; "

fun ReceivedSmsEntity.toDomain(): ReceivedSms = ReceivedSms(
    id = id,
    sender = sender,
    body = body,
    receivedAt = receivedAt,
    otpCode = otpCode,
    otpType = otpType?.let { runCatching { OtpType.valueOf(it) }.getOrNull() },
    confidence = confidence,
    classifierTier = classifierTier?.let { runCatching { ClassifierTier.valueOf(it) }.getOrNull() },
    processingStatus = processingStatus,
    matchedRuleNames = if (matchedRuleNames.isBlank()) emptyList() else matchedRuleNames.split(LIST_SEPARATOR),
    forwardedRecipients = if (forwardedRecipients.isBlank()) emptyList() else forwardedRecipients.split(LIST_SEPARATOR),
    summary = summary,
    processedAt = processedAt
)

fun ReceivedSms.toEntity(): ReceivedSmsEntity = ReceivedSmsEntity(
    id = id,
    sender = sender,
    body = body,
    receivedAt = receivedAt,
    otpCode = otpCode,
    otpType = otpType?.name,
    confidence = confidence,
    classifierTier = classifierTier?.name,
    processingStatus = processingStatus,
    matchedRuleNames = matchedRuleNames.joinToString(LIST_SEPARATOR),
    forwardedRecipients = forwardedRecipients.joinToString(LIST_SEPARATOR),
    summary = summary,
    processedAt = processedAt
)
