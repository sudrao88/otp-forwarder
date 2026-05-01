package com.otpforwarder.domain.model

import java.time.Instant

data class OtpLogEntry(
    val id: Long,
    val code: String?,
    val otpType: OtpType?,
    val sender: String,
    val originalMessage: String,
    val detectedAt: Instant,
    val confidence: Double?,
    val classifierTier: ClassifierTier?,
    val ruleName: String,
    val summaryLines: List<String>,
    val status: String,
    val forwardedAt: Instant
)
