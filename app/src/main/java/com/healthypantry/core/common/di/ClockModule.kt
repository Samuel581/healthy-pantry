package com.healthypantry.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides an injectable [Clock] so date-sensitive logic (e.g.
 * [com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository]'s "today" reference point)
 * can be substituted with [Clock.fixed] in tests instead of calling [java.time.LocalDate.now]
 * directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
