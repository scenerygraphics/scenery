package graphics.scenery.tests.unit

import graphics.scenery.PointLight
import graphics.scenery.Scene
import graphics.scenery.numerics.Random
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for [PointLight].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PointLightTests {
    @Test
    fun testCreation() {
        val size = Random.randomFromRange(1.0f, 500.0f)
        val pl = PointLight(size)

        assertEquals(size, pl.lightRadius)
    }
}
