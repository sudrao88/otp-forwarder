package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rule_conditions",
    foreignKeys = [
        ForeignKey(
            entity = ForwardingRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ruleId"])]
)
data class RuleConditionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long,
    val orderIndex: Int,
    val connector: String,
    val conditionType: String,
    val otpTypeValue: String? = null,
    val pattern: String? = null
) {
    companion object {
        const val TYPE_OTP_TYPE = "OTP_TYPE"
        const val TYPE_SENDER_REGEX = "SENDER_REGEX"
        const val TYPE_BODY_REGEX = "BODY_REGEX"
        const val TYPE_MAPS_LINK = "MAPS_LINK"
    }
}
