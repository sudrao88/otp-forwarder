package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.domain.model.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins the use-case contract:
 *  - body without a recognised Maps URL → skipped (no notifier call);
 *  - body with a Maps URL → notifier is asked to post and the URL flows back
 *    on the result for the dispatcher / log line;
 *  - notifier failure (e.g. POST_NOTIFICATIONS missing) is reported faithfully;
 *  - the per-rule auto-launch flag is forwarded to the notifier so the FSI
 *    path can be wired without leaking platform plumbing into the use case.
 */
class OpenMapsActionUseCaseTest {

    private class FakeMapsNotifier(
        private val postSucceeds: Boolean = true
    ) : MapsNotifier {
        var calls: Int = 0
        var lastSender: String? = null
        var lastUrl: String? = null
        var lastAutoLaunch: Boolean? = null

        override fun notifyNavigation(sender: String, mapsUrl: String, autoLaunch: Boolean): Boolean {
            calls++
            lastSender = sender
            lastUrl = mapsUrl
            lastAutoLaunch = autoLaunch
            return postSucceeds
        }
    }

    private fun sms(body: String, sender: String = "+15551234567"): IncomingSms = IncomingSms(
        sender = sender,
        body = body,
        otp = null,
        receivedAt = Instant.parse("2026-05-01T12:00:00Z")
    )

    @Test
    fun `body without a Maps link — skipped, notifier not invoked`() {
        val notifier = FakeMapsNotifier()
        val result = OpenMapsActionUseCase(notifier)(
            sms("Just a plain message with no link"),
            autoLaunch = false
        )

        assertTrue("expected skipped result", result.skipped)
        assertFalse(result.success)
        assertNull(result.mapsUrl)
        assertEquals(0, notifier.calls)
    }

    @Test
    fun `body with a Maps link — notifier called and result reports success`() {
        val notifier = FakeMapsNotifier(postSucceeds = true)
        val url = "https://maps.google.com/?q=12.34,56.78"
        val result = OpenMapsActionUseCase(notifier)(
            sms("Heading here: $url see you soon", sender = "+19998887777"),
            autoLaunch = false
        )

        assertFalse(result.skipped)
        assertTrue(result.success)
        assertEquals(url, result.mapsUrl)
        assertEquals(1, notifier.calls)
        assertEquals("+19998887777", notifier.lastSender)
        assertEquals(url, notifier.lastUrl)
        assertEquals(false, notifier.lastAutoLaunch)
    }

    @Test
    fun `notifier failure is propagated as a non-success, non-skipped result`() {
        val notifier = FakeMapsNotifier(postSucceeds = false)
        val url = "https://maps.app.goo.gl/abcXYZ"
        val result = OpenMapsActionUseCase(notifier)(sms("trip: $url"), autoLaunch = false)

        assertFalse("link was found, must not be skipped", result.skipped)
        assertFalse("post failed, must not report success", result.success)
        assertEquals(url, result.mapsUrl)
        assertEquals(1, notifier.calls)
    }

    @Test
    fun `picks the first Maps URL when the body carries multiple`() {
        val notifier = FakeMapsNotifier(postSucceeds = true)
        val first = "https://goo.gl/maps/first"
        val second = "https://maps.google.com/second"
        val result = OpenMapsActionUseCase(notifier)(
            sms("Two pins: $first then $second"),
            autoLaunch = false
        )

        assertTrue(result.success)
        assertEquals(first, result.mapsUrl)
        assertEquals(first, notifier.lastUrl)
    }

    @Test
    fun `autoLaunch flag is forwarded to the notifier`() {
        val notifier = FakeMapsNotifier(postSucceeds = true)
        val url = "https://maps.app.goo.gl/driveNow"
        val result = OpenMapsActionUseCase(notifier)(sms("Heading: $url"), autoLaunch = true)

        assertTrue(result.success)
        assertEquals(true, notifier.lastAutoLaunch)
    }
}
