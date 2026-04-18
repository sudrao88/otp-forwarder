package com.otpforwarder.worker

import android.content.Context
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

        return try {
            when (processIncomingSms(sender, body)) {
                is ProcessIncomingSmsUseCase.Result.Forwarded,
                is ProcessIncomingSmsUseCase.Result.NoMatchingRule,
                ProcessIncomingSmsUseCase.Result.NotOtp -> Result.success()
            }
        } catch (t: Throwable) {
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val INITIAL_BACKOFF_SECONDS = 10L
        private const val MAX_ATTEMPTS = 5

        private fun uniqueName(sender: String, body: String): String =
            "otp-retry-${(sender + body).hashCode()}"

        fun enqueue(context: Context, sender: String, body: String) {
            val data: Data = workDataOf(
                KEY_SENDER to sender,
                KEY_BODY to body
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
