package com.otpforwarder.domain.detection

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RegexOtpDetectorTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-18T00:00:00Z"), ZoneOffset.UTC)
    private val detector = RegexOtpDetector(fixedClock)

    @Test
    fun `returns null when body has no OTP-related keyword`() {
        val result = detector.detect("HDFCBK", "Your purchase of Rs. 500 was successful.")
        assertNull(result)
    }

    @Test
    fun `returns null when keyword present but no numeric code`() {
        val result = detector.detect("HDFCBK", "Please enter your OTP to continue.")
        assertNull(result)
    }

    @Test
    fun `explicit OTP pattern yields 0_95 confidence`() {
        val result = detector.detect("HDFCBK", "Your OTP is 482910 for login.")
        assertNotNull(result)
        assertEquals("482910", result!!.code)
        assertEquals(0.95, result.confidence, 0.0)
        assertEquals(OtpType.UNKNOWN, result.type)
        assertEquals(ClassifierTier.NONE, result.classifierTier)
        assertEquals("HDFCBK", result.sender)
    }

    @Test
    fun `explicit OTP pattern with colon yields 0_95 confidence`() {
        val result = detector.detect("ICICIB", "OTP: 123456 do not share.")
        assertNotNull(result)
        assertEquals("123456", result!!.code)
        assertEquals(0.95, result.confidence, 0.0)
    }

    @Test
    fun `one-time password phrase yields 0_95 confidence`() {
        val result = detector.detect("MSFTNO", "Your one-time password is 48291 for Microsoft.")
        assertNotNull(result)
        assertEquals("48291", result!!.code)
        assertEquals(0.95, result.confidence, 0.0)
    }

    @Test
    fun `contextual code phrase yields 0_75 confidence`() {
        val result = detector.detect("AMZNIN", "Your code is 482910 to verify.")
        assertNotNull(result)
        assertEquals("482910", result!!.code)
        assertEquals(0.75, result.confidence, 0.0)
    }

    @Test
    fun `verification code phrase yields 0_75 confidence`() {
        val result = detector.detect("GLOGIN", "Your verification code: 4821")
        assertNotNull(result)
        assertEquals("4821", result!!.code)
        assertEquals(0.75, result.confidence, 0.0)
    }

    @Test
    fun `PIN phrase yields 0_75 confidence`() {
        val result = detector.detect("HDFCBK", "Your PIN is 4821 for txn.")
        assertNotNull(result)
        assertEquals("4821", result!!.code)
        assertEquals(0.75, result.confidence, 0.0)
    }

    @Test
    fun `bare code with keyword context yields 0_50 confidence`() {
        val result = detector.detect(
            "HDFCBK",
            "Thanks for using our OTP service. Please call 482910 if you did not request this."
        )
        assertNotNull(result)
        assertEquals("482910", result!!.code)
        assertEquals(0.50, result.confidence, 0.0)
    }

    @Test
    fun `supports 4-digit codes`() {
        val result = detector.detect("HDFCBK", "Your OTP is 1234.")
        assertNotNull(result)
        assertEquals("1234", result!!.code)
    }

    @Test
    fun `supports 8-digit codes`() {
        val result = detector.detect("HDFCBK", "Your OTP is 12345678.")
        assertNotNull(result)
        assertEquals("12345678", result!!.code)
    }

    @Test
    fun `picks first code when multiple are present`() {
        val result = detector.detect("HDFCBK", "Your OTP is 482910. Reference 999000.")
        assertNotNull(result)
        assertEquals("482910", result!!.code)
    }

    @Test
    fun `detectedAt uses provided clock`() {
        val result = detector.detect("HDFCBK", "Your OTP is 482910.")
        assertNotNull(result)
        assertEquals(Instant.parse("2026-04-18T00:00:00Z"), result!!.detectedAt)
    }

    @Test
    fun `preserves original message`() {
        val body = "Your OTP is 482910 for login."
        val result = detector.detect("HDFCBK", body)
        assertNotNull(result)
        assertEquals(body, result!!.originalMessage)
    }

    @Test
    fun `rejects numbers that are too short or too long`() {
        assertNull(detector.detect("X", "Your OTP is 12."))
        assertNull(detector.detect("X", "Your OTP is 1234567890."))
    }

    @Test
    fun `firstclub-style code-before-keyword extracts the code via bare-numeric tier`() {
        val body = "Hi 690750 is your login OTP for Firstclub.\n" +
            "<#> Please do not share this code with anyone\n" +
            "TefNEjPX2vy -FirstClub"
        val result = detector.detect("FirstClub", body)
        assertNotNull(result)
        assertEquals("690750", result!!.code)
    }
}
