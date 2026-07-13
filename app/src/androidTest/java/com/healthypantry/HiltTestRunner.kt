package com.healthypantry

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps the real [com.healthypantry.app.HealthyPantryApp] for [HiltTestApplication] so
 * `@HiltAndroidTest` instrumented tests (e.g. [AppNavigationSmokeTest]) get a fresh Hilt
 * component per test, without also triggering the real app's `onCreate` side effects
 * (notification channel creation, periodic `ExpiryCheckWorker` scheduling) that a real
 * `Application.onCreate()` would otherwise run before Hilt injection is even set up for the test.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
