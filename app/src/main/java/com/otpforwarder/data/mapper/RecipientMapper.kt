package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.RecipientEntity
import com.otpforwarder.domain.model.Recipient

fun RecipientEntity.toDomain(): Recipient = Recipient(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    isActive = isActive
)

fun Recipient.toEntity(): RecipientEntity = RecipientEntity(
    id = id,
    name = name,
    phoneNumber = phoneNumber,
    isActive = isActive
)
