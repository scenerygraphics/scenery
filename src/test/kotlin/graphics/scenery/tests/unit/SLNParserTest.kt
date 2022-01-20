package graphics.scenery.tests.unit

import graphics.scenery.proteins.chemistry.MoleculeTree
import graphics.scenery.proteins.chemistry.SLNParser
import graphics.scenery.utils.LazyLogger
import org.junit.Test

/**
 * Test the [SLNParser]
 */
class SLNParserTest {
    private val logger by LazyLogger()
    val slnParser = SLNParser()

    /**
     * Verifies that the sln parser returns the right value for propionic acid
     */
    @Test
    fun testPropionicAcid() {
        logger.info("Test Propionic Acid")
        val sln = "CH3CH2C(=O)OH"
        val c1 = MoleculeTree("C", 0)
        c1.addhydrogen(3)
        val c2 = MoleculeTree("C", 1)
        c2.addhydrogen(2)
        val c3 = MoleculeTree("C", 1)
        val o1 = MoleculeTree("O", 2)
        c3.addMolecule(o1)
        val o2 = MoleculeTree("O", 1)
        o2.addhydrogen(1)
        c3.addMolecule(o2)
        c2.addMolecule(c3)
        c1.addMolecule(c2)

    }


    /**
     * Tests that the right helperTree is created
     */
    @Test
    fun testHelperTree() {
        val sln =  "CH3CH2C(=O)OH"
        val helperTree = slnParser.helperTree(sln)
        printHelperTree(helperTree)

    }

    /**
     * Tests the string preperation
     */
    @Test
    fun stringPreperationTest() {
        val sln = "CH3CH2C(=O)OH"
        print(slnParser.prepareSLNString(sln))
    }

    fun printHelperTree(helperTree: SLNParser.HelperTree) {
        println(helperTree.element + helperTree.bondOrder)
        helperTree.children.forEach {
            printHelperTree(it)
        }
    }
}
