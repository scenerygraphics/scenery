package graphics.scenery.tests.unit.volumes

import graphics.scenery.volumes.Colormap
import org.joml.Vector4f
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for [Colormap] class.
 *
 * @author Deborah Schmidt
 */
class ColormapTests {

    @Test
    fun testGet() {
        val res = Colormap.get("grays")
        assertNotNull(res)
        assertEquals(256, res.width)
        var sample = res.sample(0f)
        val precision = 0.01f
        assertEquals(0f, sample[0], precision)
        assertEquals(0f, sample[1], precision)
        assertEquals(0f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
        sample = res.sample(0.5f)
        assertEquals(0.5f, sample[0], precision)
        assertEquals(0.5f, sample[1], precision)
        assertEquals(0.5f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
        sample = res.sample(0.9f)
        assertEquals(0.9f, sample[0], precision)
        assertEquals(0.9f, sample[1], precision)
        assertEquals(0.9f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
    }
    @Test
    fun testFromColor() {
        val res = Colormap.fromColor(Vector4f(255f, 0f, 0f, 255f))
        assertNotNull(res)
        assertEquals(256, res.width)
        var sample = res.sample(0f)
        val precision = 0.01f
        assertEquals(0f, sample[0], precision)
        assertEquals(0f, sample[1], precision)
        assertEquals(0f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
        sample = res.sample(0.5f)
        assertEquals(0.5f, sample[0], precision)
        assertEquals(0f, sample[1], precision)
        assertEquals(0f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
        sample = res.sample(0.9f)
        assertEquals(0.9f, sample[0], precision)
        assertEquals(0f, sample[1], precision)
        assertEquals(0f, sample[2], precision)
        assertEquals(1f, sample[3], precision)
    }

}
