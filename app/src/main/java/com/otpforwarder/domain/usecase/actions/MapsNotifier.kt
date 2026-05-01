package com.otpforwarder.domain.usecase.actions

import com.otpforwarder.util.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow seam over the platform notification stack so [OpenMapsActionUseCase]
 * stays unit-testable on the JVM. Returns `true` when the notification was
 * successfully posted; `false` covers the missing-permission and platform
 * failure paths.
 */
fun interface MapsNotifier {
    fun notifyNavigation(sender: String, mapsUrl: String): Boolean
}

@Singleton
class AndroidMapsNotifier @Inject constructor(
    private val notificationHelper: NotificationHelper
) : MapsNotifier {

    override fun notifyNavigation(sender: String, mapsUrl: String): Boolean =
        notificationHelper.notifyMapsNavigation(sender, mapsUrl)
}
