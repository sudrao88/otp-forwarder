package com.otpforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.otpforwarder.service.OtpProcessingService
import com.otpforwarder.worker.RetryWorker

/**
 * Entry point for the pipeline. Receives `SMS_RECEIVED` broadcasts, reassembles
 * multi-part messages per sender, and hands each reconstructed message off to
 * [OtpProcessingService]. If the foreground-service start is blocked (Android
 * 12+ background start restrictions), we fall back to [RetryWorker] so the SMS
 * is still processed instead of lost.
 *
 * All work here is synchronous PDU parsing + startForegroundService, so the
 * standard 10s onReceive window is plenty — no goAsync required. Any unhandled
 * throw inside a BroadcastReceiver would otherwise surface to the user as a
 * crash dialog for every incoming SMS.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            dispatch(context, intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to process incoming SMS broadcast", t)
        }
    }

    private fun dispatch(context: Context, intent: Intent) {
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "PDU parse failed", t)
            null
        } ?: return
        if (messages.isEmpty()) return

        val parts = messages.mapNotNull { sms ->
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: return@mapNotNull null
            val body = sms.displayMessageBody ?: sms.messageBody ?: return@mapNotNull null
            sender to body
        }
        for ((sender, body) in assembleMultipart(parts)) {
            deliver(context, sender, body)
        }
    }

    private fun deliver(context: Context, sender: String, body: String) {
        val serviceIntent = OtpProcessingService.intent(context, sender, body)
        try {
            startProcessingService(context, serviceIntent)
        } catch (t: Throwable) {
            // ForegroundServiceStartNotAllowedException (API 31+) or
            // IllegalStateException when the app is backgrounded by the system:
            // fall back to WorkManager so the SMS isn't lost.
            Log.w(TAG, "Foreground service start blocked, falling back to RetryWorker", t)
            runCatching { RetryWorker.enqueue(context, sender, body) }
                .onFailure { Log.e(TAG, "RetryWorker enqueue failed", it) }
        }
    }

    private fun startProcessingService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"

        /**
         * Multi-part SMS arrive as multiple PDUs with the same originating
         * address; concatenate bodies per sender in arrival order. Empty bodies
         * are dropped after concatenation.
         */
        internal fun assembleMultipart(
            parts: List<Pair<String, String>>
        ): List<Pair<String, String>> {
            val assembled = linkedMapOf<String, StringBuilder>()
            for ((sender, body) in parts) {
                assembled.getOrPut(sender) { StringBuilder() }.append(body)
            }
            return assembled.mapNotNull { (sender, builder) ->
                val body = builder.toString()
                if (body.isBlank()) null else sender to body
            }
        }
    }
}
