package com.healthypantry

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Android instrumented-test runner (`./gradlew connectedDebugAndroidTest`) is wired
 * correctly before any feature code exists: it resolves the real target application context.
 * Real Compose UI tests start in PR6 (feature/pantry/ui).
 */
@RunWith(AndroidJUnit4::class)
class HarnessInstrumentedSmokeTest {

    @Test
    fun instrumentedRunnerResolvesTargetAppPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.healthypantry", context.packageName)
    }
}
