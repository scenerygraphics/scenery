package graphics.scenery.tests.unit.utils

import graphics.scenery.Hub
import graphics.scenery.numerics.Random
import graphics.scenery.utils.Profiler
import graphics.scenery.utils.RemoteryProfiler
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Unit tests for [RemoteryProfiler] integration.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class RemoteryProfilerTests {
    /**
     * Tests standard remotery functionality.
     */
    @Test
    fun testRemotery() {
        val hub = Hub()
        val profiler = RemoteryProfiler(hub)
        hub.add(profiler)

        assertTrue { hub.get<Profiler>() == profiler }

        profiler.setThreadName("MainThread")

        for(it in 0..10) {
            profiler.begin("Task$it")

            profiler.begin("Task$it.Subtask1")
            Thread.sleep(Random.randomFromRange(1.0f, 10.0f).toLong())
            profiler.end()

            profiler.begin("Task$it.Subtask2")
            Thread.sleep(Random.randomFromRange(5.0f, 10.0f).toLong())
            profiler.end()

            profiler.end()
        }

        profiler.close()
    }
}
