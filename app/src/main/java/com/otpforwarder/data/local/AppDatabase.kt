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
        ReceivedSmsEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipientDao(): RecipientDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao
    abstract fun receivedSmsDao(): ReceivedSmsDao
}
