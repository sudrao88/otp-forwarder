package com.otpforwarder.data.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val masterEnabled: Flow<Boolean> = booleanFlow(KEY_MASTER_ENABLED, DEFAULT_MASTER_ENABLED)

    val includeOriginalMessage: Flow<Boolean> =
        booleanFlow(KEY_INCLUDE_ORIGINAL, DEFAULT_INCLUDE_ORIGINAL)

    fun isMasterEnabled(): Boolean =
        prefs.getBoolean(KEY_MASTER_ENABLED, DEFAULT_MASTER_ENABLED)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun isIncludeOriginalMessage(): Boolean =
        prefs.getBoolean(KEY_INCLUDE_ORIGINAL, DEFAULT_INCLUDE_ORIGINAL)

    fun setIncludeOriginalMessage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_ORIGINAL, enabled).apply()
    }

    private fun booleanFlow(key: String, default: Boolean): Flow<Boolean> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                if (changed == key) trySend(prefs.getBoolean(key, default))
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
            .onStart { emit(prefs.getBoolean(key, default)) }
            .distinctUntilChanged()

    companion object {
        private const val PREFS_NAME = "otp_forwarder_settings"
        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_INCLUDE_ORIGINAL = "include_original_message"
        private const val DEFAULT_MASTER_ENABLED = true
        private const val DEFAULT_INCLUDE_ORIGINAL = true
    }
}
