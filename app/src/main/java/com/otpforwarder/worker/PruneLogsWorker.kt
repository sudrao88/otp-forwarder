package com.otpforwarder.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Periodically drops received-SMS feed rows older than [WINDOW]. Scheduled
 * from [com.otpforwarder.OtpForwarderApplication] at app start. Moving this
 * out of `HomeViewModel.init` stops opening Home from destroying entries the
 * user might still want to see.
 */
@HiltWorker
class PruneLogsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val receivedSmsRepository: ReceivedSmsRepository,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = clock.instant().minus(WINDOW).toEpochMilli()
        receivedSmsRepository.pruneOlderThan(cutoff)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "otp-prune-logs"
        private val WINDOW: Duration = Duration.ofDays(30)

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
