package com.otpforwarder.worker

import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the `Data` key contract between [com.otpforwarder.service.OtpProcessingService]
 * (enqueue) and [RetryWorker.doWork] (consume). Any rename of these keys
 * without a coordinated update would silently fail every retry.
 */
class RetryWorkerTest {

    @Test
    fun inputData_roundTripsSenderAndBody() {
        val sender = "HDFCBK"
        val body = "Your OTP is 482910."

        val data = workDataOf(
            RetryWorker.KEY_SENDER to sender,
            RetryWorker.KEY_BODY to body
        )

        assertEquals(sender, data.getString(RetryWorker.KEY_SENDER))
        assertEquals(body, data.getString(RetryWorker.KEY_BODY))
    }

    @Test
    fun inputData_missingKeys_returnNull() {
        val empty = workDataOf()
        assertEquals(null, empty.getString(RetryWorker.KEY_SENDER))
        assertEquals(null, empty.getString(RetryWorker.KEY_BODY))
    }
}
