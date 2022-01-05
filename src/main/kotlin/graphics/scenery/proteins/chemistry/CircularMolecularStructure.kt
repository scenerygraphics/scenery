package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import org.joml.Matrix3f
import org.joml.Vector3f

/**
 * Class to compute the 3D structure of a circular molecule. We are using the simplest model, i.e., the ring is kept
 * flat.
 */
class CircularMolecularStructure(val root: BondTreeCycle, val initialAngle: Float, private val basis: Matrix3f, val bondLength: Float): Mesh("CircularMolecularStructure") {

    init {


        val allCycles = root.cyclesAndChildren.filter{it.size > 1}
        if(allCycles.isEmpty()) {
            logger.warn("Cicle without children")
        }
        //more than one circle attached to the root
        else if(allCycles.size > 1) {
            //TODO
        }
        else {
            val cycle = allCycles[0]
            //angle around which to turn each new z axis
            val theta = 2*kotlin.math.PI/cycle.size
            val cosTheta = kotlin.math.cos(theta)
            val sinTheta = kotlin.math.sin(theta)
            var currentRoot = root
            var currentBasis = basis
            var i = 0
            while(cycle.drop(i).size > 1) {
                val currentConstituent = cycle[i]
                val element = PeriodicTable().findElementBySymbol(currentConstituent.element)
                val elementSphere = if(element == PeriodicTable().findElementByNumber(1)) {
                    Icosphere(0.05f, 2) }
                else { Icosphere(0.15f, 2) }
                if (element.color != null) { elementSphere.ifMaterial { diffuse = element.color } }
                val x = Vector3f()
                currentBasis.getColumn(0, x)
                val y = Vector3f()
                currentBasis.getColumn(1, y)
                val z = Vector3f()
                currentBasis.getColumn(2, z)

                i += 1
            }
        }
    }

    /**
     * checks if there are more than one circles attached to the root.
     */
    fun moreThanOneCircle(): Boolean {
        return root.cyclesAndChildren.filter{it.size > 1}.size > 1
    }
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
