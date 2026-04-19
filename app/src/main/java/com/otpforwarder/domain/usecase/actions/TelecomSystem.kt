package com.otpforwarder.domain.usecase.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow seam over the Android telecom stack so [PlaceCallActionUseCase] stays
 * unit-testable on the JVM.
 */
interface TelecomSystem {
    fun hasCallPhonePermission(): Boolean
    fun placeCall(phoneNumber: String)
}

@Singleton
class AndroidTelecomSystem @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telecomManager: TelecomManager
) : TelecomSystem {

    override fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    override fun placeCall(phoneNumber: String) {
        val uri = Uri.fromParts("tel", phoneNumber, null)
        telecomManager.placeCall(uri, null)
    }
}
