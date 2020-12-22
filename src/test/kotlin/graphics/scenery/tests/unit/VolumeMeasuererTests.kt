package graphics.scenery.tests.unit

import graphics.scenery.Cylinder
import graphics.scenery.Icosphere
import graphics.scenery.VolumeMeasurer
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Very basic test for the volume measurer.
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class VolumeMeasurerTests {

    @Test
    fun testVolumeSphere() {
        val s = Icosphere(2f, 2)
        val volume = VolumeMeasurer().calculateVolume(s)
        assertEquals(volume, 32.376359045505524)
    }
    @Test
    fun testVolumeCylinder() {
        val c = Cylinder(2f, 10f, 5)
        val volume = VolumeMeasurer().calculateVolume(c)
        assertEquals(volume, 63.40377712249756)
    }
}
