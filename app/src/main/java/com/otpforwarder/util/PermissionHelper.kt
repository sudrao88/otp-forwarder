package com.otpforwarder.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralises runtime permission logic for the OTP pipeline.
 *
 * The app needs three runtime permissions:
 *   - [Manifest.permission.RECEIVE_SMS] — to observe incoming SMS
 *   - [Manifest.permission.SEND_SMS] — to forward extracted OTPs
 *   - [Manifest.permission.POST_NOTIFICATIONS] — to surface results (API 33+)
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Permissions the app actively requests, filtered for the running OS. */
    val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasReceiveSms(): Boolean = isGranted(Manifest.permission.RECEIVE_SMS)

    fun hasSendSms(): Boolean = isGranted(Manifest.permission.SEND_SMS)

    fun hasPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return isGranted(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun hasCallPhone(): Boolean = isGranted(Manifest.permission.CALL_PHONE)

    fun hasNotificationPolicyAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** `true` only when every permission required for the pipeline is granted. */
    fun hasAllRequired(): Boolean = requiredPermissions.all(::isGranted)

    /** Opens the app's system settings so a user can grant denied permissions. */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Opens the system Notification Policy access screen (special access). */
    fun openNotificationPolicySettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    companion object {
        /**
         * Convenience for activities: registers a multi-permission launcher and
         * returns the callback-driven result contract.
         */
        fun register(
            activity: ComponentActivity,
            onResult: (Map<String, Boolean>) -> Unit
        ): ActivityResultLauncher<Array<String>> = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            onResult
        )
    }
}
