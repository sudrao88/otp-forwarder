package com.otpforwarder.di

import android.content.Context
import androidx.room.Room
import com.otpforwarder.data.local.AppDatabase
import com.otpforwarder.data.local.ForwardingRuleDao
import com.otpforwarder.data.local.OtpLogDao
import com.otpforwarder.data.local.RecipientDao
import com.otpforwarder.data.local.migrations.MIGRATION_1_2
import com.otpforwarder.data.repository.ForwardingRuleRepositoryImpl
import com.otpforwarder.data.repository.OtpLogRepositoryImpl
import com.otpforwarder.data.repository.RecipientRepositoryImpl
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.OtpLogRepository
import com.otpforwarder.domain.repository.RecipientRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "otp_forwarder_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideRecipientDao(database: AppDatabase): RecipientDao =
        database.recipientDao()

    @Provides
    fun provideForwardingRuleDao(database: AppDatabase): ForwardingRuleDao =
        database.forwardingRuleDao()

    @Provides
    fun provideOtpLogDao(database: AppDatabase): OtpLogDao =
        database.otpLogDao()

    @Provides
    @Singleton
    fun provideRecipientRepository(recipientDao: RecipientDao): RecipientRepository =
        RecipientRepositoryImpl(recipientDao)

    @Provides
    @Singleton
    fun provideForwardingRuleRepository(
        database: AppDatabase,
        forwardingRuleDao: ForwardingRuleDao
    ): ForwardingRuleRepository =
        ForwardingRuleRepositoryImpl(database, forwardingRuleDao)

    @Provides
    @Singleton
    fun provideOtpLogRepository(otpLogDao: OtpLogDao): OtpLogRepository =
        OtpLogRepositoryImpl(otpLogDao)
}
