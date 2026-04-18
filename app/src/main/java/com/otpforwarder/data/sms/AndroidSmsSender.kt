package com.otpforwarder.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.otpforwarder.domain.sms.SmsSender
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SmsSender] backed by the platform [SmsManager]. Long messages are split into
 * multipart segments automatically. Any failure — missing permission, carrier
 * error, null [SmsManager] service — surfaces as `false` rather than propagating
 * exceptions, so one bad recipient cannot abort the forwarding loop.
 */
@Singleton
class AndroidSmsSender @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsSender {

    override fun send(phoneNumber: String, message: String): Boolean {
        if (!hasSendSmsPermission()) {
            Log.w(TAG, "SEND_SMS permission not granted; skipping forward")
            return false
        }
        return runCatching {
            val manager = smsManager() ?: error("SmsManager unavailable")
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                manager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            true
        }.getOrElse { t ->
            Log.e(TAG, "Failed to send SMS", t)
            false
        }
    }

    private fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    companion object {
        private const val TAG = "AndroidSmsSender"
    }
}
