package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.engine.RuleEngine
import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpLogEntry
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.OtpLogRepository
import com.otpforwarder.domain.repository.RecipientRepository
import com.otpforwarder.domain.usecase.actions.ExecuteRuleActionsUseCase
import com.otpforwarder.domain.usecase.actions.ForwardSmsActionUseCase
import com.otpforwarder.domain.usecase.actions.OpenMapsAction
import com.otpforwarder.domain.usecase.actions.OpenMapsResult
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.usecase.actions.PlaceCallAction
import com.otpforwarder.domain.usecase.actions.PlaceCallResult
import com.otpforwarder.domain.usecase.actions.SetRingerLoudAction
import com.otpforwarder.domain.usecase.actions.SetRingerLoudResult
import com.otpforwarder.domain.sms.SmsSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Phase 1 invariant: rules are evaluated against every SMS, OTP or not. The
 * legacy pipeline short-circuited on `OtpDetector.detect` returning null, so a
 * `SenderMatches` + `SetRingerLoud` rule never fired on a plain text. This test
 * locks that fix in.
 */
class ProcessIncomingSmsUseCaseTest {

    private val now = Instant.parse("2026-04-25T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class FakeOtpDetector(private val detected: Otp? = null) : OtpDetector {
        override fun detect(sender: String, body: String): Otp? = detected
    }

    private class FakeOtpClassifier(
        private val type: OtpType = OtpType.UNKNOWN,
        private val tier: ClassifierTier = ClassifierTier.KEYWORD
    ) : OtpClassifier {
        var calls: Int = 0
        override suspend fun classify(sender: String, body: String): Pair<OtpType, ClassifierTier> {
            calls++
            return type to tier
        }
    }

    private class FakeRuleRepo(private val rules: List<ForwardingRule>) : ForwardingRuleRepository {
        override fun getAllRulesWithDetails(): Flow<List<ForwardingRule>> = flowOf(rules)
        override suspend fun getRuleWithDetailsById(id: Long): ForwardingRule? =
            rules.firstOrNull { it.id == id }
        override suspend fun getEnabledRulesWithDetails(): List<ForwardingRule> =
            rules.filter { it.isEnabled }
        override suspend fun insertRule(rule: ForwardingRule): Long = 0
        override suspend fun updateRule(rule: ForwardingRule) = Unit
        override suspend fun deleteRule(rule: ForwardingRule) = Unit
        override suspend fun getRuleIdsForRecipient(recipientId: Long): List<Long> = emptyList()
        override suspend fun setRuleAssignmentsForRecipient(recipientId: Long, ruleIds: Set<Long>) = Unit
    }

    private class FakeRecipientRepo(private val active: List<Recipient> = emptyList()) : RecipientRepository {
        override fun getAllRecipients(): Flow<List<Recipient>> = flowOf(active)
        override suspend fun getRecipientById(id: Long): Recipient? = active.firstOrNull { it.id == id }
        override suspend fun getActiveRecipients(): List<Recipient> = active
        override suspend fun getRecipientsForRule(ruleId: Long): List<Recipient> = emptyList()
        override suspend fun insertRecipient(recipient: Recipient): Long = 0
        override suspend fun updateRecipient(recipient: Recipient) = Unit
        override suspend fun deleteRecipient(recipient: Recipient) = Unit
    }

    private class CapturingLogRepo : OtpLogRepository {
        val inserted = mutableListOf<OtpLogEntry>()
        override fun getRecentLogs(sinceTimestamp: Long): Flow<List<OtpLogEntry>> = flowOf(emptyList())
        override suspend fun insertLog(entry: OtpLogEntry): Long {
            inserted += entry
            return inserted.size.toLong()
        }
        override suspend fun updateLog(entry: OtpLogEntry) = Unit
        override suspend fun pruneOldLogs(cutoffTimestamp: Long) = Unit
        override suspend fun getLogById(id: Long): OtpLogEntry? = null
    }

    private class CountingRinger(private val result: SetRingerLoudResult) : SetRingerLoudAction {
        var calls: Int = 0
        override fun invoke(): SetRingerLoudResult {
            calls++
            return result
        }
    }

    private object UnusedSmsSender : SmsSender {
        override fun send(phoneNumber: String, message: String): Boolean =
            error("ForwardSms must not run for ringer-only rule")
    }

    private object UnusedPlaceCall : PlaceCallAction {
        override fun invoke(recipient: Recipient): PlaceCallResult =
            error("PlaceCall must not run for ringer-only rule")
    }

    private object UnusedOpenMaps : OpenMapsAction {
        override fun invoke(sms: IncomingSms): OpenMapsResult =
            error("OpenMaps must not run for non-maps rule")
    }

