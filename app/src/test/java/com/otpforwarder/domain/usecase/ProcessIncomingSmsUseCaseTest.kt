package com.otpforwarder.domain.usecase

import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.engine.RuleEngine
import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.Connector
import com.otpforwarder.domain.model.ForwardingRule
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.ReceivedSms
import com.otpforwarder.domain.model.ReceivedSmsStatus
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.model.RuleCondition
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import com.otpforwarder.domain.repository.RecipientRepository
import com.otpforwarder.domain.usecase.actions.ExecuteRuleActionsUseCase
import com.otpforwarder.domain.usecase.actions.ForwardSmsActionUseCase
import com.otpforwarder.domain.usecase.actions.OpenMapsAction
import com.otpforwarder.domain.usecase.actions.OpenMapsResult
import com.otpforwarder.domain.usecase.actions.PlaceCallAction
import com.otpforwarder.domain.usecase.actions.PlaceCallResult
import com.otpforwarder.domain.usecase.actions.SetRingerLoudAction
import com.otpforwarder.domain.usecase.actions.SetRingerLoudResult
import com.otpforwarder.domain.sms.SmsSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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

    private class CapturingFeedRepo : ReceivedSmsRepository {
        val inserted = mutableListOf<ReceivedSms>()
        val updated = mutableListOf<ReceivedSms>()
        override fun getRecent(sinceTimestamp: Long): Flow<List<ReceivedSms>> = flowOf(emptyList())
        override suspend fun insert(entry: ReceivedSms): Long {
            inserted += entry
            return inserted.size.toLong()
        }
        override suspend fun update(entry: ReceivedSms) {
            updated += entry
        }
        override suspend fun getById(id: Long): ReceivedSms? = null
        override suspend fun pruneOlderThan(cutoffTimestamp: Long) = Unit
        override suspend fun deleteAll() = Unit
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
        override fun invoke(sms: IncomingSms, autoLaunch: Boolean): OpenMapsResult =
            error("OpenMaps must not run for non-maps rule")
    }

    private fun makeUseCase(
        detector: OtpDetector,
        classifier: OtpClassifier,
        rules: List<ForwardingRule>,
        feed: CapturingFeedRepo,
        recipients: FakeRecipientRepo = FakeRecipientRepo(),
        ringer: SetRingerLoudAction = CountingRinger(SetRingerLoudResult(true, true))
    ) = ProcessIncomingSmsUseCase(
        detector = detector,
        classifier = classifier,
        ruleEngine = RuleEngine(FakeRuleRepo(rules)),
        executeRuleActions = ExecuteRuleActionsUseCase(
            forwardSms = ForwardSmsActionUseCase(UnusedSmsSender),
            setRingerLoud = ringer,
            placeCall = UnusedPlaceCall,
            openMaps = UnusedOpenMaps
        ),
        recipientRepository = recipients,
        receivedSmsRepository = feed,
        clock = clock
    )

    @Test
    fun `non-OTP SMS that matches a SenderMatches rule still fires SetRingerLoud and is logged as FORWARDED`() {
        val ringer = CountingRinger(SetRingerLoudResult(ringerChanged = true, bypassedDnd = true))
        val ringerRule = ForwardingRule(
            id = 1,
            name = "Mom alerts",
            isEnabled = true,
            priority = 1,
            conditions = listOf(RuleCondition.SenderMatches(pattern = "MOM", connector = Connector.AND)),
            actions = listOf(RuleAction.SetRingerLoud)
        )
        val feed = CapturingFeedRepo()
        val classifier = FakeOtpClassifier()

        val useCase = makeUseCase(
            detector = FakeOtpDetector(detected = null),
            classifier = classifier,
            rules = listOf(ringerRule),
            feed = feed,
            ringer = ringer
        )

        val result = runBlocking { useCase("MOM", "Where are you?") }

        assertTrue("expected Forwarded, got $result", result is ProcessIncomingSmsUseCase.Result.Forwarded)
        assertEquals(1, ringer.calls)
        assertEquals(0, classifier.calls)
        assertEquals(1, feed.inserted.size)
        assertEquals(ReceivedSmsStatus.PENDING, feed.inserted.single().processingStatus)
        val final = feed.updated.single()
        assertEquals(ReceivedSmsStatus.FORWARDED, final.processingStatus)
        assertEquals(listOf("Mom alerts"), final.matchedRuleNames)
        assertNull("non-OTP feed row should have null code", final.otpCode)
        assertNull(final.otpType)
        assertNull(final.confidence)
        assertNull(final.classifierTier)
        assertEquals("MOM", final.sender)
        assertEquals("Where are you?", final.body)
        assertEquals(now, final.processedAt)
    }

    @Test
    fun `OTP SMS classifies and records OTP fields on the feed row`() {
        val detected = Otp(
            code = "482910",
            type = OtpType.UNKNOWN,
            sender = "HDFCBK",
            originalMessage = "Your OTP is 482910",
            detectedAt = now,
            confidence = 0.95,
            classifierTier = ClassifierTier.NONE
        )
        val ringerRule = ForwardingRule(
            id = 1,
            name = "All OTPs ring",
            isEnabled = true,
            priority = 1,
            conditions = listOf(RuleCondition.OtpTypeIs(OtpType.ALL, Connector.AND)),
            actions = listOf(RuleAction.SetRingerLoud)
        )
        val classifier = FakeOtpClassifier(type = OtpType.TRANSACTION, tier = ClassifierTier.KEYWORD)
        val feed = CapturingFeedRepo()

        val useCase = makeUseCase(
            detector = FakeOtpDetector(detected = detected),
            classifier = classifier,
            rules = listOf(ringerRule),
            feed = feed
        )

        val result = runBlocking { useCase("HDFCBK", "Your OTP is 482910") }
        assertTrue(result is ProcessIncomingSmsUseCase.Result.Forwarded)
        assertEquals(1, classifier.calls)
        val final = feed.updated.single()
        assertEquals("482910", final.otpCode)
        assertEquals(OtpType.TRANSACTION, final.otpType)
        assertEquals(ClassifierTier.KEYWORD, final.classifierTier)
        assertEquals(0.95, final.confidence!!, 0.0001)
        assertEquals(ReceivedSmsStatus.FORWARDED, final.processingStatus)
    }

    @Test
    fun `SMS with no matching rule still produces a NO_MATCH feed row`() {
        val classifier = FakeOtpClassifier()
        val feed = CapturingFeedRepo()
        val useCase = makeUseCase(
            detector = FakeOtpDetector(detected = null),
            classifier = classifier,
            rules = emptyList(),
            feed = feed
        )

        val result = runBlocking { useCase("RANDOM", "hello world") }
        assertTrue(result is ProcessIncomingSmsUseCase.Result.NoMatchingRule)
        assertEquals(0, classifier.calls)
        assertEquals(1, feed.inserted.size)
        val final = feed.updated.single()
        assertEquals(ReceivedSmsStatus.NO_MATCH, final.processingStatus)
        assertEquals("RANDOM", final.sender)
        assertEquals("hello world", final.body)
        assertEquals(emptyList<String>(), final.matchedRuleNames)
        assertNotNull(final.processedAt)
    }

    @Test
    fun `OTP detected but no matching rule records detected code on NO_MATCH row`() {
        val detected = Otp(
            code = "690750",
            type = OtpType.UNKNOWN,
            sender = "FirstClub",
            originalMessage = "Hi 690750 is your login OTP for Firstclub.",
            detectedAt = now,
            confidence = 0.50,
            classifierTier = ClassifierTier.NONE
        )
        val classifier = FakeOtpClassifier(type = OtpType.LOGIN, tier = ClassifierTier.KEYWORD)
        val feed = CapturingFeedRepo()

        val useCase = makeUseCase(
            detector = FakeOtpDetector(detected = detected),
            classifier = classifier,
            rules = emptyList(),
            feed = feed
        )

        val result = runBlocking { useCase("FirstClub", "Hi 690750 is your login OTP for Firstclub.") }
        assertTrue(result is ProcessIncomingSmsUseCase.Result.NoMatchingRule)
        val final = feed.updated.single()
        assertEquals(ReceivedSmsStatus.NO_MATCH, final.processingStatus)
        assertEquals("690750", final.otpCode)
        assertEquals(OtpType.LOGIN, final.otpType)
    }
}
