package com.otpforwarder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.otpforwarder.domain.repository.OtpLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Periodically drops OTP log entries older than [WINDOW]. Scheduled from
 * [com.otpforwarder.OtpForwarderApp] at app start. Moving this out of
 * `HomeViewModel.init` stops opening Home from destroying logs the user
 * might still want to see.
 */
@HiltWorker
class PruneLogsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val otpLogRepository: OtpLogRepository,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = clock.instant().minus(WINDOW).toEpochMilli()
        otpLogRepository.pruneOldLogs(cutoff)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "otp-prune-logs"
        private val WINDOW: Duration = Duration.ofHours(12)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PruneLogsWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