    @Test
    fun `non-OTP SMS that matches a SenderMatches rule still fires SetRingerLoud`() {
        val ringer = CountingRinger(SetRingerLoudResult(ringerChanged = true, bypassedDnd = true))
        val ringerRule = ForwardingRule(
            id = 1,
            name = "Mom alerts",
            isEnabled = true,
            priority = 1,
            conditions = listOf(RuleCondition.SenderMatches(pattern = "MOM", connector = Connector.AND)),
            actions = listOf(RuleAction.SetRingerLoud)
        )
        val ruleRepo = FakeRuleRepo(listOf(ringerRule))
        val logRepo = CapturingLogRepo()
        val classifier = FakeOtpClassifier()

        val useCase = ProcessIncomingSmsUseCase(
            detector = FakeOtpDetector(detected = null),
            classifier = classifier,
            ruleEngine = RuleEngine(ruleRepo),
            executeRuleActions = ExecuteRuleActionsUseCase(
                forwardSms = ForwardSmsActionUseCase(UnusedSmsSender),
                setRingerLoud = ringer,
                placeCall = UnusedPlaceCall,
                openMaps = UnusedOpenMaps
            ),
            recipientRepository = FakeRecipientRepo(),
            otpLogRepository = logRepo,
            clock = clock
        )

        val result = runBlocking { useCase("MOM", "Where are you?") }

        assertTrue("expected Forwarded result, got $result", result is ProcessIncomingSmsUseCase.Result.Forwarded)
        assertEquals("ringer must fire on a non-OTP SMS", 1, ringer.calls)
        assertEquals(0, classifier.calls)
        assertEquals(1, logRepo.inserted.size)
        val logged = logRepo.inserted.single()
        assertNull("non-OTP log entry should have null code", logged.code)
        assertNull("non-OTP log entry should have null otpType", logged.otpType)
        assertNull("non-OTP log entry should have null confidence", logged.confidence)
        assertNull("non-OTP log entry should have null classifierTier", logged.classifierTier)
        assertEquals("MOM", logged.sender)
        assertEquals("Where are you?", logged.originalMessage)
        assertEquals(ProcessIncomingSmsUseCase.STATUS_SENT, logged.status)
        assertEquals(listOf("Rang loudly"), logged.summaryLines)
    }

    @Test
    fun `OTP SMS still classifies and logs the OTP fields`() {
        val detected = Otp(
            code = "482910",
            type = OtpType.UNKNOWN,
            sender = "HDFCBK",
            originalMessage = "Your OTP is 482910",
            detectedAt = now,
            confidence = 0.95,
            classifierTier = ClassifierTier.NONE
        )
        val ringer = CountingRinger(SetRingerLoudResult(ringerChanged = true, bypassedDnd = true))
        val ringerRule = ForwardingRule(
            id = 1,
            name = "All OTPs ring",
            isEnabled = true,
            priority = 1,
            conditions = listOf(RuleCondition.OtpTypeIs(OtpType.ALL, Connector.AND)),
            actions = listOf(RuleAction.SetRingerLoud)
        )
        val classifier = FakeOtpClassifier(type = OtpType.TRANSACTION, tier = ClassifierTier.KEYWORD)
        val logRepo = CapturingLogRepo()

        val useCase = ProcessIncomingSmsUseCase(
            detector = FakeOtpDetector(detected = detected),
            classifier = classifier,
            ruleEngine = RuleEngine(FakeRuleRepo(listOf(ringerRule))),
            executeRuleActions = ExecuteRuleActionsUseCase(
                forwardSms = ForwardSmsActionUseCase(UnusedSmsSender),
                setRingerLoud = ringer,
                placeCall = UnusedPlaceCall,
                openMaps = UnusedOpenMaps
            ),
            recipientRepository = FakeRecipientRepo(),
            otpLogRepository = logRepo,
            clock = clock
        )

        val result = runBlocking { useCase("HDFCBK", "Your OTP is 482910") }
        assertTrue(result is ProcessIncomingSmsUseCase.Result.Forwarded)
        assertEquals(1, classifier.calls)
        val logged = logRepo.inserted.single()
        assertEquals("482910", logged.code)
        assertEquals(OtpType.TRANSACTION, logged.otpType)
        assertEquals(ClassifierTier.KEYWORD, logged.classifierTier)
        assertEquals(0.95, logged.confidence!!, 0.0001)
    }

    @Test
    fun `non-OTP SMS with no matching rule returns NoMatchingRule and does not log`() {
        val classifier = FakeOtpClassifier()
        val logRepo = CapturingLogRepo()
        val useCase = ProcessIncomingSmsUseCase(
            detector = FakeOtpDetector(detected = null),
            classifier = classifier,
            ruleEngine = RuleEngine(FakeRuleRepo(emptyList())),
            executeRuleActions = ExecuteRuleActionsUseCase(
                forwardSms = ForwardSmsActionUseCase(UnusedSmsSender),
                setRingerLoud = CountingRinger(SetRingerLoudResult(true, true)),
                placeCall = UnusedPlaceCall,
                openMaps = UnusedOpenMaps
            ),
            recipientRepository = FakeRecipientRepo(),
            otpLogRepository = logRepo,
            clock = clock
        )

        val result = runBlocking { useCase("RANDOM", "hello world") }
        assertTrue(result is ProcessIncomingSmsUseCase.Result.NoMatchingRule)
        assertEquals(0, classifier.calls)
        assertTrue(logRepo.inserted.isEmpty())
    }
}
