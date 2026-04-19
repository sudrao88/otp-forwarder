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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiOtpClassifierTest {

    private class FixedRuntime(
        private val available: Boolean,
        private val response: String = "",
        private val error: Throwable? = null
    ) : GeminiRuntime {
        var generateCalls = 0
            private set

        override suspend fun isAvailable(): Boolean = available
        override suspend fun generate(prompt: String): String {
            generateCalls++
            error?.let { throw it }
            return response
        }
    }

    @Test
    fun `when unavailable returns UNKNOWN without invoking generate`() = runBlocking {
        val runtime = FixedRuntime(available = false)
        val (type, tier) = GeminiOtpClassifier(runtime).classify("HDFCBK", "anything")
        assertEquals(OtpType.UNKNOWN, type)
        assertEquals(ClassifierTier.GEMINI_NANO, tier)
        assertEquals(0, runtime.generateCalls)
    }

    @Test
    fun `parses a clean category token`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "TRANSACTION")
        val (type, tier) = GeminiOtpClassifier(runtime).classify("HDFCBK", "INR 500 debited.")
        assertEquals(OtpType.TRANSACTION, type)
        assertEquals(ClassifierTier.GEMINI_NANO, tier)
    }

    @Test
    fun `parses token with trailing period`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "TRANSACTION.")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.TRANSACTION, type)
    }

    @Test
    fun `parses token with lowercase and whitespace`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "  login\n")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.LOGIN, type)
    }

    @Test
    fun `takes the first token when model replies with a sentence`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "I think: LOGIN")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        // First token is "I" — invalid → UNKNOWN
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `ALL response maps to UNKNOWN`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "ALL")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `empty response maps to UNKNOWN`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `garbled response maps to UNKNOWN`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "\$\$@#!!")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.UNKNOWN, type)
    }

    @Test
    fun `runtime error surfaces as UNKNOWN (non-cancellation)`() = runBlocking {
        val runtime = FixedRuntime(available = true, error = IllegalStateException("boom"))
        val (type, tier) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.UNKNOWN, type)
        assertEquals(ClassifierTier.GEMINI_NANO, tier)
    }

    @Test
    fun `quoted token is parsed`() = runBlocking {
        val runtime = FixedRuntime(available = true, response = "\"PARCEL_DELIVERY\"")
        val (type, _) = GeminiOtpClassifier(runtime).classify("S", "b")
        assertEquals(OtpType.PARCEL_DELIVERY, type)
    }

    @Test
    fun `cancellation propagates through classify`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val runtime = object : GeminiRuntime {
            override suspend fun isAvailable(): Boolean = true
            override suspend fun generate(prompt: String): String {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val classifier = GeminiOtpClassifier(runtime)

        coroutineScope {
            val job = async { classifier.classify("S", "b") }
            started.await()
            job.cancel()
            var caught = false
            try {
                job.await()
                fail("expected CancellationException")
            } catch (c: CancellationException) {
                caught = true
            }
            assertTrue(caught)
        }
    }
}

class DisabledGeminiRuntimeTest {

    @Test
    fun `isAvailable is always false`() = runBlocking {
        assertFalse(DisabledGeminiRuntime().isAvailable())
    }

    @Test
    fun `generate throws UnsupportedOperationException`() = runBlocking {
        var thrown = false
        try {
            DisabledGeminiRuntime().generate("anything")
            fail("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
