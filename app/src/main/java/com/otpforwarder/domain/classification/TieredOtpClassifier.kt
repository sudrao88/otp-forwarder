package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator that tries [GeminiOtpClassifier] first and silently falls back to
 * [KeywordOtpClassifier] on unavailability, timeout, or an unparsable response.
 *
 * The returned [ClassifierTier] identifies which tier produced the result, and is
 * persisted in [com.otpforwarder.data.local.OtpLogEntity] for observability.
 */
@Singleton
class TieredOtpClassifier @Inject constructor(
    private val gemini: GeminiOtpClassifier,
    private val keyword: KeywordOtpClassifier
) : OtpClassifier {

    override suspend fun classify(
        sender: String,
        body: String
    ): Pair<OtpType, ClassifierTier> {
        if (gemini.isAvailable()) {
            val (type, tier) = runCatching { gemini.classify(sender, body) }
                .getOrDefault(OtpType.UNKNOWN to ClassifierTier.GEMINI_NANO)
            if (type != OtpType.UNKNOWN) return type to tier
        }
        return keyword.classify(sender, body)
    }
}
