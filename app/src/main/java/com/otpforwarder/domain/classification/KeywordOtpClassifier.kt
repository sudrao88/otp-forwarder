package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Weighted keyword classifier.
 *
 * For each [OtpType] the message body is scanned for a set of weighted keywords.
 * The per-category score is the sum of matched weights, plus a sender-reputation
 * boost (+0.5) if the sender matches a known prefix.
 *
 * The category with the highest score wins, provided that score exceeds
 * [SCORE_THRESHOLD]. On a tie, the *more specific* category wins — defined here
 * as the category with fewer total registered keywords (a smaller vocabulary
 * implies a narrower, more specific topic).
 *
 * Keyword matches are word-boundary anchored so `tax` doesn't match `syntax`,
 * `credit` doesn't double-count inside `credited`, and `pan` doesn't match
 * `Japan`. Uses `(?<!\w)…(?!\w)` rather than `\b…\b` so keywords whose
 * leading/trailing character is non-word (`a/c`, `Rs.`) still match.
 */
@Singleton
class KeywordOtpClassifier @Inject constructor() : OtpClassifier {

    override suspend fun classify(
        sender: String,
        body: String
    ): Pair<OtpType, ClassifierTier> {
        val normalizedSender = sender.uppercase()

        val scores = mutableMapOf<OtpType, Double>()
        for ((type, keywords) in CATEGORY_REGEXES) {
            var score = 0.0
            for ((regex, weight) in keywords) {
                if (regex.containsMatchIn(body)) {
                    score += weight
                }
            }
            if (score > 0.0) scores[type] = score
        }

        for ((prefix, type) in SENDER_BOOST) {
            if (normalizedSender.contains(prefix)) {
                scores[type] = (scores[type] ?: 0.0) + SENDER_BOOST_WEIGHT
            }
        }

        // Round to avoid IEEE-754 noise breaking true ties (e.g. 0.7+0.4+0.3
        // summing to 1.4000000000000001 instead of 1.4 vs 0.8+0.6).
        for (type in scores.keys.toList()) {
            scores[type] = roundTo2dp(scores[type]!!)
        }

        val winner = scores
            .filterValues { it >= SCORE_THRESHOLD }
            .entries
            .maxWithOrNull(
                compareBy<Map.Entry<OtpType, Double>> { it.value }
                    .thenByDescending { CATEGORY_KEYWORDS[it.key]?.size ?: Int.MAX_VALUE }
            )
            ?.key
            ?: OtpType.UNKNOWN

        return winner to ClassifierTier.KEYWORD
    }

    private fun roundTo2dp(x: Double): Double = (x * 100.0).roundToLong() / 100.0

    companion object {
        const val SCORE_THRESHOLD = 0.5
        const val SENDER_BOOST_WEIGHT = 0.5

        val CATEGORY_KEYWORDS: Map<OtpType, Map<String, Double>> = mapOf(
            OtpType.TRANSACTION to mapOf(
                "transaction" to 0.9,
                "payment" to 0.8,
                "debit" to 0.9,
                "credit" to 0.7,
                "credited" to 0.9,
                "debited" to 0.9,
                "INR" to 0.8,
                "Rs." to 0.8,
                "bank" to 0.4,
                "account" to 0.3,
                "a/c" to 0.7,
                "UPI" to 0.8,
                "NEFT" to 0.7,
                "IMPS" to 0.7
            ),
            OtpType.LOGIN to mapOf(
                "log in" to 0.9,
                "sign in" to 0.9,
                "authenticate" to 0.8,
                "2FA" to 0.9,
                "two-factor" to 0.9,
                "verification code" to 0.6,
                "login" to 0.8
            ),
            OtpType.PARCEL_DELIVERY to mapOf(
                "deliver" to 0.8,
                "delivered" to 0.9,
                "shipment" to 0.9,
                "parcel" to 0.9,
                "tracking" to 0.8,
                "courier" to 0.9,
                "dispatch" to 0.7,
                "dispatched" to 0.7,
                "out for delivery" to 1.0,
                "AWB" to 0.9
            ),
            OtpType.REGISTRATION to mapOf(
                "register" to 0.8,
                "sign up" to 0.9,
                "signup" to 0.9,
                "create account" to 0.9,
                "new account" to 0.8,
                "welcome" to 0.4
            ),
            OtpType.PASSWORD_RESET to mapOf(
                "reset password" to 1.0,
                "reset your password" to 1.0,
                "forgot" to 0.7,
                "recover" to 0.7,
                "change password" to 0.9
            ),
            OtpType.GOVERNMENT to mapOf(
                "Aadhaar" to 1.0,
                "PAN" to 0.8,
                "tax" to 0.6,
                "govt" to 0.7,
                "DigiLocker" to 0.9,
                "UMANG" to 0.9,
                "e-filing" to 0.9,
                "ITR" to 0.9
            )
        )

        private val CATEGORY_REGEXES: Map<OtpType, List<Pair<Regex, Double>>> =
            CATEGORY_KEYWORDS.mapValues { (_, keywords) ->
                keywords.map { (kw, weight) ->
                    Regex("(?<!\\w)${Regex.escape(kw)}(?!\\w)", RegexOption.IGNORE_CASE) to weight
                }
            }

        val SENDER_BOOST: Map<String, OtpType> = mapOf(
            "HDFCBK" to OtpType.TRANSACTION,
            "SBIBNK" to OtpType.TRANSACTION,
            "ICICIB" to OtpType.TRANSACTION,
            "AXISBK" to OtpType.TRANSACTION,
            "KOTAKB" to OtpType.TRANSACTION,
            "AMZNIN" to OtpType.PARCEL_DELIVERY,
            "FKRTIN" to OtpType.PARCEL_DELIVERY,
            "SWIGGY" to OtpType.PARCEL_DELIVERY,
            "ZOMATO" to OtpType.PARCEL_DELIVERY,
            "IKITWT" to OtpType.LOGIN,
            "GLOGIN" to OtpType.LOGIN,
            "MSFTNO" to OtpType.LOGIN,
            "GOVTIN" to OtpType.GOVERNMENT,
            "AABORL" to OtpType.GOVERNMENT,
            "ITDEPT" to OtpType.GOVERNMENT
        )
    }
}
