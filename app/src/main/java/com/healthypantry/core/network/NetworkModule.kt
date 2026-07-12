package com.healthypantry.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Base networking wiring shared by every remote data source. No API endpoints are declared
 * here — nutrition-lookup sources (Open Food Facts, USDA FoodData Central) are added on top of
 * this shared [OkHttpClient]/[Retrofit] in PR5 via `retrofit.newBuilder().baseUrl(...)`, since
 * each source has its own base URL.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PLACEHOLDER_BASE_URL = "https://healthypantry.invalid/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    /**
     * Unconfigured base [Retrofit] instance: shares [OkHttpClient] and the kotlinx-serialization
     * converter, but its [PLACEHOLDER_BASE_URL] is never called directly. Real sources call
     * `retrofit.newBuilder().baseUrl(realBaseUrl).build()` to inherit client/converter config.
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
