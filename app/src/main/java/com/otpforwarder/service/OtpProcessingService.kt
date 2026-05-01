package com.otpforwarder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import android.util.Log
import com.otpforwarder.data.settings.SettingsRepository
import com.otpforwarder.domain.usecase.ProcessIncomingSmsUseCase
import com.otpforwarder.util.NotificationHelper
import com.otpforwarder.worker.RetryWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Short-lived foreground service that runs the incoming-SMS pipeline.
 *
 * The service lives only for the time it takes to detect, classify, match, and
 * forward the OTP (typically well under the 3-minute short-service cap on
 * Android 14+). Multiple concurrent SMS triggers are tracked with a ref-count;
 * [stopSelf] is called once the last job completes.
 *
 * On pipeline failure a [RetryWorker] is enqueued so WorkManager can retry the
 * send with exponential backoff under its own constraints.
 */
@AndroidEntryPoint
class OtpProcessingService : Service() {

    @Inject lateinit var processIncomingSms: ProcessIncomingSmsUseCase
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settings: SettingsRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val inFlight = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra(EXTRA_SENDER)
        val body = intent?.getStringExtra(EXTRA_BODY)
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            finishIfIdle()
            return START_NOT_STICKY
        }
        if (!settings.isMasterEnabled()) {
            finishIfIdle()
            return START_NOT_STICKY
        }

        inFlight.incrementAndGet()
        scope.launch {
            try {
                val result = processIncomingSms(sender, body)
                when (result) {
                    is ProcessIncomingSmsUseCase.Result.Forwarded -> {
                        notificationHelper.notifyForwarded(
                            sms = result.sms,
                            recipientNames = result.recipients,
                            ruleCount = result.ruleCount
                        )
                    }
                    is ProcessIncomingSmsUseCase.Result.NoMatchingRule -> Unit
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Pipeline failed; scheduling retry", t)
                notificationHelper.notifyRetrying(sender, body)
                RetryWorker.enqueue(applicationContext, sender, body)
            } finally {
                if (inFlight.decrementAndGet() == 0) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        // Android 14+ short-service timeout — drain and stop gracefully. Any
        // unfinished work is handed off to WorkManager by the catch block.
        stopSelf()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = notificationHelper.buildProcessingNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.PROCESSING_NOTIFICATION_ID,
            notification,
            type
        )
    }

    private fun finishIfIdle() {
        if (inFlight.get() == 0) stopSelf()
    }

    companion object {
        private const val TAG = "OtpProcessingService"
        internal const val EXTRA_SENDER = "extra_sender"
        internal const val EXTRA_BODY = "extra_body"

        fun intent(context: Context, sender: String, body: String): Intent =
            Intent(context, OtpProcessingService::class.java).apply {
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_BODY, body)
            }
    }
}
