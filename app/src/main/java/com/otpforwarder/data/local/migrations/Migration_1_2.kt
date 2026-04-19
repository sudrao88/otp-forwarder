package com.otpforwarder.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rule_conditions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ruleId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                connector TEXT NOT NULL,
                conditionType TEXT NOT NULL,
                otpTypeValue TEXT,
                pattern TEXT,
                FOREIGN KEY(ruleId) REFERENCES forwarding_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_conditions_ruleId ON rule_conditions(ruleId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rule_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ruleId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                actionType TEXT NOT NULL,
                callRecipientId INTEGER,
                FOREIGN KEY(ruleId) REFERENCES forwarding_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(callRecipientId) REFERENCES recipients(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_ruleId ON rule_actions(ruleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_rule_actions_callRecipientId ON rule_actions(callRecipientId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS action_recipient_cross_ref (
                actionId INTEGER NOT NULL,
                recipientId INTEGER NOT NULL,
                PRIMARY KEY(actionId, recipientId),
                FOREIGN KEY(actionId) REFERENCES rule_actions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(recipientId) REFERENCES recipients(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_recipient_cross_ref_recipientId ON action_recipient_cross_ref(recipientId)")

        db.execSQL(
            """
            INSERT INTO rule_conditions (ruleId, orderIndex, connector, conditionType, otpTypeValue, pattern)
            SELECT id, 0, 'AND', 'OTP_TYPE', otpType, NULL FROM forwarding_rules
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO rule_conditions (ruleId, orderIndex, connector, conditionType, otpTypeValue, pattern)
            SELECT id, 1, 'AND', 'SENDER_REGEX', NULL, senderFilter
            FROM forwarding_rules
            WHERE senderFilter IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO rule_conditions (ruleId, orderIndex, connector, conditionType, otpTypeValue, pattern)
            SELECT id,
                   CASE WHEN senderFilter IS NOT NULL THEN 2 ELSE 1 END,
                   'AND',
                   'BODY_REGEX',
                   NULL,
                   bodyFilter
            FROM forwarding_rules
            WHERE bodyFilter IS NOT NULL
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO rule_actions (ruleId, orderIndex, actionType, callRecipientId)
            SELECT id, 0, 'FORWARD_SMS', NULL FROM forwarding_rules
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO action_recipient_cross_ref (actionId, recipientId)
            SELECT a.id, x.recipientId
            FROM rule_recipient_cross_ref x
            JOIN rule_actions a
              ON a.ruleId = x.ruleId
             AND a.actionType = 'FORWARD_SMS'
            """.trimIndent()
        )

        db.execSQL("DROP TABLE rule_recipient_cross_ref")

        db.execSQL(
            """
            CREATE TABLE forwarding_rules_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isEnabled INTEGER NOT NULL,
                priority INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO forwarding_rules_new (id, name, isEnabled, priority)
            SELECT id, name, isEnabled, priority FROM forwarding_rules
            """.trimIndent()
        )
        db.execSQL("DROP TABLE forwarding_rules")
        db.execSQL("ALTER TABLE forwarding_rules_new RENAME TO forwarding_rules")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_forwarding_rules_priority ON forwarding_rules(priority)")
    }
}
