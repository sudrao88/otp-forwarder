package com.otpforwarder.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Replaces the per-rule `otp_log` table with a unified per-SMS `received_sms`
 * feed. Every broadcast the receiver delivers writes one row here; the row is
 * updated with classification + rule outcomes once the pipeline runs. The old
 * log was per-rule (one SMS could produce N rows) and never recorded
 * unmatched messages, which made silent misses invisible.
 *
 * Existing `otp_log` rows are dropped — the feed retains only a short window
 * (default 30 days) and the new schema is structurally different, so a
 * column-level migration would be more code than it is worth.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS otp_log")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS received_sms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                receivedAt INTEGER NOT NULL,
                otpCode TEXT,
                otpType TEXT,
                confidence REAL,
                classifierTier TEXT,
                processingStatus TEXT NOT NULL,
                matchedRuleNames TEXT NOT NULL,
                forwardedRecipients TEXT NOT NULL,
                summary TEXT NOT NULL,
                processedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_received_sms_receivedAt ON received_sms(receivedAt)")
    }
}
