package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "rule_recipient_cross_ref",
    primaryKeys = ["ruleId", "recipientId"],
    foreignKeys = [
        ForeignKey(
            entity = ForwardingRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipientEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recipientId"])]
)
data class RuleRecipientCrossRef(
    val ruleId: Long,
    val recipientId: Long
)
