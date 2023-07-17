package graphics.scenery.tests.unit

import graphics.scenery.Icosphere
import graphics.scenery.VolumeMeasurer
import graphics.scenery.primitives.Cylinder
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Very basic test for the volume measurer.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class VolumeMeasurerTests {

    /**
     * Tests the volume of a sphere against a precalculated volume
     */
    @Test
    fun testVolumeSphere() {
        val s = Icosphere(2f, 2)
        val volume = VolumeMeasurer().calculateVolume(s)
        assertEquals(volume, 32.376, absoluteTolerance = 0.001)
    }

    /**
     * Tests the volume of a cylinder against a precalculated volume
     */
    @Test
    fun testVolumeCylinder() {
        val c = Cylinder(2f, 10f, 5)
        val volume = VolumeMeasurer().calculateVolume(c)
        assertEquals(volume, 63.404, absoluteTolerance = 0.001)
    }
}
