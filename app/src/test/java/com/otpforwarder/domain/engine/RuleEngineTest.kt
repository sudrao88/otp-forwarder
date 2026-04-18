package com.otpforwarder.domain.engine

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
        bodyFilter: String? = null
    ) = ForwardingRule(
        id = id,
        name = name,
        otpType = type,
        isEnabled = true,
        priority = priority,
        senderFilter = senderFilter,
        bodyFilter = bodyFilter
    )

    /**
     * Fake repository that returns [candidates] as-is from
     * [getMatchingRulesWithRecipients]. Emulates the SQL query that already
     * filters disabled rules/inactive recipients and handles type vs. ALL
     * matching — the RuleEngine's remaining job is regex post-filtering.
     */
    private class FakeRepo(
        private val candidates: List<Pair<ForwardingRule, List<Recipient>>>
    ) : ForwardingRuleRepository {
        override fun getAllRulesWithRecipients(): Flow<List<Pair<ForwardingRule, List<Recipient>>>> =
            flowOf(candidates)

        override fun getAllRules(): Flow<List<ForwardingRule>> =
            flowOf(candidates.map { it.first })

        override suspend fun getRuleById(id: Long): ForwardingRule? =
            candidates.firstOrNull { it.first.id == id }?.first

        override suspend fun getRuleWithRecipientsById(id: Long): Pair<ForwardingRule, List<Recipient>>? =
            candidates.firstOrNull { it.first.id == id }

        override suspend fun getMatchingRulesWithRecipients(
            otpType: OtpType
        ): List<Pair<ForwardingRule, List<Recipient>>> = candidates

        override suspend fun insertRule(rule: ForwardingRule, recipientIds: List<Long>): Long = 0
        override suspend fun updateRule(rule: ForwardingRule, recipientIds: List<Long>) = Unit
        override suspend fun deleteRule(rule: ForwardingRule) = Unit
        override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> = emptyList()
    }

    private fun evaluate(
        candidates: List<Pair<ForwardingRule, List<Recipient>>>,
        otp: Otp = otp()
    ): List<Pair<ForwardingRule, List<Recipient>>> =
        runBlocking { RuleEngine(FakeRepo(candidates)).evaluate(otp) }

    @Test
    fun `returns empty when repository yields no candidates`() {
        assertTrue(evaluate(emptyList()).isEmpty())
    }

    @Test
    fun `passes rule through when no filters are set`() {
        val r = rule(1)
        val result = evaluate(listOf(r to listOf(mom, dad)))
        assertEquals(1, result.size)
        assertEquals(r, result[0].first)
        assertEquals(listOf(mom, dad), result[0].second)
    }

    @Test
    fun `matches sender filter when regex hits`() {
        val r = rule(1, senderFilter = "HDFCBK.*")
        val result = evaluate(listOf(r to listOf(mom)))
        assertEquals(1, result.size)
    }

    @Test
    fun `rejects rule when sender filter does not match`() {
        val r = rule(1, senderFilter = "SBIBNK.*")
        val result = evaluate(listOf(r to listOf(mom)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `matches body filter when regex hits`() {
        val r = rule(1, bodyFilter = ".*debited.*")
        val result = evaluate(listOf(r to listOf(mom)))
        assertEquals(1, result.size)
    }

    @Test
    fun `rejects rule when body filter does not match`() {
        val r = rule(1, bodyFilter = ".*credited.*")
        val result = evaluate(listOf(r to listOf(mom)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `requires both sender and body filters to match when both are set`() {
        val both = rule(1, senderFilter = "HDFCBK.*", bodyFilter = ".*debited.*")
        assertEquals(1, evaluate(listOf(both to listOf(mom))).size)

        val senderOnly = rule(2, senderFilter = "HDFCBK.*", bodyFilter = ".*credited.*")
        assertTrue(evaluate(listOf(senderOnly to listOf(mom))).isEmpty())

        val bodyOnly = rule(3, senderFilter = "SBIBNK.*", bodyFilter = ".*debited.*")
        assertTrue(evaluate(listOf(bodyOnly to listOf(mom))).isEmpty())
    }

    @Test
    fun `treats invalid regex as non-match instead of throwing`() {
        val r = rule(1, senderFilter = "[invalid(regex")
        val result = evaluate(listOf(r to listOf(mom)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns every matching rule with its full recipient list when recipients overlap`() {
        val r1 = rule(1, name = "High", priority = 1)
        val r2 = rule(2, name = "Low", priority = 5)
        val result = evaluate(
            listOf(
                r1 to listOf(mom, dad),
                r2 to listOf(dad, sis)
            )
        )
        assertEquals(2, result.size)
        assertEquals("High", result[0].first.name)
        assertEquals(listOf(mom, dad), result[0].second)
        assertEquals("Low", result[1].first.name)
        assertEquals(listOf(dad, sis), result[1].second)
    }

    @Test
    fun `keeps rule even when every recipient is shared with a higher-priority rule`() {
        val r1 = rule(1, name = "High", priority = 1)
        val r2 = rule(2, name = "Redundant", priority = 2)
        val result = evaluate(
            listOf(
                r1 to listOf(mom, dad),
                r2 to listOf(mom, dad)
            )
        )
        assertEquals(2, result.size)
        assertEquals(listOf(mom, dad), result[0].second)
        assertEquals(listOf(mom, dad), result[1].second)
    }

    @Test
    fun `preserves priority order from repository`() {
        // Simulates the DAO's ordering: typed rules first, then ALL, each ordered by priority.
        val typed = rule(1, name = "Typed", type = OtpType.TRANSACTION, priority = 2)
        val all = rule(2, name = "Catch-all", type = OtpType.ALL, priority = 1)
        val result = evaluate(
            listOf(
                typed to listOf(mom),
                all to listOf(dad)
            )
        )
        assertEquals(listOf("Typed", "Catch-all"), result.map { it.first.name })
    }

    @Test
    fun `applies filters and returns full recipient lists for each surviving rule`() {
        val bankRule = rule(1, name = "Bank", priority = 1, senderFilter = "HDFCBK.*")
        val allRule = rule(2, name = "All", type = OtpType.ALL, priority = 10)
        val result = evaluate(
            listOf(
                bankRule to listOf(mom, dad),
                allRule to listOf(mom, sis)
            )
        )
        assertEquals(2, result.size)
        assertEquals(listOf(mom, dad), result[0].second)
        assertEquals(listOf(mom, sis), result[1].second)
    }

    @Test
    fun `sender filter evaluates against otp sender not message body`() {
        val r = rule(1, senderFilter = "debited")
        // Body contains "debited" but sender does not — should NOT match.
        val result = evaluate(
            listOf(r to listOf(mom)),
            otp = otp(sender = "HDFCBK", body = "Your a/c was debited. OTP 482910.")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `body filter evaluates against original message not sender`() {
        val r = rule(1, bodyFilter = "HDFCBK")
        // Sender contains "HDFCBK" but body does not — should NOT match.
        val result = evaluate(
            listOf(r to listOf(mom)),
            otp = otp(sender = "HDFCBK", body = "Your code is 482910.")
        )
        assertTrue(result.isEmpty())
    }
}
