package com.otpforwarder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forwarding_rules",
    indices = [Index(value = ["priority"])]
)
data class ForwardingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int
)
