package com.otpforwarder.di

import com.otpforwarder.domain.usecase.actions.ForwardSmsAction
import com.otpforwarder.domain.usecase.actions.ForwardSmsActionUseCase
import com.otpforwarder.domain.usecase.actions.PlaceCallAction
import com.otpforwarder.domain.usecase.actions.PlaceCallActionUseCase
import com.otpforwarder.domain.usecase.actions.SetRingerLoudAction
import com.otpforwarder.domain.usecase.actions.SetRingerLoudActionUseCase
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
}
