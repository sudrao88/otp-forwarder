package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType

/**
 * Classifies an SMS into an [OtpType] and reports the [ClassifierTier] that produced it.
 *
 * Implementations may perform blocking work (network, on-device inference), so this is suspending.
 */
interface OtpClassifier {
    suspend fun classify(sender: String, body: String): Pair<OtpType, ClassifierTier>
}
