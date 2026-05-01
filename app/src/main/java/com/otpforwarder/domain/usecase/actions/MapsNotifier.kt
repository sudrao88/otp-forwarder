package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.util.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow seam over the platform notification stack so [OpenMapsActionUseCase]
 * stays unit-testable on the JVM. Returns `true` when the notification was
 * successfully posted; `false` covers the missing-permission and platform
 * failure paths. [autoLaunch] turns on the full-screen intent path; the OS
 * decides whether to honour it (screen off / unlocked) or fall back to the
 * tap UX, so the seam does not need to model the fallback explicitly.
 */
fun interface MapsNotifier {
    fun notifyNavigation(sender: String, mapsUrl: String, autoLaunch: Boolean): Boolean
}

@Singleton
class AndroidMapsNotifier @Inject constructor(
    private val notificationHelper: NotificationHelper
) : MapsNotifier {

    override fun notifyNavigation(sender: String, mapsUrl: String, autoLaunch: Boolean): Boolean =
        notificationHelper.notifyMapsNavigation(sender, mapsUrl, autoLaunch)
}
