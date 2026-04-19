package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_actions",
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
            childColumns = ["callRecipientId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["ruleId"]), Index(value = ["callRecipientId"])]
)
data class RuleActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long,
    val orderIndex: Int,
    val actionType: String,
    val callRecipientId: Long? = null
) {
    companion object {
        const val TYPE_FORWARD_SMS = "FORWARD_SMS"
        const val TYPE_RINGER_LOUD = "RINGER_LOUD"
        const val TYPE_PLACE_CALL = "PLACE_CALL"
    }
}
