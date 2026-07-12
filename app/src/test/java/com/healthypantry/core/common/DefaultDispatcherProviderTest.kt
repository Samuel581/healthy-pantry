package com.healthypantry.core.common

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DefaultDispatcherProvider] is a thin, deterministic mapping onto [kotlinx.coroutines.Dispatchers];
 * later data/domain layers depend on the [DispatcherProvider] abstraction (not [Dispatchers]
 * directly) so tests can substitute a `TestDispatcher` instead of real threading.
 */
class DefaultDispatcherProviderTest {

    private val provider: DispatcherProvider = DefaultDispatcherProvider()

    @Test
    fun `main dispatcher maps to Dispatchers Main`() {
        assertEquals(Dispatchers.Main, provider.main)
    }

    @Test
    fun `io dispatcher maps to Dispatchers IO`() {
        assertEquals(Dispatchers.IO, provider.io)
    }

    @Test
    fun `default dispatcher maps to Dispatchers Default`() {
        assertEquals(Dispatchers.Default, provider.default)
    }
}
