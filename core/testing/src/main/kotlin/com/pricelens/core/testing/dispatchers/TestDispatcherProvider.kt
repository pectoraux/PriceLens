package com.pricelens.core.testing.dispatchers

import com.pricelens.core.common.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * [DispatcherProvider] implementation for tests that uses [UnconfinedTestDispatcher]
 * for all dispatchers to ensure deterministic execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider : DispatcherProvider {
    val testDispatcher = UnconfinedTestDispatcher()
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
