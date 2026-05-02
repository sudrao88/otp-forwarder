package com.otpforwarder.domain.model

import java.time.Instant

/**
 * One row in the unified message feed: every SMS the broadcast receiver
 * delivered to the pipeline, regardless of whether a rule matched.
 *
 * [processingStatus] is one of [ReceivedSmsStatus]. [matchedRuleNames] and
 * [forwardedRecipients] are populated only for [ReceivedSmsStatus.FORWARDED] /
 * [ReceivedSmsStatus.PARTIAL] rows.
 */
data class ReceivedSms(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val otpCode: String?,
    val otpType: OtpType?,
    val confidence: Double?,
    val classifierTier: ClassifierTier?,
    val processingStatus: String,
    val matchedRuleNames: List<String>,
    val forwardedRecipients: List<String>,
    val summary: String,
    val processedAt: Instant?
)

object ReceivedSmsStatus {
    /** Inserted at broadcast time; pipeline hasn't run (or was interrupted). */
    const val PENDING = "PENDING"

    /** Pipeline finished; no rule matched. */
    const val NO_MATCH = "NO_MATCH"

    /** At least one rule matched and every action that ran succeeded. */
    const val FORWARDED = "FORWARDED"

    /** Rules matched; some actions succeeded, others failed. */
    const val PARTIAL = "PARTIAL"

    /** Rules matched but every action failed. */
    const val FAILED = "FAILED"

    /** Rules matched but every recipient was already reached by an earlier rule. */
    const val SKIPPED = "SKIPPED"

    /** Pipeline threw — captured for diagnostics; retry is scheduled separately. */
    const val ERROR = "ERROR"
}
