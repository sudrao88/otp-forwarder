package com.otpforwarder.domain.model

sealed interface RuleAction {
    data class ForwardSms(val recipientIds: List<Long>) : RuleAction
    data object SetRingerLoud : RuleAction
    data class PlaceCall(val recipientId: Long) : RuleAction
}
