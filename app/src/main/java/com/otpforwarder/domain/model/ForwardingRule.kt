package com.otpforwarder.domain.model

data class ForwardingRule(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int,
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>
)
