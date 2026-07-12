package com.healthypantry.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Abstraction over [kotlinx.coroutines.Dispatchers] so data/domain layers depend on an
 * injectable interface instead of the global singleton dispatchers. Tests substitute a fake
 * implementation (e.g. backed by a single `TestDispatcher`) to run coroutines deterministically
 * without real threading.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
