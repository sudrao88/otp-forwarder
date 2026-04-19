package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator that tries [GeminiOtpClassifier] first and falls back to
 * [KeywordOtpClassifier] on unavailability or an `UNKNOWN` result.
 *
 * [GeminiOtpClassifier.classify] already converts any non-cancellation
 * throwable into `UNKNOWN to GEMINI_NANO`, so no outer `runCatching` is
 * needed here — and wrapping in one would mask `CancellationException`.
 *
 * The returned [ClassifierTier] identifies which tier produced the result and
 * is persisted in [com.otpforwarder.data.local.OtpLogEntity] for observability.
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
            val (type, tier) = gemini.classify(sender, body)
            if (type != OtpType.UNKNOWN) return type to tier
        }
        return keyword.classify(sender, body)
    }
}
