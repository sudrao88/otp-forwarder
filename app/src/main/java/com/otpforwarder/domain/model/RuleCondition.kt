package com.otpforwarder.domain.model

sealed interface RuleCondition {
    val connector: Connector

    fun matches(otp: Otp): Boolean

    data class OtpTypeIs(
        val type: OtpType,
        override val connector: Connector
    ) : RuleCondition {
        override fun matches(otp: Otp): Boolean =
            type == OtpType.ALL || type == otp.type
    }

    data class SenderMatches(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition {
        private val compiled: Regex? = runCatching { Regex(pattern) }.getOrNull()
        override fun matches(otp: Otp): Boolean =
            compiled?.containsMatchIn(otp.sender) ?: false
    }

    data class BodyContains(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition {
        private val compiled: Regex? = runCatching { Regex(pattern) }.getOrNull()
        override fun matches(otp: Otp): Boolean =
            compiled?.containsMatchIn(otp.originalMessage) ?: false
    }
}
