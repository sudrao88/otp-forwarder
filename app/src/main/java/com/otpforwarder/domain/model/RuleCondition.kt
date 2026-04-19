package com.otpforwarder.domain.model

sealed interface RuleCondition {
    val connector: Connector

    data class OtpTypeIs(
        val type: OtpType,
        override val connector: Connector
    ) : RuleCondition

    data class SenderMatches(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition

    data class BodyContains(
        val pattern: String,
        override val connector: Connector
    ) : RuleCondition
}
