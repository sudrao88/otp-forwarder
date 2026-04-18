package com.otpforwarder.data.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.otpforwarder.domain.sms.SmsSender
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SmsSender] backed by the platform [SmsManager]. Long messages are split into
 * multipart segments automatically. Send failures surface as `false` rather than
 * propagating exceptions, so one bad recipient cannot abort the forwarding loop.
 */
@Singleton
class AndroidSmsSender @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsSender {

    override fun send(phoneNumber: String, message: String): Boolean = runCatching {
        val manager = smsManager()
        val parts = manager.divideMessage(message)
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        } else {
            manager.sendTextMessage(phoneNumber, null, message, null, null)
        }
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
