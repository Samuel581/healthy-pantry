package com.healthypantry.feature.recipes.data.repo.di

import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.data.repo.RecipeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the recipes repository interface to its production implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecipeRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository
}
