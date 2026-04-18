package com.otpforwarder.domain.usecase

import com.otpforwarder.data.settings.SettingsRepository
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.sms.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards a detected [Otp] to a single [Recipient] via the configured [SmsSender].
 *
 * The outgoing message carries the extracted code up front so it renders in SMS
 * previews. The original message is appended for context when
 * [SettingsRepository.isIncludeOriginalMessage] is enabled.
 */
@Singleton
class ForwardOtpUseCase @Inject constructor(
    private val smsSender: SmsSender,
    private val settings: SettingsRepository
) {

    operator fun invoke(otp: Otp, recipient: Recipient): Boolean =
        smsSender.send(recipient.phoneNumber, buildMessage(otp))

    private fun buildMessage(otp: Otp): String = buildString {
        append("[").append(otp.type.name).append("] ")
        append(otp.sender).append(": ").append(otp.code)
        if (settings.isIncludeOriginalMessage()) {
            append("\n\n").append(otp.originalMessage)
        }
    }
}
