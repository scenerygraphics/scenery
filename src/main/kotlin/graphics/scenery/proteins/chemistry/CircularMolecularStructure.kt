package graphics.scenery.proteins.chemistry

import graphics.scenery.Mesh

/**
 * Class to compute the 3D structure of a circular molecule. We are using the simplest model, i.e., the ring is kept
 * flat.
 */
class CircularMolecularStructure(val root: BondTreeCycle, val initialAngle: Float): Mesh("CircularMolecularStructure") {


    /**
     * A root can be part of two circles. This leaves us (at least*) two possibilities: either the root is a singular root
     * or there is another node which could also function as a root.
     * one root:
     *      \ /
     *      / \
     * two roots:
     *      \ /
     *       |
     *      / \
     *
     * *we don't consider other possibilities than those described above as they are rare and not necessary at this stage
     */
    fun twoRoots(): Boolean {
        //are there even two circles to consider?
        return if(root.cyclesAndChildren.filter { it.size > 1 }.size > 1) {
            //yes, well, then check if they have a common root other than the original one
            val allChildren = root.cyclesAndChildren.filter { it.size > 1 }.flatMap{it}
            allChildren.distinct().size != allChildren.size
        } else {
            false
        }
    }
}
