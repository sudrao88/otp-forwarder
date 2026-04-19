package com.otpforwarder.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the multi-part SMS reassembly invariant: PDUs from the same sender
 * concatenate in arrival order; distinct senders stay separate; blank-only
 * results are dropped.
 */
class SmsReceiverTest {

    @Test
    fun assembleMultipart_concatenatesPerSender_inArrivalOrder() {
        val parts = listOf(
            "HDFCBK" to "Your OTP is ",
            "HDFCBK" to "482910. ",
            "HDFCBK" to "Valid 10 min."
        )
        val result = SmsReceiver.assembleMultipart(parts)
        assertEquals(listOf("HDFCBK" to "Your OTP is 482910. Valid 10 min."), result)
    }

    @Test
    fun assembleMultipart_keepsDistinctSendersSeparate() {
        val parts = listOf(
            "HDFCBK" to "a",
            "ICICI" to "x",
            "HDFCBK" to "b",
            "ICICI" to "y"
        )
        val result = SmsReceiver.assembleMultipart(parts)
        assertEquals(
            listOf(
                "HDFCBK" to "ab",
                "ICICI" to "xy"
            ),
            result
        )
    }

    @Test
    fun assembleMultipart_dropsBlankAssembly() {
        val parts = listOf(
            "HDFCBK" to "",
            "HDFCBK" to "   "
        )
        val result = SmsReceiver.assembleMultipart(parts)
        assertEquals(emptyList<Pair<String, String>>(), result)
    }

    @Test
    fun assembleMultipart_emptyInput_returnsEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), SmsReceiver.assembleMultipart(emptyList()))
    }
}
