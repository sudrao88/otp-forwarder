package com.otpforwarder.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ActionWithRecipients(
    @Embedded val action: RuleActionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ActionRecipientCrossRef::class,
            parentColumn = "actionId",
            entityColumn = "recipientId"
        )
    )
    val recipients: List<RecipientEntity>
)

data class RuleWithDetails(
    @Embedded val rule: ForwardingRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ruleId"
    )
    val conditions: List<RuleConditionEntity>,
    @Relation(
        entity = RuleActionEntity::class,
        parentColumn = "id",
        entityColumn = "ruleId"
    )
    val actions: List<ActionWithRecipients>
)
