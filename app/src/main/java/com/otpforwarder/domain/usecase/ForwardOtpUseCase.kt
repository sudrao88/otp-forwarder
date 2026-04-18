package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.sms.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards a detected [Otp] to a single [Recipient] via the configured [SmsSender].
 *
 * The outgoing message carries the extracted code up front so it renders in SMS
 * previews, followed by the original message for full context.
 */
@Singleton
class ForwardOtpUseCase @Inject constructor(
    private val smsSender: SmsSender
) {

    operator fun invoke(otp: Otp, recipient: Recipient): Boolean =
        smsSender.send(recipient.phoneNumber, buildMessage(otp))

    private fun buildMessage(otp: Otp): String = buildString {
        append("[").append(otp.type.name).append("] ")
        append(otp.sender).append(": ").append(otp.code)
        append("\n\n").append(otp.originalMessage)
    }
}
