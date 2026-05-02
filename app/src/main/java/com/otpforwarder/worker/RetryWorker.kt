package com.otpforwarder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.otpforwarder.domain.usecase.ProcessIncomingSmsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Retries the full OTP pipeline for a single SMS that failed inside
 * [com.otpforwarder.service.OtpProcessingService].
 *
 * Uses exponential backoff — WorkManager doubles the delay each attempt
 * starting from [INITIAL_BACKOFF_SECONDS]. After [MAX_ATTEMPTS] runs the work
 * is failed and the user keeps the failure notification from the service run.
 */
@HiltWorker
class RetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processIncomingSms: ProcessIncomingSmsUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val receivedAtMillis = inputData.getLong(KEY_RECEIVED_AT, 0L)
        val receivedAt = if (receivedAtMillis > 0) Instant.ofEpochMilli(receivedAtMillis) else Instant.now()

        return try {
            when (processIncomingSms(sender, body, receivedAt)) {
                is ProcessIncomingSmsUseCase.Result.Forwarded,
                is ProcessIncomingSmsUseCase.Result.NoMatchingRule -> Result.success()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Retry attempt ${runAttemptCount + 1} failed", t)
            // runAttemptCount is 0-indexed (0 on the first run). Check whether
            // this run was the last permitted attempt.
            val isLastAttempt = runAttemptCount + 1 >= MAX_ATTEMPTS
            if (isLastAttempt) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "RetryWorker"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_RECEIVED_AT = "received_at"
        private const val INITIAL_BACKOFF_SECONDS = 10L
        private const val MAX_ATTEMPTS = 5

        private fun uniqueName(sender: String, body: String): String =
            "otp-retry-${(sender + body).hashCode()}"

        fun enqueue(context: Context, sender: String, body: String, receivedAtMillis: Long) {
            val data: Data = workDataOf(
                KEY_SENDER to sender,
                KEY_BODY to body,
                KEY_RECEIVED_AT to receivedAtMillis
            )
            val request = OneTimeWorkRequestBuilder<RetryWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(sender, body),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
