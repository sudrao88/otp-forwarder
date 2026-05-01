package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.sms.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards the incoming SMS body to the recipients of a [RuleAction.ForwardSms].
 *
 * Deduplication runs across every rule and action fired for a single incoming
 * SMS via the shared [alreadySentTo] set: once a recipient has been attempted
 * (success or failure), subsequent rules skip them. That preserves the
 * behaviour of the old single-use-case pipeline where one OTP could never be
 * forwarded to the same recipient twice.
 */
fun interface ForwardSmsAction {
    operator fun invoke(
        sms: IncomingSms,
        action: RuleAction.ForwardSms,
        recipientsById: Map<Long, Recipient>,
        alreadySentTo: MutableSet<Long>
    ): ForwardSmsResult
}

data class ForwardSmsResult(
    val sent: List<Recipient>,
    val skipped: List<Recipient>,
    val failed: List<Recipient>
)

@Singleton
class ForwardSmsActionUseCase @Inject constructor(
    private val smsSender: SmsSender
) : ForwardSmsAction {

    override fun invoke(
        sms: IncomingSms,
        action: RuleAction.ForwardSms,
        recipientsById: Map<Long, Recipient>,
        alreadySentTo: MutableSet<Long>
    ): ForwardSmsResult {
        val sent = mutableListOf<Recipient>()
        val skipped = mutableListOf<Recipient>()
        val failed = mutableListOf<Recipient>()
        action.recipientIds.distinct().forEach { id ->
            val recipient = recipientsById[id] ?: return@forEach
            if (!alreadySentTo.add(id)) {
                skipped += recipient
                return@forEach
            }
            val ok = smsSender.send(recipient.phoneNumber, sms.body)
            if (ok) sent += recipient else failed += recipient
        }
        return ForwardSmsResult(sent, skipped, failed)
    }
}
