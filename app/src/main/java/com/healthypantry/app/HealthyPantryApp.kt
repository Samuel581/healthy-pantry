package com.healthypantry.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the top-level DI container ([dagger.hilt.android.internal.managers.ApplicationComponentManager])
 * rooted here; feature modules request their dependencies via constructor injection.
 */
@HiltAndroidApp
class HealthyPantryApp : Application()
