package graphics.scenery.proteins.chemistry

import graphics.scenery.proteins.AminoAcid

/**
 * Computes and stores the 3D structure of a molecule according to the VSEPR model. We took the simplest versions and
 * use the following angles:
 * 2 regions of high electron density(bond or free electron pair): 180°
 * 3 regions of high electron density(bond or free electron pair): 120°
 * 4 regions of high electron density(bond or free electron pair): 109.5°
 * 5 regions of high electron density(bond or free electron pair): three regions planar with 120° between them, two regions perpendicular to the plane 90°
 * 6 regions of high electron density(bond or free electron pair): 90°
 *
 * Circular molecules, e.g., aromatics are displayed as planes, with all inner angles being of equal size.
 */
class ThreeDimensionalMolecularStructure(molecule: Molecule, val lConfiguration: Boolean = true) {

    init {
        //at the moment only amino acids are supported
        if(molecule is AminoAcid) {

        }
    }


    data class OuterElectronsAndShellNumber(val numberOfOuterElectrons: Int, val shellNumber: Int)
    /**
     * Calculate the number of electrons on the outermost shell of each element
     */
    fun countOuterElectronNumber(atomNumber: Int): OuterElectronsAndShellNumber {
        var n = 1
        var outerElectronNumber = atomNumber
        while(2*(n*n) < outerElectronNumber) {
            outerElectronNumber -= 2*(n*n)
            n += 1
        }
        return OuterElectronsAndShellNumber(outerElectronNumber, n)
    }
}
