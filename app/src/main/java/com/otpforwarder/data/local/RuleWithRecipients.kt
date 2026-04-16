package com.otpforwarder.data.local

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RuleWithRecipients(
    @Embedded val rule: ForwardingRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RuleRecipientCrossRef::class,
            parentColumn = "ruleId",
            entityColumn = "recipientId"
        )
    )
    val recipients: List<RecipientEntity>
)
