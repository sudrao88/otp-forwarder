package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KeywordOtpClassifierTest {

    private val classifier = KeywordOtpClassifier()

    private fun classify(sender: String, body: String): Pair<OtpType, ClassifierTier> =
        runBlocking { classifier.classify(sender, body) }

    @Test
    fun `transaction detected from bank SMS wording`() {
        val (type, tier) = classify(
            "UNKNOWN",
            "INR 500 debited from your a/c via UPI. Ref 123."
        )
        assertEquals(OtpType.TRANSACTION, type)
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `login detected from 2FA wording`() {
        val (type, _) = classify("UNKNOWN", "Your 2FA code to sign in: 482910. Do not share.")
        assertEquals(OtpType.LOGIN, type)
    }

    @Test
    fun `parcel delivery detected from courier wording`() {
        val (type, _) = classify(
            "UNKNOWN",
            "Your shipment is out for delivery today via courier. Tracking AWB 123."
        )
        assertEquals(OtpType.PARCEL_DELIVERY, type)
    }

    @Test
    fun `registration detected from sign up wording`() {
        val (type, _) = classify("UNKNOWN", "Welcome! Sign up to create account with code 482910.")
        assertEquals(OtpType.REGISTRATION, type)
    }

    @Test
    fun `password reset detected`() {
        val (type, _) = classify(
            "UNKNOWN",
            "Use this code to reset your password. Forgot password? Recover here."
        )
        assertEquals(OtpType.PASSWORD_RESET, type)
    }

    @Test
    fun `government detected from Aadhaar wording`() {
        val (type, _) = classify("UNKNOWN", "Aadhaar OTP for DigiLocker login: 482910.")
        assertEquals(OtpType.GOVERNMENT, type)
    }

    @Test
    fun `below-threshold score returns UNKNOWN`() {
        // "bank" alone = 0.4, below 0.5 threshold
        val (type, _) = classify("UNKNOWN", "Your bank will contact you shortly.")
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `empty body returns UNKNOWN`() {
        val (type, _) = classify("UNKNOWN", "")
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `sender reputation boost elevates category over threshold`() {
        // body keyword "account" alone = 0.3 (below threshold);
        // HDFCBK sender boost +0.5 pushes TRANSACTION to 0.8 → wins.
        val (type, _) = classify("VM-HDFCBK", "Your account is now updated.")
        assertEquals(OtpType.TRANSACTION, type)
    }

    @Test
    fun `sender reputation boost alone reaches threshold`() {
        // No content keywords matched; sender boost = 0.5 exactly at threshold.
        val (type, _) = classify("AX-HDFCBK", "Hello there.")
        assertEquals(OtpType.TRANSACTION, type)
    }

    @Test
    fun `sender reputation boost changes winner`() {
        // Without sender: LOGIN ("2FA" 0.9) would beat TRANSACTION ("account" 0.3).
        // With HDFCBK boost, TRANSACTION = 0.3 + 0.5 = 0.8 still loses to LOGIN 0.9 — winner unchanged.
        val (type, _) = classify("VM-HDFCBK", "Use 2FA on your account.")
        assertEquals(OtpType.LOGIN, type)
    }

    @Test
    fun `tie broken in favor of more specific category`() {
        // GOVERNMENT: "tax" (0.6) + "PAN" (0.8) = 1.4 ; 8 keywords
        // TRANSACTION: "credit" (0.7) + "bank" (0.4) + "account" (0.3) = 1.4 ; 14 keywords
        // Tie at 1.4 → GOVERNMENT (smaller keyword set) wins.
        val (type, _) = classify("UNKNOWN", "tax PAN credit bank account")
        assertEquals(OtpType.GOVERNMENT, type)
    }

    @Test
    fun `tier returned is always KEYWORD`() {
        val (_, tier) = classify("UNKNOWN", "INR 500 debited.")
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `sender matching is case-insensitive`() {
        val (type, _) = classify("vm-hdfcbk", "Your account was debited.")
        assertEquals(OtpType.TRANSACTION, type)
    }

    @Test
    fun `parcel sender boost applied`() {
        val (type, _) = classify("VM-AMZNIN", "Your order has been dispatched.")
        // body: "dispatch" 0.7 ; PARCEL boost +0.5 = 1.2 → PARCEL_DELIVERY
        assertEquals(OtpType.PARCEL_DELIVERY, type)
    }

    @Test
    fun `government sender boost applied`() {
        val (type, _) = classify("VM-ITDEPT", "Please file your return.")
        // no body keyword matches; GOVERNMENT boost alone = 0.5 → threshold met
        assertEquals(OtpType.GOVERNMENT, type)
    }
}
