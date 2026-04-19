package com.otpforwarder.domain.model

import java.time.Instant

data class Otp(
    val code: String,
    val type: OtpType,
    val sender: String,
    val originalMessage: String,
    val detectedAt: Instant,
    val confidence: Double,
    val classifierTier: ClassifierTier = ClassifierTier.NONE
)
