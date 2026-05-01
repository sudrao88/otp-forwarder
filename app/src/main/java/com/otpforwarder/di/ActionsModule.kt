package com.otpforwarder.di

import com.otpforwarder.domain.usecase.actions.AndroidMapsNotifier
import com.otpforwarder.domain.usecase.actions.AndroidRingerSystem
import com.otpforwarder.domain.usecase.actions.AndroidTelecomSystem
import com.otpforwarder.domain.usecase.actions.ForwardSmsAction
import com.otpforwarder.domain.usecase.actions.ForwardSmsActionUseCase
import com.otpforwarder.domain.usecase.actions.MapsNotifier
import com.otpforwarder.domain.usecase.actions.OpenMapsAction
import com.otpforwarder.domain.usecase.actions.OpenMapsActionUseCase
import com.otpforwarder.domain.usecase.actions.PlaceCallAction
import com.otpforwarder.domain.usecase.actions.PlaceCallActionUseCase
import com.otpforwarder.domain.usecase.actions.RingerSystem
import com.otpforwarder.domain.usecase.actions.SetRingerLoudAction
import com.otpforwarder.domain.usecase.actions.SetRingerLoudActionUseCase
import com.otpforwarder.domain.usecase.actions.TelecomSystem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActionsModule {

    @Binds
    @Singleton
    abstract fun bindForwardSmsAction(impl: ForwardSmsActionUseCase): ForwardSmsAction

    @Binds
    @Singleton
    abstract fun bindSetRingerLoudAction(impl: SetRingerLoudActionUseCase): SetRingerLoudAction

    @Binds
    @Singleton
    abstract fun bindPlaceCallAction(impl: PlaceCallActionUseCase): PlaceCallAction

    @Binds
    @Singleton
    abstract fun bindOpenMapsAction(impl: OpenMapsActionUseCase): OpenMapsAction

    @Binds
    @Singleton
    abstract fun bindRingerSystem(impl: AndroidRingerSystem): RingerSystem

    @Binds
    @Singleton
    abstract fun bindTelecomSystem(impl: AndroidTelecomSystem): TelecomSystem

    @Binds
    @Singleton
    abstract fun bindMapsNotifier(impl: AndroidMapsNotifier): MapsNotifier
}
