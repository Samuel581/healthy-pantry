package com.healthypantry.feature.expiry.notification.di

import com.healthypantry.feature.expiry.notification.ExpiryNotifier
import com.healthypantry.feature.expiry.notification.SystemExpiryNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [ExpiryNotifier] to its production implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExpiryNotifierModule {

    @Binds
    @Singleton
    abstract fun bindExpiryNotifier(impl: SystemExpiryNotifier): ExpiryNotifier
}
