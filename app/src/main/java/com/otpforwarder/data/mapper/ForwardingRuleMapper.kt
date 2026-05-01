package com.otpforwarder.data.mapper

import com.otpforwarder.data.local.ActionWithRecipients
import com.otpforwarder.data.local.ForwardingRuleEntity
import com.otpforwarder.data.local.RuleActionEntity
import com.otpforwarder.data.local.RuleConditionEntity
import com.otpforwarder.data.local.RuleWithDetails
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition

fun ForwardingRule.toRuleEntity(): ForwardingRuleEntity = ForwardingRuleEntity(
    id = id,
    name = name,
    isEnabled = isEnabled,
    priority = priority
)

fun RuleConditionEntity.toDomain(): RuleCondition {
    val conn = runCatching { Connector.valueOf(connector) }.getOrDefault(Connector.AND)
    return when (conditionType) {
        RuleConditionEntity.TYPE_OTP_TYPE -> RuleCondition.OtpTypeIs(
            type = otpTypeValue?.let { runCatching { OtpType.valueOf(it) }.getOrNull() }
                ?: OtpType.ALL,
            connector = conn
        )
        RuleConditionEntity.TYPE_SENDER_REGEX -> RuleCondition.SenderMatches(
            pattern = pattern.orEmpty(),
            connector = conn
        )
        RuleConditionEntity.TYPE_BODY_REGEX -> RuleCondition.BodyContains(
            pattern = pattern.orEmpty(),
            connector = conn
        )
        RuleConditionEntity.TYPE_MAPS_LINK -> RuleCondition.ContainsMapsLink(
            connector = conn
        )
        else -> error("Unknown conditionType=$conditionType")
    }
}

fun RuleCondition.toEntity(ruleId: Long, orderIndex: Int): RuleConditionEntity = when (this) {
    is RuleCondition.OtpTypeIs -> RuleConditionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        connector = connector.name,
        conditionType = RuleConditionEntity.TYPE_OTP_TYPE,
        otpTypeValue = type.name,
        pattern = null
    )
    is RuleCondition.SenderMatches -> RuleConditionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        connector = connector.name,
        conditionType = RuleConditionEntity.TYPE_SENDER_REGEX,
        otpTypeValue = null,
        pattern = pattern
    )
    is RuleCondition.BodyContains -> RuleConditionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        connector = connector.name,
        conditionType = RuleConditionEntity.TYPE_BODY_REGEX,
        otpTypeValue = null,
        pattern = pattern
    )
    is RuleCondition.ContainsMapsLink -> RuleConditionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        connector = connector.name,
        conditionType = RuleConditionEntity.TYPE_MAPS_LINK,
        otpTypeValue = null,
        pattern = null
    )
}

fun ActionWithRecipients.toDomain(): RuleAction? = when (action.actionType) {
    RuleActionEntity.TYPE_FORWARD_SMS -> RuleAction.ForwardSms(
        recipientIds = recipients.map { it.id }
    )
    RuleActionEntity.TYPE_RINGER_LOUD -> RuleAction.SetRingerLoud
    // Drop orphaned PlaceCall rows (recipient deleted without cascade) so we don't silently call id=0.
    RuleActionEntity.TYPE_PLACE_CALL -> action.callRecipientId?.let { RuleAction.PlaceCall(it) }
    RuleActionEntity.TYPE_OPEN_MAPS -> RuleAction.OpenMapsNavigation(autoLaunch = action.mapsAutoLaunch)
    else -> error("Unknown actionType=${action.actionType}")
}

fun RuleAction.toEntity(ruleId: Long, orderIndex: Int): RuleActionEntity = when (this) {
    is RuleAction.ForwardSms -> RuleActionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        actionType = RuleActionEntity.TYPE_FORWARD_SMS,
        callRecipientId = null
    )
    RuleAction.SetRingerLoud -> RuleActionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        actionType = RuleActionEntity.TYPE_RINGER_LOUD,
        callRecipientId = null
    )
    is RuleAction.PlaceCall -> RuleActionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        actionType = RuleActionEntity.TYPE_PLACE_CALL,
        callRecipientId = recipientId
    )
    is RuleAction.OpenMapsNavigation -> RuleActionEntity(
        ruleId = ruleId,
        orderIndex = orderIndex,
        actionType = RuleActionEntity.TYPE_OPEN_MAPS,
        callRecipientId = null,
        mapsAutoLaunch = autoLaunch
    )
}

fun RuleWithDetails.toDomain(): ForwardingRule = ForwardingRule(
    id = rule.id,
    name = rule.name,
    isEnabled = rule.isEnabled,
    priority = rule.priority,
    conditions = conditions
        .sortedBy { it.orderIndex }
        .map { it.toDomain() },
    actions = actions
        .sortedBy { it.action.orderIndex }
        .mapNotNull { it.toDomain() }
)
