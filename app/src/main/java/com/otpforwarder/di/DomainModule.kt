package com.otpforwarder.di

import com.otpforwarder.data.gemini.AiCoreGeminiRuntime
import com.otpforwarder.data.sms.AndroidSmsSender
import com.otpforwarder.domain.classification.GeminiRuntime
import com.otpforwarder.domain.classification.OtpClassifier
import com.otpforwarder.domain.classification.TieredOtpClassifier
import com.otpforwarder.domain.detection.OtpDetector
import com.otpforwarder.domain.detection.RegexOtpDetector
import com.otpforwarder.domain.sms.SmsSender
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindOtpDetector(impl: RegexOtpDetector): OtpDetector

    @Binds
    @Singleton
    abstract fun bindOtpClassifier(impl: TieredOtpClassifier): OtpClassifier

    @Binds
    @Singleton
    abstract fun bindGeminiRuntime(impl: AiCoreGeminiRuntime): GeminiRuntime

    @Binds
    @Singleton
    abstract fun bindSmsSender(impl: AndroidSmsSender): SmsSender

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
