package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.Recipient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCallActionUseCaseTest {

    private val mom = Recipient(id = 1, name = "Mom", phoneNumber = "+111")

    private class FakeTelecomSystem(
        var hasPermission: Boolean = true,
        var placeCallThrows: Throwable? = null
    ) : TelecomSystem {
        val calls = mutableListOf<String>()
        override fun hasCallPhonePermission(): Boolean = hasPermission
        override fun placeCall(phoneNumber: String) {
            calls += phoneNumber
            placeCallThrows?.let { throw it }
        }
    }

    @Test
    fun `returns failure with CALL_PHONE reason when permission is denied`() {
        val telecom = FakeTelecomSystem(hasPermission = false)
        val result = PlaceCallActionUseCase(telecom)(mom)
        assertFalse(result.success)
        assertEquals("CALL_PHONE not granted", result.reason)
        assertTrue("placeCall must not be attempted without permission", telecom.calls.isEmpty())
    }

    @Test
    fun `returns success when permission granted and placeCall returns normally`() {
        val telecom = FakeTelecomSystem(hasPermission = true)
        val result = PlaceCallActionUseCase(telecom)(mom)
        assertTrue(result.success)
        assertEquals(listOf(mom.phoneNumber), telecom.calls)
    }

    @Test
    fun `returns failure with thrown message when placeCall throws`() {
        val telecom = FakeTelecomSystem(
            hasPermission = true,
            placeCallThrows = SecurityException("no call permission at OS level")
        )
        val result = PlaceCallActionUseCase(telecom)(mom)
        assertFalse(result.success)
        assertEquals("no call permission at OS level", result.reason)
        assertEquals(listOf(mom.phoneNumber), telecom.calls)
    }

    @Test
    fun `returns failure with fallback reason when thrown message is null`() {
        val telecom = FakeTelecomSystem(
            hasPermission = true,
            placeCallThrows = RuntimeException()
        )
        val result = PlaceCallActionUseCase(telecom)(mom)
        assertFalse(result.success)
        assertEquals("placeCall failed", result.reason)
    }
}
