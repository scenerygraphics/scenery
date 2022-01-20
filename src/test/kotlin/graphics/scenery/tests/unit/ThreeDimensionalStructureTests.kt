package graphics.scenery.tests.unit

import graphics.scenery.proteins.chemistry.MoleculeTree
import graphics.scenery.proteins.chemistry.MoleculeMesh
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
        val moleculeTree = MoleculeTree("H", 1)
        //hydrogen
        val outerElectronsHydrogen = MoleculeMesh(moleculeTree).countOuterElectronNumber(1)
        Assert.assertEquals(outerElectronsHydrogen.numberOfOuterElectrons, 1)
        // carbon has atom number 6
        val outerElectronsCarbon = MoleculeMesh(moleculeTree).countOuterElectronNumber(6)
        Assert.assertEquals(outerElectronsCarbon.numberOfOuterElectrons, 4)
        //nitrogen
        val outerElectronsNitrogen = MoleculeMesh(moleculeTree).countOuterElectronNumber(7)
        Assert.assertEquals(outerElectronsNitrogen.numberOfOuterElectrons, 5)
        //arsenic
        val outerElectronsArsenic = MoleculeMesh(moleculeTree).countOuterElectronNumber(33)
        Assert.assertEquals(outerElectronsArsenic.numberOfOuterElectrons, 5)
        Assert.assertEquals(outerElectronsArsenic.shellNumber, 4)
    }

    /**
     * Tests if the number of remaining electrons is returned correctly
     */
    @Test
    fun testFreeElectronPairs() {
        val water = MoleculeTree("O", 0)
        water.addhydrogen(2)
        Assert.assertEquals(MoleculeMesh(water).callPrivateFunc("numberOfFreeElectronPairs", water), 2)
        val ammonia = MoleculeTree("N", 0,)
        ammonia.addhydrogen(3)
        Assert.assertEquals(MoleculeMesh(ammonia).callPrivateFunc("numberOfFreeElectronPairs", ammonia), 1)
    }

    //Inline function to access private function in the RibbonDiagram
    private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
        T::class
            .declaredMemberFunctions
            .firstOrNull { it.name == name }
            ?.apply { isAccessible = true }
            ?.call(this, *args)
}
