package com.otpforwarder.domain.model

data class Recipient(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val isActive: Boolean = true
)
