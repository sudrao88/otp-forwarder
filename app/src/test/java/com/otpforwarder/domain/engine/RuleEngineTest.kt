package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Phase 2 tests: cover left-to-right AND/OR evaluation, every [OtpType] with the
 * `ALL` wildcard, invalid regex safety, and zero-condition always-matches behavior.
 */
class RuleEngineTest {

    private fun otp(
        type: OtpType = OtpType.TRANSACTION,
        sender: String = "HDFCBK",
        body: String = "INR 500 debited from a/c. OTP 482910."
    ) = Otp(
        code = "482910",
        type = type,
        sender = sender,
        originalMessage = body,
        detectedAt = Instant.parse("2026-04-18T00:00:00Z"),
        confidence = 0.95,
        classifierTier = ClassifierTier.KEYWORD
    )

    private fun rule(
        id: Long,
        conditions: List<RuleCondition>,
        isEnabled: Boolean = true,
        priority: Int = 1
    ) = ForwardingRule(
        id = id,
        name = "Rule $id",
        isEnabled = isEnabled,
        priority = priority,
        conditions = conditions,
        actions = listOf(RuleAction.ForwardSms(emptyList()))
    )

    private class FakeRuleRepo(
        private val rules: List<ForwardingRule>
    ) : ForwardingRuleRepository {
        override fun getAllRulesWithDetails(): Flow<List<ForwardingRule>> = flowOf(rules)
        override suspend fun getRuleWithDetailsById(id: Long): ForwardingRule? =
            rules.firstOrNull { it.id == id }
        override suspend fun getEnabledRulesWithDetails(): List<ForwardingRule> =
            rules.filter { it.isEnabled }
        override suspend fun insertRule(rule: ForwardingRule): Long = 0
        override suspend fun updateRule(rule: ForwardingRule) = Unit
        override suspend fun deleteRule(rule: ForwardingRule) = Unit
        override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> = emptyList()
    }

    private fun evaluate(
        rules: List<ForwardingRule>,
        otp: Otp = otp()
    ): List<ForwardingRule> = runBlocking {
        RuleEngine(FakeRuleRepo(rules)).evaluate(otp)
    }

    private fun otpType(type: OtpType, connector: Connector = Connector.AND) =
        RuleCondition.OtpTypeIs(type, connector)

    private fun sender(pattern: String, connector: Connector = Connector.AND) =
        RuleCondition.SenderMatches(pattern, connector)

    private fun body(pattern: String, connector: Connector = Connector.AND) =
        RuleCondition.BodyContains(pattern, connector)

    @Test
    fun `returns empty when no rules exist`() {
        assertTrue(evaluate(emptyList()).isEmpty())
    }

    @Test
    fun `returns empty when no rule matches`() {
        val r = rule(1, listOf(otpType(OtpType.LOGIN)))
        assertTrue(evaluate(listOf(r), otp(type = OtpType.TRANSACTION)).isEmpty())
    }

    @Test
    fun `skips disabled rules`() {
        val r = rule(1, listOf(otpType(OtpType.TRANSACTION)), isEnabled = false)
        assertTrue(evaluate(listOf(r)).isEmpty())
    }

    @Test
    fun `rule with zero conditions always matches`() {
        val r = rule(1, emptyList())
        OtpType.entries.forEach { t ->
            val result = evaluate(listOf(r), otp(type = t))
            assertEquals("zero conditions should match $t", 1, result.size)
        }
    }

    @Test
    fun `OtpType ALL matches every OtpType value`() {
        val r = rule(1, listOf(otpType(OtpType.ALL)))
        OtpType.entries.forEach { t ->
            val result = evaluate(listOf(r), otp(type = t))
            assertEquals("ALL did not match $t", 1, result.size)
        }
    }

    @Test
    fun `OtpType non-ALL only matches its own value`() {
        OtpType.entries.filter { it != OtpType.ALL }.forEach { declared ->
            val r = rule(1, listOf(otpType(declared)))
            OtpType.entries.forEach { actual ->
                val matched = evaluate(listOf(r), otp(type = actual)).isNotEmpty()
                val expected = declared == actual
                assertEquals(
                    "declared=$declared actual=$actual expected=$expected",
                    expected,
                    matched
                )
            }
        }
    }

