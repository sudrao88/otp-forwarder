package com.otpforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.otpforwarder.service.OtpProcessingService

/**
 * Entry point for the pipeline. Receives `SMS_RECEIVED` broadcasts, reassembles
 * multi-part messages per sender, and hands each reconstructed message off to
 * [OtpProcessingService].
 *
 * We use [goAsync] to give ourselves a bit more headroom than the usual 10s
 * [onReceive] window, but all we actually do here is parse PDUs and start the
 * service — the heavy lifting happens in the service.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pending = goAsync()
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            // Multi-part SMS arrive as multiple SmsMessage PDUs with the same
            // originating address; concatenate the bodies in arrival order.
            val assembled = linkedMapOf<String, StringBuilder>()
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
                val body = sms.displayMessageBody ?: sms.messageBody ?: continue
                assembled.getOrPut(sender) { StringBuilder() }.append(body)
            }

            for ((sender, bodyBuilder) in assembled) {
                val body = bodyBuilder.toString()
                if (body.isBlank()) continue
                val serviceIntent = OtpProcessingService.intent(context, sender, body)
                startProcessingService(context, serviceIntent)
            }
        } finally {
            pending.finish()
        }
    }

    private fun startProcessingService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
