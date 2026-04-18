package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.sms.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards a detected [Otp] to a single [Recipient] via the configured [SmsSender].
 *
 * The forwarded message is the original SMS body verbatim — the recipient sees
 * exactly what arrived on this device.
 */
@Singleton
class ForwardOtpUseCase @Inject constructor(
    private val smsSender: SmsSender
) {

    operator fun invoke(otp: Otp, recipient: Recipient): Boolean =
        smsSender.send(recipient.phoneNumber, otp.originalMessage)
}
