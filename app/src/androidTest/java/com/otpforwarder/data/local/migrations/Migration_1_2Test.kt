package com.otpforwarder.data.local.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otpforwarder.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [MIGRATION_1_2] end-to-end. [MigrationTestHelper.runMigrationsAndValidate]
 * checks the post-migration schema (columns, indexes, FK actions) against the exported
 * v2 JSON; the row-level assertions on top verify the data transformations.
 */
@RunWith(AndroidJUnit4::class)
class Migration_1_2Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration_1_2_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migratesRuleWithAllFiltersAndRecipients() {
        helper.createDatabase(dbName, 1).use { db ->
            seedRecipient(db, id = 1, name = "Mom", phone = "+111")
            seedRecipient(db, id = 2, name = "Dad", phone = "+222")
            seedV1Rule(
                db, id = 10, name = "Bank OTPs", otpType = "TRANSACTION",
                isEnabled = true, priority = 1,
                senderFilter = "HDFCBK", bodyFilter = "credited"
            )
            seedV1CrossRef(db, ruleId = 10, recipientId = 1)
            seedV1CrossRef(db, ruleId = 10, recipientId = 2)
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // Rule row preserved.
        queryOne(db, "SELECT id, name, isEnabled, priority FROM forwarding_rules WHERE id = 10") { c ->
            assertEquals(10L, c.getLong(0))
            assertEquals("Bank OTPs", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(1, c.getInt(3))
        }

        // forwarding_rules should no longer have the old columns.
        assertFalse("otpType column should be dropped", columnExists(db, "forwarding_rules", "otpType"))
        assertFalse("senderFilter column should be dropped", columnExists(db, "forwarding_rules", "senderFilter"))
        assertFalse("bodyFilter column should be dropped", columnExists(db, "forwarding_rules", "bodyFilter"))

        // Three conditions in order: OTP_TYPE, SENDER_REGEX, BODY_REGEX, all AND.
        val conditions = mutableListOf<Triple<Int, String, Pair<String?, String?>>>()
        queryAll(db, "SELECT orderIndex, connector, conditionType, otpTypeValue, pattern FROM rule_conditions WHERE ruleId = 10 ORDER BY orderIndex") { c ->
            conditions += Triple(
                c.getInt(0),
                c.getString(1) + "|" + c.getString(2),
                (if (c.isNull(3)) null else c.getString(3)) to (if (c.isNull(4)) null else c.getString(4))
            )
        }
        assertEquals(3, conditions.size)
        assertEquals(0 to "AND|OTP_TYPE", conditions[0].first to conditions[0].second)
        assertEquals("TRANSACTION" to null, conditions[0].third)
        assertEquals(1 to "AND|SENDER_REGEX", conditions[1].first to conditions[1].second)
        assertEquals(null to "HDFCBK", conditions[1].third)
        assertEquals(2 to "AND|BODY_REGEX", conditions[2].first to conditions[2].second)
        assertEquals(null to "credited", conditions[2].third)

        // One FORWARD_SMS action.
        val actionIds = mutableListOf<Long>()
        queryAll(db, "SELECT id, ruleId, orderIndex, actionType, callRecipientId FROM rule_actions WHERE ruleId = 10") { c ->
            actionIds += c.getLong(0)
            assertEquals(10L, c.getLong(1))
            assertEquals(0, c.getInt(2))
            assertEquals("FORWARD_SMS", c.getString(3))
            assertTrue("callRecipientId should be null for FORWARD_SMS", c.isNull(4))
        }
        assertEquals(1, actionIds.size)

        // Action cross-refs carry both original recipients.
        val actionRecipients = mutableListOf<Pair<Long, Long>>()
        queryAll(db, "SELECT actionId, recipientId FROM action_recipient_cross_ref ORDER BY recipientId") { c ->
            actionRecipients += c.getLong(0) to c.getLong(1)
        }
        assertEquals(
            listOf(actionIds.single() to 1L, actionIds.single() to 2L),
            actionRecipients
        )

        // Old cross-ref table must be gone.
        assertFalse("rule_recipient_cross_ref should be dropped", tableExists(db, "rule_recipient_cross_ref"))
        db.close()
    }

    @Test
    fun migratesRuleWithNoOptionalFilters() {
        helper.createDatabase(dbName, 1).use { db ->
            seedRecipient(db, id = 1, name = "Mom", phone = "+111")
            seedV1Rule(
                db, id = 20, name = "All OTPs", otpType = "ALL",
                isEnabled = false, priority = 5,
                senderFilter = null, bodyFilter = null
            )
            seedV1CrossRef(db, ruleId = 20, recipientId = 1)
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // Exactly one condition (OTP_TYPE), no sender/body conditions.
        val conditionTypes = mutableListOf<String>()
        queryAll(db, "SELECT conditionType FROM rule_conditions WHERE ruleId = 20 ORDER BY orderIndex") { c ->
            conditionTypes += c.getString(0)
        }
        assertEquals(listOf("OTP_TYPE"), conditionTypes)

        // isEnabled flag preserved through the table rebuild.
        queryOne(db, "SELECT isEnabled, priority FROM forwarding_rules WHERE id = 20") { c ->
            assertEquals(0, c.getInt(0))
            assertEquals(5, c.getInt(1))
        }

        // One FORWARD_SMS action with one recipient cross-ref.
        val actionId = queryLong(db, "SELECT id FROM rule_actions WHERE ruleId = 20")
        val recipientIds = mutableListOf<Long>()
        queryAll(db, "SELECT recipientId FROM action_recipient_cross_ref WHERE actionId = $actionId") { c ->
            recipientIds += c.getLong(0)
        }
        assertEquals(listOf(1L), recipientIds)
        db.close()
    }

    @Test
    fun migrationOrdersBodyConditionAfterSenderWhenBothPresent() {
        helper.createDatabase(dbName, 1).use { db ->
            seedV1Rule(
                db, id = 30, name = "Body only", otpType = "LOGIN",
                isEnabled = true, priority = 0,
                senderFilter = null, bodyFilter = "code"
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // With no sender filter, body should land at orderIndex 1 (right after OTP_TYPE at 0).
        val byOrder = mutableListOf<Pair<Int, String>>()
        queryAll(db, "SELECT orderIndex, conditionType FROM rule_conditions WHERE ruleId = 30 ORDER BY orderIndex") { c ->
            byOrder += c.getInt(0) to c.getString(1)
        }
        assertEquals(listOf(0 to "OTP_TYPE", 1 to "BODY_REGEX"), byOrder)
        db.close()
    }

    /**
     * Two v1 rules with distinct recipient sets guard the recipient-reparenting JOIN —
     * each rule's new FORWARD_SMS action must only receive its own recipients, never
     * the other rule's.
     */
    @Test
    fun migratesMultipleRulesWithoutCrossContaminatingRecipients() {
        helper.createDatabase(dbName, 1).use { db ->
            seedRecipient(db, id = 1, name = "Mom", phone = "+111")
            seedRecipient(db, id = 2, name = "Dad", phone = "+222")
            seedRecipient(db, id = 3, name = "Sis", phone = "+333")
            seedRecipient(db, id = 4, name = "Bro", phone = "+444")

            seedV1Rule(
                db, id = 100, name = "Bank rule", otpType = "TRANSACTION",
                isEnabled = true, priority = 1,
                senderFilter = "HDFCBK", bodyFilter = null
            )
            seedV1CrossRef(db, ruleId = 100, recipientId = 1)
            seedV1CrossRef(db, ruleId = 100, recipientId = 2)

            seedV1Rule(
                db, id = 200, name = "Login rule", otpType = "LOGIN",
                isEnabled = true, priority = 2,
                senderFilter = null, bodyFilter = "code"
            )
            seedV1CrossRef(db, ruleId = 200, recipientId = 3)
            seedV1CrossRef(db, ruleId = 200, recipientId = 4)
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        val action100 = queryLong(db, "SELECT id FROM rule_actions WHERE ruleId = 100")
        val action200 = queryLong(db, "SELECT id FROM rule_actions WHERE ruleId = 200")

        val recipients100 = mutableListOf<Long>()
        queryAll(db, "SELECT recipientId FROM action_recipient_cross_ref WHERE actionId = $action100 ORDER BY recipientId") { c ->
            recipients100 += c.getLong(0)
        }
        val recipients200 = mutableListOf<Long>()
        queryAll(db, "SELECT recipientId FROM action_recipient_cross_ref WHERE actionId = $action200 ORDER BY recipientId") { c ->
            recipients200 += c.getLong(0)
        }

        assertEquals(listOf(1L, 2L), recipients100)
        assertEquals(listOf(3L, 4L), recipients200)
        db.close()
    }

    /**
     * A v1 database that has never had a rule created must still upgrade cleanly —
     * new tables exist, old columns are gone, and nothing throws.
     */
    @Test
    fun migratesEmptyV1Database() {
        helper.createDatabase(dbName, 1).use { /* no seed */ }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        assertTrue("rule_conditions must exist", tableExists(db, "rule_conditions"))
        assertTrue("rule_actions must exist", tableExists(db, "rule_actions"))
        assertTrue("action_recipient_cross_ref must exist", tableExists(db, "action_recipient_cross_ref"))
        assertFalse("rule_recipient_cross_ref must be dropped", tableExists(db, "rule_recipient_cross_ref"))
        assertFalse("otpType column must be dropped", columnExists(db, "forwarding_rules", "otpType"))

        assertEquals(0L, countRows(db, "forwarding_rules"))
        assertEquals(0L, countRows(db, "rule_conditions"))
        assertEquals(0L, countRows(db, "rule_actions"))
        assertEquals(0L, countRows(db, "action_recipient_cross_ref"))
        db.close()
    }

    /**
     * The migration rebuilds `forwarding_rules` but must not touch `otp_log`. A seeded
     * log row from v1 should survive the upgrade intact.
     */
    @Test
    fun preservesOtpLogAcrossMigration() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO otp_log (
                    id, code, otpType, sender, originalMessage, detectedAt, confidence,
                    classifierTier, ruleName, recipientNames, status, forwardedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    42L, "123456", "TRANSACTION", "HDFCBK",
                    "Your OTP is 123456", 1700000000000L, 0.95,
                    "KEYWORD", "Bank rule", "Mom; Dad", "SENT", 1700000001000L
                )
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        queryOne(
            db,
            "SELECT id, code, otpType, sender, originalMessage, detectedAt, confidence, " +
                "classifierTier, ruleName, recipientNames, status, forwardedAt FROM otp_log WHERE id = 42"
        ) { c ->
            assertEquals(42L, c.getLong(0))
            assertEquals("123456", c.getString(1))
            assertEquals("TRANSACTION", c.getString(2))
            assertEquals("HDFCBK", c.getString(3))
            assertEquals("Your OTP is 123456", c.getString(4))
            assertEquals(1700000000000L, c.getLong(5))
            assertEquals(0.95, c.getDouble(6), 0.0001)
            assertEquals("KEYWORD", c.getString(7))
            assertEquals("Bank rule", c.getString(8))
            assertEquals("Mom; Dad", c.getString(9))
            assertEquals("SENT", c.getString(10))
            assertEquals(1700000001000L, c.getLong(11))
        }
        db.close()
    }

    // --- helpers ---------------------------------------------------------

    private fun seedRecipient(db: SupportSQLiteDatabase, id: Long, name: String, phone: String) {
        db.execSQL(
            "INSERT INTO recipients (id, name, phoneNumber, isActive) VALUES (?, ?, ?, 1)",
            arrayOf<Any>(id, name, phone)
        )
    }

    private fun seedV1Rule(
        db: SupportSQLiteDatabase,
        id: Long,
        name: String,
        otpType: String,
        isEnabled: Boolean,
        priority: Int,
        senderFilter: String?,
        bodyFilter: String?
    ) {
        db.execSQL(
            "INSERT INTO forwarding_rules (id, name, otpType, isEnabled, priority, senderFilter, bodyFilter) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, name, otpType, if (isEnabled) 1 else 0, priority, senderFilter, bodyFilter)
        )
    }

    private fun seedV1CrossRef(db: SupportSQLiteDatabase, ruleId: Long, recipientId: Long) {
        db.execSQL(
            "INSERT INTO rule_recipient_cross_ref (ruleId, recipientId) VALUES (?, ?)",
            arrayOf<Any>(ruleId, recipientId)
        )
    }

    private inline fun queryOne(db: SupportSQLiteDatabase, sql: String, block: (android.database.Cursor) -> Unit) {
        db.query(sql).use { c ->
            assertTrue("expected one row for: $sql", c.moveToFirst())
            block(c)
            assertFalse("expected exactly one row for: $sql", c.moveToNext())
        }
    }

    private inline fun queryAll(db: SupportSQLiteDatabase, sql: String, block: (android.database.Cursor) -> Unit) {
        db.query(sql).use { c ->
            while (c.moveToNext()) block(c)
        }
    }

    private fun queryLong(db: SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { c ->
            assertTrue("expected one row for: $sql", c.moveToFirst())
            return c.getLong(0)
        }
    }

    private fun countRows(db: SupportSQLiteDatabase, table: String): Long =
        queryLong(db, "SELECT COUNT(*) FROM $table")

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name = ?", arrayOf<Any>(tableName)).use { c ->
            return c.moveToFirst()
        }
    }

    private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        db.query("PRAGMA table_info($tableName)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == columnName) return true
            }
            return false
        }
    }
}
