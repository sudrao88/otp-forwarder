package com.otpforwarder.domain.detection

/**
 * Finds the first Google Maps URL embedded in an SMS body.
 *
 * Recognises four host shapes shipped by Google in share / navigation flows:
 * `maps.google.com`, `(www.)?google.com/maps`, `goo.gl/maps`, `maps.app.goo.gl`.
 * Apple Maps, Waze, and generic `geo:` URIs are intentionally out of scope.
 */
object MapsLinkDetector {

    // The lookahead anchors the host so a URL like `https://maps.google.com.evil.com/x`
    // does NOT match `https://maps.google.com` — the host must end at `/`, `?`, `#`,
    // whitespace, or end of input.
    private val pattern = Regex(
        """\bhttps?://(?:maps\.google\.com|(?:www\.)?google\.com/maps|goo\.gl/maps|maps\.app\.goo\.gl)(?=[/?#]|\s|$)\S*""",
        RegexOption.IGNORE_CASE
    )

    fun findMapsLink(body: String): String? = pattern.find(body)?.value
}
