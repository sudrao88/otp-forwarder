package com.otpforwarder.domain.model

sealed interface RuleCondition {
    val connector: Connector

    fun matches(sms: IncomingSms): Boolean

    data class OtpTypeIs(
        val type: OtpType,
        override val connector: Connector
    ) : RuleCondition {
        override fun matches(sms: IncomingSms): Boolean =
            type == OtpType.ALL || sms.otp?.type == type
    }

    data class SenderMatches(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition {
        private val compiled: Regex? = runCatching { Regex(pattern) }.getOrNull()
        override fun matches(sms: IncomingSms): Boolean =
            compiled?.containsMatchIn(sms.sender) ?: false
    }

    data class BodyContains(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition {
        private val compiled: Regex? = runCatching { Regex(pattern) }.getOrNull()
        override fun matches(sms: IncomingSms): Boolean =
            compiled?.containsMatchIn(sms.body) ?: false
    }
}
