package com.otpforwarder.domain.model

import java.time.Instant

/**
 * A single inbound SMS as it flows through the rule pipeline.
 *
 * [otp] is populated when [com.otpforwarder.domain.detection.OtpDetector] finds
 * an OTP code in the body and a classifier has tagged its type. It is null for
 * plain SMS — the rule engine still evaluates these so non-OTP rules
 * (sender / body keyword based) can fire.
 */
data class IncomingSms(
    val sender: String,
    val body: String,
    val otp: Otp?,
    val receivedAt: Instant
)
