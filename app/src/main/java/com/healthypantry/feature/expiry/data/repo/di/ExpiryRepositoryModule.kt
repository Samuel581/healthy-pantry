package com.healthypantry.feature.expiry.data.repo.di

import com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository
import com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [ExpiryAlertRepository] to its production implementation (same pattern as `PantryRepositoryModule`). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExpiryRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExpiryAlertRepository(impl: ExpiryAlertRepositoryImpl): ExpiryAlertRepository
}
