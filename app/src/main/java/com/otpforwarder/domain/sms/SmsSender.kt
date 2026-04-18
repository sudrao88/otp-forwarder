package com.otpforwarder.domain.sms

/**
 * Abstraction over the platform SMS send API so the domain layer stays free of
 * Android dependencies and can be exercised from plain JVM tests.
 */
interface SmsSender {
    /**
     * Send [message] to [phoneNumber].
     *
     * @return `true` if the platform accepted the send request, `false` otherwise.
     *         A `true` result does not guarantee delivery to the handset — it only
     *         reports that no exception was thrown during dispatch.
     */
    fun send(phoneNumber: String, message: String): Boolean
}
