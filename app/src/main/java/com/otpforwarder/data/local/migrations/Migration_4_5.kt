package com.otpforwarder.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `mapsAutoLaunch` column to `rule_actions` so the Phase 3
 * `OpenMapsNavigation` action can persist its per-rule auto-launch flag.
 * Existing rows default to 0 (auto-launch off), matching the domain default.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE rule_actions ADD COLUMN mapsAutoLaunch INTEGER NOT NULL DEFAULT 0"
        )
    }
}
