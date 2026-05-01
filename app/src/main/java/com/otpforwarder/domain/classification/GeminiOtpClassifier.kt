package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device classification backed by Android AICore (ML Kit GenAI Prompt API).
 *
 * The runtime is supplied via [GeminiRuntime]; in production this is
 * [com.otpforwarder.data.gemini.AiCoreGeminiRuntime] (wraps `Generation.getClient()`).
 * Tests can swap in a fake runtime — including [DisabledGeminiRuntime], which always
 * reports unsupported and is the safe fallback when AICore isn't on the device.
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
 * On-device Gemini Nano availability, mirroring AICore `FeatureStatus`.
 */
enum class GeminiAvailability {
    Unknown,
    Unsupported,
    Downloadable,
    Downloading,
    Ready
}

/**
 * Indirection over the AICore `GenerativeModel` so it can be stubbed in tests
 * and so the classifier compiles without a hard dependency on the SDK.
 */
interface GeminiRuntime {
    val downloadProgress: StateFlow<Float?>
    suspend fun status(): GeminiAvailability
    suspend fun isAvailable(): Boolean = status() == GeminiAvailability.Ready
    suspend fun generate(prompt: String): String
    suspend fun startDownload()
}

/**
 * Test/fallback runtime that reports the device as unsupported.
 * [generate] is never called because [isAvailable] gates it.
 */
@Singleton
class DisabledGeminiRuntime @Inject constructor() : GeminiRuntime {
    private val _progress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _progress.asStateFlow()
    override suspend fun status(): GeminiAvailability = GeminiAvailability.Unsupported
    override suspend fun generate(prompt: String): String =
        throw UnsupportedOperationException("Gemini runtime is disabled")
    override suspend fun startDownload() {
        // no-op — nothing to download on an unsupported device
    }
}
