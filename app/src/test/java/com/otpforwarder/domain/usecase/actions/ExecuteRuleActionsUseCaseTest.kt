package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.sms.SmsSender
import com.otpforwarder.domain.usecase.actions.ExecuteRuleActionsUseCase.ActionOutcome.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Covers the per-action dispatcher invariants:
 *  - every action runs in declaration order;
 *  - one action's failure does not abort the rest (isolation);
 *  - forward-recipient dedup is honoured across actions for the same OTP;
 *  - per-action summaries reflect what actually happened.
 */
class ExecuteRuleActionsUseCaseTest {

    private val mom = Recipient(id = 1, name = "Mom", phoneNumber = "+111")
    private val dad = Recipient(id = 2, name = "Dad", phoneNumber = "+222")
    private val recipientsById = mapOf(mom.id to mom, dad.id to dad)

    private val otp = Otp(
        code = "482910",
        type = OtpType.TRANSACTION,
        sender = "HDFCBK",
        originalMessage = "OTP 482910",
        detectedAt = Instant.parse("2026-04-19T00:00:00Z"),
        confidence = 0.95,
        classifierTier = ClassifierTier.KEYWORD
    )

    private class FakeSmsSender(
        val failFor: Set<String> = emptySet()
    ) : SmsSender {
        val sent = mutableListOf<Pair<String, String>>()
        override fun send(phoneNumber: String, message: String): Boolean {
            sent += phoneNumber to message
            return phoneNumber !in failFor
        }
    }

    private class FakeRingerLoud(private val result: SetRingerLoudResult) : SetRingerLoudAction {
        var calls: Int = 0
        override fun invoke(): SetRingerLoudResult {
            calls++
            return result
        }
    }

    private class FakePlaceCall(
        private val fail: Set<Long> = emptySet()
    ) : PlaceCallAction {
        val called = mutableListOf<Recipient>()
        override fun invoke(recipient: Recipient): PlaceCallResult {
            called += recipient
            return PlaceCallResult(
                recipient = recipient,
                success = recipient.id !in fail,
                reason = if (recipient.id in fail) "CALL_PHONE not granted" else null
            )
        }
    }

    private fun dispatcher(
        sender: SmsSender = FakeSmsSender(),
        ringer: SetRingerLoudAction = FakeRingerLoud(SetRingerLoudResult(true, true)),
        call: PlaceCallAction = FakePlaceCall()
    ) = ExecuteRuleActionsUseCase(
        forwardSms = ForwardSmsActionUseCase(sender),
        setRingerLoud = ringer,
        placeCall = call
    )

    private fun run(
        actions: List<RuleAction>,
        sender: SmsSender = FakeSmsSender(),
        ringer: SetRingerLoudAction = FakeRingerLoud(SetRingerLoudResult(true, true)),
        call: PlaceCallAction = FakePlaceCall(),
        alreadySentTo: MutableSet<Long> = mutableSetOf()
    ) = runBlocking {
        dispatcher(sender, ringer, call)(otp, actions, recipientsById, alreadySentTo)
    }

    @Test
    fun `runs every action in declaration order`() {
        val sender = FakeSmsSender()
        val ringer = FakeRingerLoud(SetRingerLoudResult(true, true))
        val call = FakePlaceCall()
        val actions = listOf(
            RuleAction.ForwardSms(listOf(mom.id)),
            RuleAction.SetRingerLoud,
            RuleAction.PlaceCall(dad.id)
        )
        val outcomes = run(actions, sender, ringer, call)

        assertEquals(3, outcomes.size)
        assertEquals(actions, outcomes.map { it.action })
        assertTrue(outcomes.all { it.success })
        assertTrue(outcomes.all { it.status == Status.SUCCESS })
        assertEquals(listOf("+111" to "OTP 482910"), sender.sent)
        assertEquals(1, ringer.calls)
        assertEquals(listOf(dad), call.called)
    }

    @Test
    fun `per-action failure is isolated — PlaceCall fails, forward still succeeds`() {
        val sender = FakeSmsSender()
        val call = FakePlaceCall(fail = setOf(dad.id))
        val outcomes = run(
            actions = listOf(
                RuleAction.ForwardSms(listOf(mom.id)),
                RuleAction.PlaceCall(dad.id)
            ),
            sender = sender,
            call = call
        )

        assertEquals(2, outcomes.size)
        assertTrue("forward should succeed", outcomes[0].success)
        assertFalse("call should fail", outcomes[1].success)
        assertEquals(Status.SUCCESS, outcomes[0].status)
        assertEquals(Status.FAILED, outcomes[1].status)
        assertEquals(listOf("+111" to "OTP 482910"), sender.sent)
        assertEquals(listOf(dad), call.called)
        assertTrue(outcomes[0].summary.contains("Mom"))
        assertTrue(outcomes[1].summary.contains("Dad"))
    }

