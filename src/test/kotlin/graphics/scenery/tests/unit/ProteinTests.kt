package graphics.scenery.tests.unit

import graphics.scenery.proteins.Protein
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertFails

/**
 * Unit test for the Protein class
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
class ProteinTests {
    private val logger by LazyLogger()

    @Test
    fun testInvalidPath() {
        logger.info("Tests that an invalid path gets caught")
        assertFails { Protein.fromFile("LetsGetSchwifty") }
    }

    @Test
    fun testInvalidID() {
        logger.info("Test if an invalid pdb entry is caught")
        assertFails { Protein.fromID("3mbn") }
    }
}
