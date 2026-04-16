package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.ForwardingRuleEntity
import com.otpforwarder.data.local.RuleWithRecipients
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient

fun ForwardingRuleEntity.toDomain(): ForwardingRule = ForwardingRule(
    id = id,
    name = name,
    otpType = OtpType.valueOf(otpType),
    isEnabled = isEnabled,
    priority = priority,
    senderFilter = senderFilter,
    bodyFilter = bodyFilter
)

fun ForwardingRule.toEntity(): ForwardingRuleEntity = ForwardingRuleEntity(
    id = id,
    name = name,
    otpType = otpType.name,
    isEnabled = isEnabled,
    priority = priority,
    senderFilter = senderFilter,
    bodyFilter = bodyFilter
)

fun RuleWithRecipients.toDomain(): Pair<ForwardingRule, List<Recipient>> =
    rule.toDomain() to recipients.map { it.toDomain() }
