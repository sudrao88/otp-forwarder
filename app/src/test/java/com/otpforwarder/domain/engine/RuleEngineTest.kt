package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.RecipientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Phase 1 tests cover the stub engine semantics: simple AND-of-all conditions,
 * with `OtpType.ALL` acting as a wildcard. Phase 2 will replace these with the
 * full left-to-right AND/OR truth table and additional edge cases.
 */
class RuleEngineTest {

    private val mom = Recipient(id = 1, name = "Mom", phoneNumber = "+911", isActive = true)
    private val dad = Recipient(id = 2, name = "Dad", phoneNumber = "+912", isActive = true)
    private val sis = Recipient(id = 3, name = "Sister", phoneNumber = "+913", isActive = true)

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
        name: String = "Rule $id",
        type: OtpType = OtpType.TRANSACTION,
        priority: Int = 1,
        senderFilter: String? = null,
        bodyFilter: String? = null,
        recipientIds: List<Long> = emptyList()
    ): ForwardingRule {
        val conditions = buildList {
            add(RuleCondition.OtpTypeIs(type, Connector.AND))
            senderFilter?.let { add(RuleCondition.SenderMatches(it, Connector.AND)) }
            bodyFilter?.let { add(RuleCondition.BodyContains(it, Connector.AND)) }
        }
        return ForwardingRule(
            id = id,
            name = name,
            isEnabled = true,
            priority = priority,
            conditions = conditions,
            actions = listOf(RuleAction.ForwardSms(recipientIds))
        )
    }

    private class FakeRuleRepo(
        private val rules: List<ForwardingRule>
    ) : ForwardingRuleRepository {
        override fun getAllRulesWithDetails(): Flow<List<ForwardingRule>> = flowOf(rules)
        override fun getAllRules(): Flow<List<ForwardingRule>> = flowOf(rules)
        override suspend fun getRuleById(id: Long): ForwardingRule? =
            rules.firstOrNull { it.id == id }
        override suspend fun getRuleWithDetailsById(id: Long): ForwardingRule? =
            rules.firstOrNull { it.id == id }
        override suspend fun getEnabledRulesWithDetails(): List<ForwardingRule> =
            rules.filter { it.isEnabled }
        override suspend fun insertRule(rule: ForwardingRule): Long = 0
        override suspend fun updateRule(rule: ForwardingRule) = Unit
        override suspend fun deleteRule(rule: ForwardingRule) = Unit
        override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> = emptyList()
    }

    private class FakeRecipientRepo(
        private val recipients: List<Recipient>
    ) : RecipientRepository {
        override fun getAllRecipients(): Flow<List<Recipient>> = flowOf(recipients)
        override suspend fun getRecipientById(id: Long): Recipient? =
            recipients.firstOrNull { it.id == id }
        override suspend fun getActiveRecipients(): List<Recipient> =
            recipients.filter { it.isActive }
        override suspend fun getRecipientsForRule(ruleId: Long): List<Recipient> = emptyList()
        override suspend fun insertRecipient(recipient: Recipient): Long = 0
        override suspend fun updateRecipient(recipient: Recipient) = Unit
        override suspend fun deleteRecipient(recipient: Recipient) = Unit
    }

    private fun evaluate(
        rules: List<ForwardingRule>,
        otp: Otp = otp(),
        recipients: List<Recipient> = listOf(mom, dad, sis)
    ): List<Pair<ForwardingRule, List<Recipient>>> = runBlocking {
        RuleEngine(FakeRuleRepo(rules), FakeRecipientRepo(recipients)).evaluate(otp)
    }

    @Test
    fun `returns empty when no rules exist`() {
        assertTrue(evaluate(emptyList()).isEmpty())
    }

    @Test
    fun `passes rule when only OtpType condition matches`() {
        val r = rule(1, recipientIds = listOf(mom.id, dad.id))
        val result = evaluate(listOf(r))
        assertEquals(1, result.size)
        assertEquals(listOf(mom, dad), result[0].second)
    }

    @Test
    fun `OtpType ALL matches any otp type`() {
        val r = rule(1, type = OtpType.ALL, recipientIds = listOf(mom.id))
        OtpType.entries.forEach { t ->
            val result = evaluate(listOf(r), otp = otp(type = t))
            assertEquals("ALL did not match $t", 1, result.size)
        }
    }

    @Test
    fun `rejects rule when otp type does not match and is not ALL`() {
        val r = rule(1, type = OtpType.LOGIN, recipientIds = listOf(mom.id))
        val result = evaluate(listOf(r), otp = otp(type = OtpType.TRANSACTION))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matches sender filter when regex hits`() {
        val r = rule(1, senderFilter = "HDFCBK.*", recipientIds = listOf(mom.id))
        val result = evaluate(listOf(r))
        assertEquals(1, result.size)
    }

    @Test
    fun `rejects rule when sender filter does not match`() {
        val r = rule(1, senderFilter = "SBIBNK.*", recipientIds = listOf(mom.id))
        assertTrue(evaluate(listOf(r)).isEmpty())
    }

    @Test
    fun `matches body filter when regex hits`() {
        val r = rule(1, bodyFilter = ".*debited.*", recipientIds = listOf(mom.id))
        assertEquals(1, evaluate(listOf(r)).size)
    }

    @Test
    fun `rejects rule when body filter does not match`() {
        val r = rule(1, bodyFilter = ".*credited.*", recipientIds = listOf(mom.id))
        assertTrue(evaluate(listOf(r)).isEmpty())
    }

    @Test
    fun `requires every condition to match (AND semantics)`() {
        val both = rule(1, senderFilter = "HDFCBK.*", bodyFilter = ".*debited.*", recipientIds = listOf(mom.id))
        assertEquals(1, evaluate(listOf(both)).size)

        val senderMismatch = rule(2, senderFilter = "SBIBNK.*", bodyFilter = ".*debited.*", recipientIds = listOf(mom.id))
        assertTrue(evaluate(listOf(senderMismatch)).isEmpty())

        val bodyMismatch = rule(3, senderFilter = "HDFCBK.*", bodyFilter = ".*credited.*", recipientIds = listOf(mom.id))
        assertTrue(evaluate(listOf(bodyMismatch)).isEmpty())
    }

    @Test
    fun `treats invalid regex as non-match instead of throwing`() {
        val r = rule(1, senderFilter = "[invalid(regex", recipientIds = listOf(mom.id))
        assertTrue(evaluate(listOf(r)).isEmpty())
    }

    @Test
    fun `skips disabled rules`() {
        val disabled = rule(1, recipientIds = listOf(mom.id)).copy(isEnabled = false)
        assertTrue(evaluate(listOf(disabled)).isEmpty())
    }

    @Test
    fun `excludes inactive recipients from the returned list`() {
        val inactive = mom.copy(isActive = false)
        val r = rule(1, recipientIds = listOf(mom.id, dad.id))
        val result = evaluate(listOf(r), recipients = listOf(inactive, dad, sis))
        assertEquals(1, result.size)
        assertEquals(listOf(dad), result[0].second)
    }

    @Test
    fun `returns each matching rule with its own recipient list`() {
        val r1 = rule(1, name = "High", priority = 1, recipientIds = listOf(mom.id, dad.id))
        val r2 = rule(2, name = "Low", priority = 5, recipientIds = listOf(dad.id, sis.id))
        val result = evaluate(listOf(r1, r2))
        assertEquals(2, result.size)
        assertEquals("High", result[0].first.name)
        assertEquals(listOf(mom, dad), result[0].second)
        assertEquals("Low", result[1].first.name)
        assertEquals(listOf(dad, sis), result[1].second)
    }

    @Test
    fun `rule with zero conditions matches anything`() {
        val empty = ForwardingRule(
            id = 1,
            name = "Catch-all",
            isEnabled = true,
            priority = 1,
            conditions = emptyList(),
            actions = listOf(RuleAction.ForwardSms(listOf(mom.id)))
        )
        val result = evaluate(listOf(empty), otp = otp(type = OtpType.UNKNOWN))
        assertEquals(1, result.size)
    }

    @Test
    fun `sender filter evaluates against otp sender not message body`() {
        val r = rule(1, senderFilter = "debited", recipientIds = listOf(mom.id))
        val result = evaluate(
            listOf(r),
            otp = otp(sender = "HDFCBK", body = "Your a/c was debited. OTP 482910.")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `body filter evaluates against original message not sender`() {
        val r = rule(1, bodyFilter = "HDFCBK", recipientIds = listOf(mom.id))
        val result = evaluate(
            listOf(r),
            otp = otp(sender = "HDFCBK", body = "Your code is 482910.")
        )
        assertTrue(result.isEmpty())
    }
}
