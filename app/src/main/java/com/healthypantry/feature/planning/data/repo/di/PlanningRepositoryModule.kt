package com.healthypantry.feature.planning.data.repo.di

import com.healthypantry.feature.planning.data.repo.PlanEntryRepository
import com.healthypantry.feature.planning.data.repo.PlanEntryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the planning repository interface to its production implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlanningRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlanEntryRepository(impl: PlanEntryRepositoryImpl): PlanEntryRepository
}
