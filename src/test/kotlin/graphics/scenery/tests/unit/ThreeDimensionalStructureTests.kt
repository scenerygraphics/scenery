package graphics.scenery.tests.unit

import graphics.scenery.proteins.chemistry.BondTree
import graphics.scenery.proteins.chemistry.ThreeDimensionalMolecularStructure
import org.biojava.nbio.structure.Bond
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible


/**
 * Tests for verifying that the 3D structure of a molecule is correctly computed
 */
class ThreeDimensionalStructureTests {

    /**
     * Check if the number of outer electrons is computed correctly
     */
    @Test
    fun testOuterElectrons() {
        val bondTree = BondTree("H", 1, null)
        //hydrogen
        val outerElectronsHydrogen = ThreeDimensionalMolecularStructure(bondTree).countOuterElectronNumber(1)
        Assert.assertEquals(outerElectronsHydrogen.numberOfOuterElectrons, 1)
        // carbon has atom number 6
        val outerElectronsCarbon = ThreeDimensionalMolecularStructure(bondTree).countOuterElectronNumber(6)
        Assert.assertEquals(outerElectronsCarbon.numberOfOuterElectrons, 4)
        //nitrogen
        val outerElectronsNitrogen = ThreeDimensionalMolecularStructure(bondTree).countOuterElectronNumber(7)
        Assert.assertEquals(outerElectronsNitrogen.numberOfOuterElectrons, 5)
        //arsenic
        val outerElectronsArsenic = ThreeDimensionalMolecularStructure(bondTree).countOuterElectronNumber(33)
        Assert.assertEquals(outerElectronsArsenic.numberOfOuterElectrons, 5)
        Assert.assertEquals(outerElectronsArsenic.shellNumber, 4)
    }

    /**
     * Tests if the number of remaining electrons is returned correctly
     */
    @Test
    fun testFreeElectronPairs() {
        val water = BondTree("O", 0, null)
        water.addhydrogen(2)
        Assert.assertEquals(ThreeDimensionalMolecularStructure(water).callPrivateFunc("numberOfFreeElectronPairs", water), 2)
        val ammonia = BondTree("N", 0, null)
        ammonia.addhydrogen(3)
        Assert.assertEquals(ThreeDimensionalMolecularStructure(ammonia).callPrivateFunc("numberOfFreeElectronPairs", ammonia), 1)
    }

    //Inline function to access private function in the RibbonDiagram
    private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
        T::class
            .declaredMemberFunctions
            .firstOrNull { it.name == name }
            ?.apply { isAccessible = true }
            ?.call(this, *args)
}
