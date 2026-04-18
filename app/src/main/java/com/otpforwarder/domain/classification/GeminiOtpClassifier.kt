package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device classification backed by Google AI Edge SDK's `GenerativeModel` (Gemini Nano).
 *
 * The SDK surface is invoked via reflection so the app still builds and runs on
 * devices or CI environments where the artifact is not present. When the SDK or
 * backing AICore service is unavailable, [isAvailable] returns `false` and
 * [classify] surfaces `UNKNOWN` (the [TieredOtpClassifier] falls back to the
 * keyword classifier on that signal).
 */
@Singleton
class GeminiOtpClassifier(
    private val runtime: GeminiRuntime
) : OtpClassifier {

    @Inject
    constructor() : this(ReflectiveGeminiRuntime())

    suspend fun isAvailable(): Boolean = runtime.isAvailable()

    override suspend fun classify(
        sender: String,
        body: String
    ): Pair<OtpType, ClassifierTier> {
        if (!isAvailable()) return OtpType.UNKNOWN to ClassifierTier.GEMINI_NANO

        val prompt = buildPrompt(body)
        val raw = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { runtime.generate(prompt) }.getOrNull()
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
 * and so the classifier still compiles if the SDK artifact is not on the classpath.
 */
interface GeminiRuntime {
    suspend fun isAvailable(): Boolean
    suspend fun generate(prompt: String): String
}

/**
 * Calls the Google AI Edge SDK via reflection. If the SDK classes are not present
 * (or the on-device model is not available), [isAvailable] returns `false` and
 * [generate] throws — callers must guard with [isAvailable] first.
 */
class ReflectiveGeminiRuntime : GeminiRuntime {
    override suspend fun isAvailable(): Boolean = runCatching {
        val clazz = Class.forName("com.google.ai.edge.generativeai.GenerativeModel")
        val method = clazz.getMethod("isAvailable")
        method.invoke(null) as? Boolean ?: false
    }.getOrDefault(false)

    override suspend fun generate(prompt: String): String {
        val clazz = Class.forName("com.google.ai.edge.generativeai.GenerativeModel")
        val instance = clazz.getConstructor(String::class.java).newInstance(DEFAULT_MODEL)
        val generate = clazz.getMethod("generateContent", String::class.java)
        return generate.invoke(instance, prompt)?.toString().orEmpty()
    }

    private companion object {
        const val DEFAULT_MODEL = "gemini-nano"
    }
}
