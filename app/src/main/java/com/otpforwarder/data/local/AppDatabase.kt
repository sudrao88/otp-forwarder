package com.otpforwarder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecipientEntity::class,
        ForwardingRuleEntity::class,
        RuleConditionEntity::class,
        RuleActionEntity::class,
        ActionRecipientCrossRef::class,
        OtpLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao
    abstract fun otpLogDao(): OtpLogDao
}
