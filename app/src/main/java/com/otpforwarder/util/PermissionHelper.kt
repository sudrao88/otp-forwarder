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
 * The app touches six permissions total:
 *   - [Manifest.permission.RECEIVE_SMS] — runtime (SMS group), requested at onboarding
 *   - [Manifest.permission.SEND_SMS] — runtime (SMS group), requested at onboarding
 *   - [Manifest.permission.POST_NOTIFICATIONS] — runtime, requested at onboarding (API 33+)
 *   - [Manifest.permission.CALL_PHONE] — runtime, requested per-rule when a PlaceCall action is added
 *   - [Manifest.permission.ACCESS_NOTIFICATION_POLICY] — special access (system settings deep-link),
 *     requested per-rule when a SetRingerLoud action is added
 *   - [Manifest.permission.USE_FULL_SCREEN_INTENT] — install-time grant pre-Android 14; on
 *     Android 14+ it becomes special access, queried via [hasFullScreenIntent] so the rule
 *     editor can hint when auto-launch is toggled on but the OS will downgrade it.
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Onboarding-gated set only. CALL_PHONE and ACCESS_NOTIFICATION_POLICY are requested
    // per-rule from the rule editor: CALL_PHONE is a runtime permission scoped to the
    // PlaceCall action, and ACCESS_NOTIFICATION_POLICY is a special-access setting that
    // needs a system-settings deep-link, not a runtime prompt.
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

    /**
     * `true` when the OS will honour a notification's full-screen intent.
     *
     * Pre-Android 14 the manifest declaration is sufficient. On 14+ the user
     * may revoke the grant from system settings, in which case the OS demotes
     * FSI notifications to ordinary heads-up. The rule editor uses this to
     * surface a hint when auto-launch is enabled but the OS would silently
     * fall back to the tap UX.
     */
    fun hasFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
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
        // API 30+ exposes a per-app deep-link; the constant is @SystemApi, so the
        // action string is used directly. Fall through to the list screen on older
        // OSes, or if the detail screen can't be resolved on this device.
        val detailIntent = Intent(ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val canDeepLink = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            detailIntent.resolveActivity(context.packageManager) != null
        val intent = if (canDeepLink) {
            detailIntent
        } else {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
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

        /** Context-only variant used from composables that don't have an injected helper. */
        fun openAppSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        private const val ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS =
            "android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS"
    }
}
