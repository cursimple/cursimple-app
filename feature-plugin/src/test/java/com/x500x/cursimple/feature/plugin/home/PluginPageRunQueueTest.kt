package com.x500x.cursimple.feature.plugin.home

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginPageRunQueueTest {
    @Test
    fun concurrentRunsAreQueuedInsteadOfRejectedAsBusy() = runBlocking {
        val queue = PluginPageRunQueue()
        var active = 0
        var maximum = 0
        val jobs = (1..3).map { value ->
            async {
                queue.run {
                    active++
                    maximum = maxOf(maximum, active)
                    delay(10)
                    active--
                    value
                }
            }
        }
        assertEquals(listOf(1, 2, 3), jobs.map { it.await() })
        assertEquals(1, maximum)
    }
}
