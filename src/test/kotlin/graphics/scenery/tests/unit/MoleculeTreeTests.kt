package graphics.scenery.tests.unit

import graphics.scenery.proteins.chemistry.AminoTreeList
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertNotEquals


class MoleculeTreeTests {
    private val logger by LazyLogger()

    /**
     * Tests whether an element of a certain id is correctly removed
     */
    @Test
    fun testID() {
        val proline = AminoTreeList().aminoMap["PRO"]
        val proline2 = AminoTreeList().aminoMap["PRO"]
        proline!!.removeByID("OH")
        assertNotEquals(proline.boundMolecules, proline2!!.boundMolecules)
    }
}
