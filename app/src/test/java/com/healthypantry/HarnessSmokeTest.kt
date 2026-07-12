package com.healthypantry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the JVM unit-test runner (`./gradlew test`) is wired correctly before any
 * feature code exists. Real feature TDD starts in PR2 (core/unit — UnitConverter).
 */
class HarnessSmokeTest {

    @Test
    fun `gradle jvm unit test runner executes and evaluates real arithmetic`() {
        assertEquals(4, 2 + 2)
    }
}
