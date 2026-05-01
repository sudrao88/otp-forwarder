package com.otpforwarder.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.otpforwarder.R
import com.otpforwarder.domain.model.IncomingSms
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app's notification channels and builds notifications shown by the
 * SMS processing pipeline.
 *
 * Three channels:
 *  - [CHANNEL_PROCESSING] — low-importance, used for the foreground service
 *    notification that surfaces briefly while an OTP is being processed.
 *  - [CHANNEL_RESULTS] — default importance, used for forwarding outcome
 *    notifications (sent / failed / retrying).
 *  - [CHANNEL_MAPS] — high importance with heads-up alert, used for the
 *    Maps-link rule action so the navigate prompt surfaces while driving.
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
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.notification_processing_title))
            .setContentText(context.getString(R.string.notification_processing_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /**
     * Notifies the user that an SMS was processed.
     *
     * [recipientNames] is the list of recipients that actually received the
     * forwarded SMS. If it is empty (every matching rule fired only non-forward
     * actions, e.g. ring loud / call), the notification falls back to the rule
     * count so the text still reflects what happened.
     *
     * The body line includes the OTP code+type when one was detected. Plain
     * (non-OTP) SMS that triggered a rule simply name the sender.
     */
    fun notifyForwarded(sms: IncomingSms, recipientNames: List<String>, ruleCount: Int) {
        val target = if (recipientNames.isNotEmpty()) {
            recipientNames.joinToString(", ")
        } else {
            "$ruleCount rule(s)"
        }
        val text = buildString {
            val otp = sms.otp
            if (otp != null) {
                append(otp.type.name).append(" · ")
                append(otp.code).append(" → ")
            } else {
                append(sms.sender).append(" → ")
            }
            append(target)
        }
        notify(
            id = NotificationIds.forSms(sms),
            title = context.getString(R.string.notification_forwarded_title),
            text = text,
            smallIcon = R.drawable.ic_notification_small
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
            id = NotificationIds.forRetry(sender, body),
            title = context.getString(R.string.notification_retrying_title),
            text = sender,
            smallIcon = R.drawable.ic_notification_small
        )
    }

    /**
     * Posts a high-importance notification whose tap fires an `ACTION_VIEW`
     * intent on [mapsUrl], preferring the Google Maps app and falling back to
     * a chooser when it isn't installed. Returns `true` when posting
     * succeeded — `false` is reserved for the missing POST_NOTIFICATIONS path
     * so callers can report a faithful action outcome.
     */
    @SuppressLint("MissingPermission")
    fun notifyMapsNavigation(sender: String, mapsUrl: String): Boolean {
        if (!hasPostNotificationsPermission()) return false
        val pendingIntent = PendingIntent.getActivity(
            context,
            mapsUrl.hashCode(),
            buildMapsViewIntent(mapsUrl),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.getString(R.string.notification_maps_title, sender)
        val text = context.getString(R.string.notification_maps_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_MAPS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(NotificationIds.forMaps(sender, mapsUrl), notification)
        }.onFailure { Log.w(TAG, "Failed to post Maps navigation notification", it) }.isSuccess
    }

    /**
     * Builds the View intent that the notification fires on tap. We prefer the
     * official Google Maps package; if it isn't installed [resolveActivity]
     * returns null and we fall back to a chooser so any browser/maps handler
     * can serve the URL.
     */
    private fun buildMapsViewIntent(mapsUrl: String): Intent {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
            setPackage(MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val mapsInstalled = viewIntent.resolveActivity(context.packageManager) != null
        if (mapsInstalled) return viewIntent
        val chooser = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(chooser, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @SuppressLint("MissingPermission")
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

        val maps = NotificationChannel(
            CHANNEL_MAPS,
            context.getString(R.string.notification_channel_maps_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_maps_desc)
        }

        manager.createNotificationChannels(listOf(processing, results, maps))
    }

    companion object {
        const val CHANNEL_PROCESSING = "otp_processing"
        const val CHANNEL_RESULTS = "otp_results"
        const val CHANNEL_MAPS = "maps_navigation"
        const val PROCESSING_NOTIFICATION_ID = 1001
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val TAG = "NotificationHelper"
    }
}

object NotificationIds {
    fun forSms(sms: IncomingSms): Int {
        val payload = sms.otp?.code ?: sms.body
        return (sms.sender + payload + sms.receivedAt.toEpochMilli()).hashCode()
    }

    fun forRetry(sender: String, body: String): Int =
        (sender + body.hashCode()).hashCode()

    fun forMaps(sender: String, mapsUrl: String): Int =
        ("maps:" + sender + mapsUrl).hashCode()
}
