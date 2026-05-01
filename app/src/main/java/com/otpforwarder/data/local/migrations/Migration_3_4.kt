package com.otpforwarder.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Relax NOT NULL on the OTP-derived columns of `otp_log` so that rule firings
 * on non-OTP SMS can still be logged. SQLite cannot ALTER a column's NOT NULL
 * constraint in place, so the table is rebuilt with the same column order.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE otp_log_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT,
                otpType TEXT,
                sender TEXT NOT NULL,
                originalMessage TEXT NOT NULL,
                detectedAt INTEGER NOT NULL,
                confidence REAL,
                classifierTier TEXT,
                ruleName TEXT NOT NULL,
                recipientNames TEXT NOT NULL,
                status TEXT NOT NULL,
                forwardedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO otp_log_new (
                id, code, otpType, sender, originalMessage, detectedAt, confidence,
                classifierTier, ruleName, recipientNames, status, forwardedAt
            )
            SELECT id, code, otpType, sender, originalMessage, detectedAt, confidence,
                   classifierTier, ruleName, recipientNames, status, forwardedAt
            FROM otp_log
            """.trimIndent()
        )
        db.execSQL("DROP TABLE otp_log")
        db.execSQL("ALTER TABLE otp_log_new RENAME TO otp_log")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_otp_log_forwardedAt ON otp_log(forwardedAt)")
    }
}
