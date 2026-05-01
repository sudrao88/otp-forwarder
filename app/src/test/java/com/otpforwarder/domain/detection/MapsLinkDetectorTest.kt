package com.otpforwarder.domain.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapsLinkDetectorTest {

    @Test
    fun `detects maps_google_com URL`() {
        val link = MapsLinkDetector.findMapsLink(
            "Find me here: https://maps.google.com/maps?q=48.8584,2.2945"
        )
        assertEquals("https://maps.google.com/maps?q=48.8584,2.2945", link)
    }

    @Test
    fun `detects www_google_com slash maps URL`() {
        val link = MapsLinkDetector.findMapsLink(
            "Directions: https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z"
        )
        assertEquals(
            "https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z",
            link
        )
    }

    @Test
    fun `detects google_com slash maps URL without www`() {
        val link = MapsLinkDetector.findMapsLink(
            "https://google.com/maps/place/Foo"
        )
        assertEquals("https://google.com/maps/place/Foo", link)
    }

    @Test
    fun `detects goo_gl slash maps short link`() {
        val link = MapsLinkDetector.findMapsLink(
            "Tap here https://goo.gl/maps/AbCdEf123 to navigate"
        )
        assertEquals("https://goo.gl/maps/AbCdEf123", link)
    }

    @Test
    fun `detects maps_app_goo_gl share link`() {
        val link = MapsLinkDetector.findMapsLink(
            "Cab incoming. https://maps.app.goo.gl/xY9zPq2"
        )
        assertEquals("https://maps.app.goo.gl/xY9zPq2", link)
    }

    @Test
    fun `match is case insensitive`() {
        val link = MapsLinkDetector.findMapsLink(
            "HTTPS://Maps.App.Goo.Gl/AbCdEf"
        )
        assertEquals("HTTPS://Maps.App.Goo.Gl/AbCdEf", link)
    }

    @Test
    fun `accepts http scheme`() {
        val link = MapsLinkDetector.findMapsLink(
            "Old link: http://maps.google.com/?q=foo"
        )
        assertEquals("http://maps.google.com/?q=foo", link)
    }

    @Test
    fun `returns null for plain text without URL`() {
        assertNull(MapsLinkDetector.findMapsLink("OTP 123456 to login. Do not share."))
    }

    @Test
    fun `returns null for Apple Maps URL`() {
        assertNull(
            MapsLinkDetector.findMapsLink(
                "Find me at https://maps.apple.com/?ll=48.85,2.29"
            )
        )
    }

    @Test
    fun `returns null for non-maps Google URL`() {
        assertNull(
            MapsLinkDetector.findMapsLink("Search at https://www.google.com/search?q=foo")
        )
    }

    @Test
    fun `returns null for goo_gl short link not under maps path`() {
        assertNull(MapsLinkDetector.findMapsLink("https://goo.gl/abc123"))
    }

    @Test
    fun `returns null for lookalike host with maps prefix`() {
        // host is `maps.google.com.evil.example`, not `maps.google.com`
        assertNull(
            MapsLinkDetector.findMapsLink(
                "Phishy: https://maps.google.com.evil.example/foo"
            )
        )
    }

    @Test
    fun `returns null for lookalike host with maps_app_goo_gl prefix`() {
        assertNull(
            MapsLinkDetector.findMapsLink(
                "https://maps.app.goo.gl.evil.example/abc"
            )
        )
    }

    @Test
    fun `returns first match when multiple maps URLs are present`() {
        val link = MapsLinkDetector.findMapsLink(
            "Pickup https://maps.app.goo.gl/AAA then drop at https://maps.app.goo.gl/BBB"
        )
        assertEquals("https://maps.app.goo.gl/AAA", link)
    }

    @Test
    fun `match terminates at whitespace, leaves trailing text out`() {
        val link = MapsLinkDetector.findMapsLink(
            "Open https://maps.app.goo.gl/AbCdEf and tap Start"
        )
        assertEquals("https://maps.app.goo.gl/AbCdEf", link)
    }

    @Test
    fun `match works when URL is at the very start of body`() {
        val link = MapsLinkDetector.findMapsLink("https://maps.app.goo.gl/Zz")
        assertEquals("https://maps.app.goo.gl/Zz", link)
    }

    @Test
    fun `match works when URL is at the very end of body`() {
        val link = MapsLinkDetector.findMapsLink("Navigate now: https://maps.app.goo.gl/Zz")
        assertEquals("https://maps.app.goo.gl/Zz", link)
    }

    @Test
    fun `returns null for empty body`() {
        assertNull(MapsLinkDetector.findMapsLink(""))
    }
}
