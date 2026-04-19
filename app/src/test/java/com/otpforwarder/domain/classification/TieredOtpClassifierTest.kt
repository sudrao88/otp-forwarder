package com.otpforwarder.domain.classification

import com.otpforwarder.domain.model.ClassifierTier
import com.otpforwarder.domain.model.OtpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TieredOtpClassifierTest {

    private class StubRuntime(
        private val available: Boolean,
        private val response: String = "",
        private val error: Throwable? = null
    ) : GeminiRuntime {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun generate(prompt: String): String {
            error?.let { throw it }
            return response
        }
    }

    private fun tiered(runtime: GeminiRuntime): TieredOtpClassifier =
        TieredOtpClassifier(GeminiOtpClassifier(runtime), KeywordOtpClassifier())

    @Test
    fun `gemini available and parseable wins — no keyword fallback`() = runBlocking {
        // Body would classify as PARCEL_DELIVERY under keyword, but Gemini says LOGIN.
        val classifier = tiered(StubRuntime(available = true, response = "LOGIN"))
        val (type, tier) = classifier.classify(
            "AMZNIN",
            "Your parcel is out for delivery with AWB 123."
        )
        assertEquals(OtpType.LOGIN, type)
        assertEquals(ClassifierTier.GEMINI_NANO, tier)
    }

    @Test
    fun `gemini available but UNKNOWN — falls back to keyword`() = runBlocking {
        val classifier = tiered(StubRuntime(available = true, response = "UNKNOWN"))
        val (type, tier) = classifier.classify("HDFCBK", "INR 500 debited from a/c via UPI.")
        assertEquals(OtpType.TRANSACTION, type)
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `gemini available but garbled — falls back to keyword`() = runBlocking {
        val classifier = tiered(StubRuntime(available = true, response = "@@@???"))
        val (type, tier) = classifier.classify("HDFCBK", "INR 500 debited from a/c via UPI.")
        assertEquals(OtpType.TRANSACTION, type)
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `gemini unavailable — keyword only`() = runBlocking {
        val classifier = tiered(StubRuntime(available = false))
        val (type, tier) = classifier.classify("UNKNOWN", "Your 2FA code to sign in: 482910.")
        assertEquals(OtpType.LOGIN, type)
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `gemini throws — surfaces UNKNOWN then falls back to keyword`() = runBlocking {
        val classifier = tiered(
            StubRuntime(available = true, error = IllegalStateException("boom"))
        )
        val (type, tier) = classifier.classify("HDFCBK", "INR 500 debited from a/c via UPI.")
        assertEquals(OtpType.TRANSACTION, type)
        assertEquals(ClassifierTier.KEYWORD, tier)
    }

    @Test
    fun `cancellation mid-classify propagates and does not fall back to keyword`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val runtime = object : GeminiRuntime {
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generate(prompt: String): String {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val classifier = tiered(runtime)

        coroutineScope {
            val job = async { classifier.classify("HDFCBK", "INR 500 debited.") }
            started.await()
            job.cancel()
            var caught = false
            try {
                job.await()
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                caught = true
            }
            assertTrue(caught)
        }
    }
}
