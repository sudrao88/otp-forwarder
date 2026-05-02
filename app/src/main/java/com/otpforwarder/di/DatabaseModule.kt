package com.otpforwarder.di

import android.content.Context
import androidx.room.Room
import com.otpforwarder.data.local.AppDatabase
import com.otpforwarder.data.local.ForwardingRuleDao
import com.otpforwarder.data.local.ReceivedSmsDao
import com.otpforwarder.data.local.RecipientDao
import com.otpforwarder.data.local.migrations.MIGRATION_1_2
import com.otpforwarder.data.local.migrations.MIGRATION_2_3
import com.otpforwarder.data.local.migrations.MIGRATION_3_4
import com.otpforwarder.data.local.migrations.MIGRATION_4_5
import com.otpforwarder.data.local.migrations.MIGRATION_5_6
import com.otpforwarder.data.repository.ForwardingRuleRepositoryImpl
import com.otpforwarder.data.repository.ReceivedSmsRepositoryImpl
import com.otpforwarder.data.repository.RecipientRepositoryImpl
import com.otpforwarder.domain.repository.ForwardingRuleRepository
import com.otpforwarder.domain.repository.ReceivedSmsRepository
import com.otpforwarder.domain.repository.RecipientRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindRecipientRepository(impl: RecipientRepositoryImpl): RecipientRepository

    @Binds
    @Singleton
    abstract fun bindForwardingRuleRepository(impl: ForwardingRuleRepositoryImpl): ForwardingRuleRepository

    @Binds
    @Singleton
    abstract fun bindReceivedSmsRepository(impl: ReceivedSmsRepositoryImpl): ReceivedSmsRepository

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "otp_forwarder_db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()

        @Provides
        fun provideRecipientDao(database: AppDatabase): RecipientDao =
            database.recipientDao()

        @Provides
        fun provideForwardingRuleDao(database: AppDatabase): ForwardingRuleDao =
            database.forwardingRuleDao()

        @Provides
        fun provideReceivedSmsDao(database: AppDatabase): ReceivedSmsDao =
            database.receivedSmsDao()
    }
}
