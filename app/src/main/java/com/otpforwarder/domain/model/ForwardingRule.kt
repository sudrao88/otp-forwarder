package com.otpforwarder.domain.model

data class ForwardingRule(
    val id: Long = 0,
    val name: String,
    val otpType: OtpType,
    val isEnabled: Boolean = true,
    val priority: Int,
    val senderFilter: String? = null,
    val bodyFilter: String? = null
)