    @Test
    fun `ringer failure does not abort forward action`() {
        val sender = FakeSmsSender()
        val ringer = FakeRingerLoud(SetRingerLoudResult(ringerChanged = false, bypassedDnd = false))
        val outcomes = run(
            actions = listOf(RuleAction.SetRingerLoud, RuleAction.ForwardSms(listOf(mom.id))),
            sender = sender,
            ringer = ringer
        )

        assertEquals(2, outcomes.size)
        assertFalse(outcomes[0].success)
        assertTrue(outcomes[1].success)
        assertEquals(listOf("+111" to "OTP 482910"), sender.sent)
    }

    @Test
    fun `forward dedupes recipients already attempted, summary tags them as 'already sent'`() {
        val sender = FakeSmsSender()
        val alreadySent = mutableSetOf<Long>()
        // First rule forwards to Mom + Dad.
        run(
            actions = listOf(RuleAction.ForwardSms(listOf(mom.id, dad.id))),
            sender = sender,
            alreadySentTo = alreadySent
        )
        assertEquals(setOf(mom.id, dad.id), alreadySent)
        assertEquals(2, sender.sent.size)

        // Second rule re-targets Mom + Dad — dedup skips both, no extra SMS.
        val outcomes = run(
            actions = listOf(RuleAction.ForwardSms(listOf(mom.id, dad.id))),
            sender = sender,
            alreadySentTo = alreadySent
        )
        assertEquals(2, sender.sent.size)
        val outcome = outcomes.single()
        assertEquals(Status.SKIPPED, outcome.status)
        assertFalse(outcome.success)
        assertTrue(
            "expected 'already sent' in summary, got: ${outcome.summary}",
            outcome.summary.contains("already sent: Mom, Dad")
        )
        assertFalse(
            "dedup-skipped recipients must NOT be listed under 'Forwarded:', got: ${outcome.summary}",
            outcome.summary.startsWith("Forwarded:")
        )
    }

    @Test
    fun `forward with a mix of sent and skipped lists only sent under Forwarded`() {
        val sender = FakeSmsSender()
        val alreadySent = mutableSetOf(mom.id) // Mom was already reached by an earlier rule.
        val outcomes = run(
            actions = listOf(RuleAction.ForwardSms(listOf(mom.id, dad.id))),
            sender = sender,
            alreadySentTo = alreadySent
        )
        val outcome = outcomes.single()
        assertEquals(Status.SUCCESS, outcome.status)
        // Only Dad was sent; Mom appears in the '(already sent: …)' segment.
        assertTrue(outcome.summary.startsWith("Forwarded: Dad"))
        assertTrue(outcome.summary.contains("(already sent: Mom)"))
        assertFalse(
            "'Forwarded:' line must not include dedup-skipped Mom, got: ${outcome.summary}",
            outcome.summary.contains("Forwarded: Mom")
        )
    }

    @Test
    fun `forward failure is reported and does not block subsequent actions`() {
        val sender = FakeSmsSender(failFor = setOf(mom.phoneNumber))
        val outcomes = run(
            actions = listOf(
                RuleAction.ForwardSms(listOf(mom.id)),
                RuleAction.SetRingerLoud
            ),
            sender = sender
        )
        assertEquals(Status.FAILED, outcomes[0].status)
        assertFalse(outcomes[0].success)
        assertTrue(outcomes[0].summary.contains("failed", ignoreCase = true))
        assertTrue(outcomes[1].success)
    }

    @Test
    fun `PlaceCall with missing recipient fails gracefully`() {
        val missingId = 99L
        val outcomes = run(
            actions = listOf(RuleAction.PlaceCall(missingId)),
            call = FakePlaceCall()
        )
        assertEquals(1, outcomes.size)
        assertEquals(Status.FAILED, outcomes[0].status)
        assertTrue(outcomes[0].summary.contains("missing", ignoreCase = true))
    }

    @Test
    fun `empty actions list yields empty outcomes`() {
        val outcomes = run(actions = emptyList())
        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun `ringer succeeds when DND was not active even if bypass was not attempted`() {
        val loudNoDnd = FakeRingerLoud(
            SetRingerLoudResult(ringerChanged = true, bypassedDnd = false, dndWasActive = false)
        )
        val outcomes = run(actions = listOf(RuleAction.SetRingerLoud), ringer = loudNoDnd)
        assertEquals(Status.SUCCESS, outcomes[0].status)
        assertEquals("Rang loudly", outcomes[0].summary)
    }

    @Test
    fun `ringer fails when DND was active and bypass was denied`() {
        val loudButDnd = FakeRingerLoud(
            SetRingerLoudResult(ringerChanged = true, bypassedDnd = false, dndWasActive = true)
        )
        val outcomes = run(actions = listOf(RuleAction.SetRingerLoud), ringer = loudButDnd)
        assertEquals(Status.FAILED, outcomes[0].status)
        assertFalse(outcomes[0].success)
        assertTrue(
            "expected DND mention in summary, got: ${outcomes[0].summary}",
            outcomes[0].summary.contains("DND", ignoreCase = true)
        )
    }
}