    // Connector on conditions[0] is ignored — the first condition's truth value
    // seeds the accumulator; connector matters starting at index 1.
    @Test
    fun `connector on first condition is ignored`() {
        val withAnd = rule(1, listOf(otpType(OtpType.TRANSACTION, Connector.AND)))
        val withOr = rule(2, listOf(otpType(OtpType.TRANSACTION, Connector.OR)))
        assertEquals(1, evaluate(listOf(withAnd)).size)
        assertEquals(1, evaluate(listOf(withOr)).size)

        val falseAnd = rule(3, listOf(otpType(OtpType.LOGIN, Connector.AND)))
        val falseOr = rule(4, listOf(otpType(OtpType.LOGIN, Connector.OR)))
        assertTrue(evaluate(listOf(falseAnd)).isEmpty())
        assertTrue(evaluate(listOf(falseOr)).isEmpty())
    }

    // Exhaustive two-condition truth table for A op B.
    @Test
    fun `two-condition AND truth table`() {
        val t = otpType(OtpType.TRANSACTION) // true for TRANSACTION otp
        val f = otpType(OtpType.LOGIN)       // false for TRANSACTION otp
        val and = Connector.AND

        assertEquals(1, evaluate(listOf(rule(1, listOf(t, t.copy(connector = and))))).size)
        assertTrue(evaluate(listOf(rule(2, listOf(t, f.copy(connector = and))))).isEmpty())
        assertTrue(evaluate(listOf(rule(3, listOf(f, t.copy(connector = and))))).isEmpty())
        assertTrue(evaluate(listOf(rule(4, listOf(f, f.copy(connector = and))))).isEmpty())
    }

    @Test
    fun `two-condition OR truth table`() {
        val t = otpType(OtpType.TRANSACTION)
        val f = otpType(OtpType.LOGIN)
        val or = Connector.OR

        assertEquals(1, evaluate(listOf(rule(1, listOf(t, t.copy(connector = or))))).size)
        assertEquals(1, evaluate(listOf(rule(2, listOf(t, f.copy(connector = or))))).size)
        assertEquals(1, evaluate(listOf(rule(3, listOf(f, t.copy(connector = or))))).size)
        assertTrue(evaluate(listOf(rule(4, listOf(f, f.copy(connector = or))))).isEmpty())
    }

    // Left-to-right evaluation with mixed connectors: A AND B OR C
    // Parsed left-to-right: (A AND B) OR C — no precedence.
    @Test
    fun `mixed AND then OR evaluates left-to-right`() {
        val t = otpType(OtpType.TRANSACTION) // T for TRANSACTION otp
        val f = otpType(OtpType.LOGIN)       // F for TRANSACTION otp

        // T AND F OR T -> (T AND F) OR T -> F OR T -> T
        val r1 = rule(1, listOf(t, f.copy(connector = Connector.AND), t.copy(connector = Connector.OR)))
        assertEquals(1, evaluate(listOf(r1)).size)

        // T AND F OR F -> F OR F -> F
        val r2 = rule(2, listOf(t, f.copy(connector = Connector.AND), f.copy(connector = Connector.OR)))
        assertTrue(evaluate(listOf(r2)).isEmpty())

        // F OR T AND F -> (F OR T) AND F -> T AND F -> F
        // (If grouped right-to-left as F OR (T AND F) it would be F OR F = F as well,
        //  so pick a case where grouping matters to prove left-to-right.)
        val r3 = rule(3, listOf(f, t.copy(connector = Connector.OR), f.copy(connector = Connector.AND)))
        assertTrue(evaluate(listOf(r3)).isEmpty())

        // F OR T AND T -> (F OR T) AND T -> T AND T -> T
        val r4 = rule(4, listOf(f, t.copy(connector = Connector.OR), t.copy(connector = Connector.AND)))
        assertEquals(1, evaluate(listOf(r4)).size)

        // Proves left-to-right vs precedence: F AND T OR T.
        // Left-to-right: (F AND T) OR T -> F OR T -> T
        // Precedence (AND binds tighter): F AND T = F, then F OR T -> T
        // Both give T, so pick a distinguishing case: F OR F AND T.
        // Left-to-right: (F OR F) AND T -> F AND T -> F
        // Precedence: F OR (F AND T) -> F OR F -> F. Same — AND/OR over booleans coincide often.
        // Decisive case: T OR F AND F.
        // Left-to-right: (T OR F) AND F -> T AND F -> F
        // Precedence: T OR (F AND F) -> T OR F -> T
        val decisive = rule(5, listOf(t, f.copy(connector = Connector.OR), f.copy(connector = Connector.AND)))
        assertTrue("left-to-right must give F, not T", evaluate(listOf(decisive)).isEmpty())
    }

