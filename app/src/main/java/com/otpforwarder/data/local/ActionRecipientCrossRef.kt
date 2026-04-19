package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "action_recipient_cross_ref",
    primaryKeys = ["actionId", "recipientId"],
    foreignKeys = [
        ForeignKey(
            entity = RuleActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
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
data class ActionRecipientCrossRef(
    val actionId: Long,
    val recipientId: Long
)
