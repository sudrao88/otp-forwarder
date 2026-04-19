package com.otpforwarder.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [MIGRATION_1_2] end-to-end against a real SQLite database: seeds v1 tables
 * with data, runs the migration, and verifies the v2 row shapes. Uses raw
 * [SupportSQLiteOpenHelper] (not Room's [MigrationTestHelper]) so no schema JSON
 * needs to be exported.
 */
@RunWith(AndroidJUnit4::class)
class Migration_1_2Test {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "migration_1_2_test.db"

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createV1Schema(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migratesRuleWithAllFiltersAndRecipients() {
        val db = helper.writableDatabase
        seedRecipient(db, id = 1, name = "Mom", phone = "+111")
        seedRecipient(db, id = 2, name = "Dad", phone = "+222")
        seedV1Rule(
            db, id = 10, name = "Bank OTPs", otpType = "TRANSACTION",
            isEnabled = true, priority = 1,
            senderFilter = "HDFCBK", bodyFilter = "credited"
        )
        seedV1CrossRef(db, ruleId = 10, recipientId = 1)
        seedV1CrossRef(db, ruleId = 10, recipientId = 2)

        MIGRATION_1_2.migrate(db)

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
    }

    @Test
    fun migratesRuleWithNoOptionalFilters() {
        val db = helper.writableDatabase
        seedRecipient(db, id = 1, name = "Mom", phone = "+111")
        seedV1Rule(
            db, id = 20, name = "All OTPs", otpType = "ALL",
            isEnabled = false, priority = 5,
            senderFilter = null, bodyFilter = null
        )
        seedV1CrossRef(db, ruleId = 20, recipientId = 1)

        MIGRATION_1_2.migrate(db)

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
    }

    @Test
    fun migrationOrdersBodyConditionAfterSenderWhenBothPresent() {
        val db = helper.writableDatabase
        seedV1Rule(
            db, id = 30, name = "Body only", otpType = "LOGIN",
            isEnabled = true, priority = 0,
            senderFilter = null, bodyFilter = "code"
        )

        MIGRATION_1_2.migrate(db)

        // With no sender filter, body should land at orderIndex 1 (right after OTP_TYPE at 0).
        val byOrder = mutableListOf<Pair<Int, String>>()
        queryAll(db, "SELECT orderIndex, conditionType FROM rule_conditions WHERE ruleId = 30 ORDER BY orderIndex") { c ->
            byOrder += c.getInt(0) to c.getString(1)
        }
        assertEquals(listOf(0 to "OTP_TYPE", 1 to "BODY_REGEX"), byOrder)
    }

    // --- helpers ---------------------------------------------------------

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE recipients (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phoneNumber TEXT NOT NULL,
                isActive INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE forwarding_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                otpType TEXT NOT NULL,
                isEnabled INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                senderFilter TEXT,
                bodyFilter TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_forwarding_rules_priority ON forwarding_rules(priority)")
        db.execSQL(
            """
            CREATE TABLE rule_recipient_cross_ref (
                ruleId INTEGER NOT NULL,
                recipientId INTEGER NOT NULL,
                PRIMARY KEY(ruleId, recipientId),
                FOREIGN KEY(ruleId) REFERENCES forwarding_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(recipientId) REFERENCES recipients(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_rule_recipient_cross_ref_recipientId ON rule_recipient_cross_ref(recipientId)")
        db.execSQL(
            """
            CREATE TABLE otp_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                otpType TEXT NOT NULL,
                sender TEXT NOT NULL,
                originalMessage TEXT NOT NULL,
                detectedAt INTEGER NOT NULL,
                confidence REAL NOT NULL,
                classifierTier TEXT NOT NULL,
                ruleName TEXT NOT NULL,
                recipientNames TEXT NOT NULL,
                status TEXT NOT NULL,
                forwardedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

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