    @Test
    fun `three-condition exhaustive truth table holds left-to-right`() {
        val tCond = otpType(OtpType.TRANSACTION)
        val fCond = otpType(OtpType.LOGIN)
        var ruleId = 0L
        for (a in listOf(true, false)) {
            for (op1 in Connector.entries) {
                for (b in listOf(true, false)) {
                    for (op2 in Connector.entries) {
                        for (c in listOf(true, false)) {
                            val conds = listOf(
                                (if (a) tCond else fCond),
                                (if (b) tCond else fCond).copy(connector = op1),
                                (if (c) tCond else fCond).copy(connector = op2)
                            )
                            val step1 = when (op1) {
                                Connector.AND -> a && b
                                Connector.OR -> a || b
                            }
                            val expected = when (op2) {
                                Connector.AND -> step1 && c
                                Connector.OR -> step1 || c
                            }
                            val actual = evaluate(listOf(rule(++ruleId, conds))).isNotEmpty()
                            assertEquals(
                                "a=$a $op1 b=$b $op2 c=$c expected=$expected",
                                expected,
                                actual
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `sender regex matches against sender only`() {
        val r = rule(1, listOf(sender("HDFCBK.*")))
        assertEquals(1, evaluate(listOf(r), otp(sender = "HDFCBK")).size)
        assertTrue(evaluate(listOf(r), otp(sender = "SBIBNK")).isEmpty())
    }

    @Test
    fun `body regex matches against original message only`() {
        val r = rule(1, listOf(body("debited")))
        assertEquals(1, evaluate(listOf(r), otp(body = "a/c debited 500")).size)
        assertTrue(evaluate(listOf(r), otp(body = "a/c credited 500")).isEmpty())
    }

    @Test
    fun `sender regex does not match against body`() {
        val r = rule(1, listOf(sender("debited")))
        assertTrue(evaluate(listOf(r), otp(sender = "HDFCBK", body = "debited")).isEmpty())
    }

    @Test
    fun `body regex does not match against sender`() {
        val r = rule(1, listOf(body("HDFCBK")))
        assertTrue(evaluate(listOf(r), otp(sender = "HDFCBK", body = "your code is 1234")).isEmpty())
    }

    @Test
    fun `invalid regex evaluates to false and does not crash`() {
        val r1 = rule(1, listOf(sender("[invalid(regex")))
        assertTrue(evaluate(listOf(r1)).isEmpty())

        val r2 = rule(2, listOf(body("(unclosed")))
        assertTrue(evaluate(listOf(r2)).isEmpty())
    }

    @Test
    fun `invalid regex ORed with a true condition still lets the rule match`() {
        val r = rule(
            1,
            listOf(
                sender("[invalid("),
                otpType(OtpType.TRANSACTION, Connector.OR)
            )
        )
        assertEquals(1, evaluate(listOf(r)).size)
    }

    @Test
    fun `returns rules in repository order when multiple match`() {
        val r1 = rule(1, listOf(otpType(OtpType.ALL)))
        val r2 = rule(2, listOf(otpType(OtpType.TRANSACTION)))
        val result = evaluate(listOf(r1, r2))
        assertEquals(listOf(1L, 2L), result.map { it.id })
    }
}
