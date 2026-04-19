package com.otpforwarder.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.otpforwarder.R
import com.otpforwarder.domain.model.Otp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app's notification channels and builds notifications shown by the
 * SMS processing pipeline.
 *
 * Two channels:
 *  - [CHANNEL_PROCESSING] — low-importance, used for the foreground service
 *    notification that surfaces briefly while an OTP is being processed.
 *  - [CHANNEL_RESULTS] — default importance, used for forwarding outcome
 *    notifications (sent / failed / retrying).
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        createChannels()
    }

    /**
     * Ongoing notification attached to the foreground service. Kept minimal and
     * low-importance so the service can be promoted to foreground without
     * creating visible noise.
     */
    fun buildProcessingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROCESSING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notification_processing_title))
            .setContentText(context.getString(R.string.notification_processing_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /**
     * Notifies the user that an OTP was processed.
     *
     * [recipientNames] is the list of recipients that actually received the
     * forwarded SMS. If it is empty (every matching rule fired only non-forward
     * actions, e.g. ring loud / call), the notification falls back to the rule
     * count so the text still reflects what happened.
     */
    fun notifyForwarded(otp: Otp, recipientNames: List<String>, ruleCount: Int) {
        val target = if (recipientNames.isNotEmpty()) {
            recipientNames.joinToString(", ")
        } else {
            "$ruleCount rule(s)"
        }
        val text = buildString {
            append(otp.type.name).append(" · ")
            append(otp.code).append(" → ")
            append(target)
        }
        notify(
            id = otp.notificationId(),
            title = context.getString(R.string.notification_forwarded_title),
            text = text,
            smallIcon = android.R.drawable.stat_sys_upload_done
        )
    }

    /** Notifies the user that forwarding failed. */
    fun notifyFailed(otp: Otp, recipientNames: List<String>) {
        val text = buildString {
            append(otp.type.name).append(" · ")
            append(otp.code).append(" → ")
            append(recipientNames.joinToString(", "))
        }
        notify(
            id = otp.notificationId(),
            title = context.getString(R.string.notification_failed_title),
            text = text,
            smallIcon = android.R.drawable.stat_notify_error
        )
    }

    /**
     * Notifies the user that forwarding is being retried in the background.
     *
     * The notification id incorporates a hash of the message body so two
     * concurrent retries from the same sender (different SMS) do not collide
     * on a single notification slot.
     */
    fun notifyRetrying(sender: String, body: String) {
        notify(
            id = (sender + body.hashCode()).hashCode(),
            title = context.getString(R.string.notification_retrying_title),
            text = sender,
            smallIcon = android.R.drawable.stat_notify_sync
        )
    }

    private fun notify(id: Int, title: String, text: String, smallIcon: Int) {
        if (!hasPostNotificationsPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val processing = NotificationChannel(
            CHANNEL_PROCESSING,
            context.getString(R.string.notification_channel_processing_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_processing_desc)
            setShowBadge(false)
        }

        val results = NotificationChannel(
            CHANNEL_RESULTS,
            context.getString(R.string.notification_channel_results_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_results_desc)
        }

        manager.createNotificationChannels(listOf(processing, results))
    }

    private fun Otp.notificationId(): Int =
        (sender + code + detectedAt.toEpochMilli()).hashCode()

    companion object {
        const val CHANNEL_PROCESSING = "otp_processing"
        const val CHANNEL_RESULTS = "otp_results"
        const val PROCESSING_NOTIFICATION_ID = 1001
    }
}
