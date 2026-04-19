package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device classification backed by Google AI Edge SDK's `GenerativeModel` (Gemini Nano).
 *
 * The Gemini SDK is **not currently on the project classpath**, so the default
 * [GeminiRuntime] binding ([DisabledGeminiRuntime]) reports unavailable and the
 * [TieredOtpClassifier] always falls back to the keyword classifier in shipped
 * builds. To enable the Gemini tier, add the `com.google.ai.edge:generative-ai`
 * artifact to `app/build.gradle.kts`, provide a real runtime that wraps
 * `GenerativeModel`, and rebind [GeminiRuntime] in `DomainModule`.
 *
 * Any unchecked throwable from the runtime is converted to `UNKNOWN` so the
 * tiered orchestrator falls back transparently. `CancellationException` is
 * explicitly re-thrown to preserve structured concurrency.
 */
@Singleton
class GeminiOtpClassifier @Inject constructor(
    private val runtime: GeminiRuntime
) : OtpClassifier {

    suspend fun isAvailable(): Boolean = runtime.isAvailable()

    override suspend fun classify(
        sender: String,
        body: String
    ): Pair<OtpType, ClassifierTier> {
        if (!isAvailable()) return OtpType.UNKNOWN to ClassifierTier.GEMINI_NANO

        val prompt = buildPrompt(body)
        val raw = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                runtime.generate(prompt)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                null
            }
        } ?: return OtpType.UNKNOWN to ClassifierTier.GEMINI_NANO

        return parseType(raw) to ClassifierTier.GEMINI_NANO
    }

    private fun buildPrompt(body: String): String = """
        Classify this SMS into exactly one category:
        TRANSACTION, LOGIN, PARCEL_DELIVERY, REGISTRATION,
        PASSWORD_RESET, GOVERNMENT, or UNKNOWN.
        Reply with only the category name.

        SMS: "$body"
    """.trimIndent()

    private fun parseType(raw: String): OtpType {
        val token = raw.trim()
            .uppercase()
            .replace("\"", "")
            .replace(".", "")
            .split(Regex("\\s+"))
            .firstOrNull()
            ?: return OtpType.UNKNOWN
        return runCatching { OtpType.valueOf(token) }
            .getOrDefault(OtpType.UNKNOWN)
            .let { if (it == OtpType.ALL) OtpType.UNKNOWN else it }
    }

    private companion object {
        const val TIMEOUT_MS = 1_500L
    }
}

/**
 * Indirection over the Google AI Edge `GenerativeModel` so it can be stubbed in tests
 * and so the classifier still compiles / runs without the SDK on the classpath.
 */
interface GeminiRuntime {
    suspend fun isAvailable(): Boolean
    suspend fun generate(prompt: String): String
}

/**
 * Default binding used until the Gemini SDK is added to the project classpath.
 * Reports unavailable; [generate] is never called because [isAvailable] gates it.
 */
@Singleton
class DisabledGeminiRuntime @Inject constructor() : GeminiRuntime {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun generate(prompt: String): String =
        throw UnsupportedOperationException("Gemini runtime is disabled")
}
