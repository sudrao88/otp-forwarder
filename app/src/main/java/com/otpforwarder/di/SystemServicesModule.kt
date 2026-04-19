package com.otpforwarder.di

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides platform services needed by rule-action use cases. Each is pulled
 * from [Context.getSystemService] and scoped as a singleton so action use
 * cases stay trivially injectable from Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemServicesModule {

    @Provides
    @Singleton
    fun provideAudioManager(@ApplicationContext context: Context): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun provideTelecomManager(@ApplicationContext context: Context): TelecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
}
