package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.IncomingSms
import com.otpforwarder.domain.model.Otp
import com.otpforwarder.domain.model.OtpType
import com.otpforwarder.domain.model.Recipient
import com.otpforwarder.domain.model.RuleAction
import com.otpforwarder.domain.sms.SmsSender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ForwardSmsActionUseCaseTest {

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

    private val sms = IncomingSms(
        sender = otp.sender,
        body = otp.originalMessage,
        otp = otp,
        receivedAt = otp.detectedAt
    )

    private class FakeSmsSender(val failFor: Set<String> = emptySet()) : SmsSender {
        val sent = mutableListOf<Pair<String, String>>()
        override fun send(phoneNumber: String, message: String): Boolean {
            sent += phoneNumber to message
            return phoneNumber !in failFor
        }
    }

    @Test
    fun `sends to every known recipient`() {
        val sender = FakeSmsSender()
        val useCase = ForwardSmsActionUseCase(sender)
        val result = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, dad.id)),
            recipientsById,
            mutableSetOf()
        )
        assertEquals(listOf(mom, dad), result.sent)
        assertTrue(result.skipped.isEmpty())
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf("+111" to otp.originalMessage, "+222" to otp.originalMessage), sender.sent)
    }

    @Test
    fun `dedupes duplicate recipient ids within a single action`() {
        val sender = FakeSmsSender()
        val useCase = ForwardSmsActionUseCase(sender)
        val result = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, mom.id, mom.id)),
            recipientsById,
            mutableSetOf()
        )
        assertEquals(listOf(mom), result.sent)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `dedupes recipients already reached by an earlier rule via alreadySentTo`() {
        val sender = FakeSmsSender()
        val useCase = ForwardSmsActionUseCase(sender)
        val shared = mutableSetOf<Long>()

        val first = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, dad.id)),
            recipientsById,
            shared
        )
        assertEquals(listOf(mom, dad), first.sent)
        assertEquals(setOf(mom.id, dad.id), shared)

        val second = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, dad.id)),
            recipientsById,
            shared
        )
        assertTrue(second.sent.isEmpty())
        assertEquals(listOf(mom, dad), second.skipped)
        assertEquals(2, sender.sent.size)
    }

    @Test
    fun `missing recipient ids are silently filtered`() {
        val sender = FakeSmsSender()
        val useCase = ForwardSmsActionUseCase(sender)
        val result = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, 999L, dad.id)),
            recipientsById,
            mutableSetOf()
        )
        assertEquals(listOf(mom, dad), result.sent)
        assertTrue(result.failed.isEmpty())
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `a send failure is reported in failed, not sent, and still marks recipient attempted`() {
        val sender = FakeSmsSender(failFor = setOf(mom.phoneNumber))
        val useCase = ForwardSmsActionUseCase(sender)
        val shared = mutableSetOf<Long>()
        val result = useCase(
            sms,
            RuleAction.ForwardSms(listOf(mom.id, dad.id)),
            recipientsById,
            shared
        )
        assertEquals(listOf(dad), result.sent)
        assertEquals(listOf(mom), result.failed)
        // Mom remains in alreadySentTo so a later rule doesn't re-attempt her.
        assertEquals(setOf(mom.id, dad.id), shared)
    }

    @Test
    fun `empty recipient list yields no sends, no skips, no failures`() {
        val sender = FakeSmsSender()
        val useCase = ForwardSmsActionUseCase(sender)
        val result = useCase(
            sms,
            RuleAction.ForwardSms(emptyList()),
            recipientsById,
            mutableSetOf()
        )
        assertTrue(result.sent.isEmpty())
        assertTrue(result.skipped.isEmpty())
        assertTrue(result.failed.isEmpty())
        assertTrue(sender.sent.isEmpty())
    }
}
