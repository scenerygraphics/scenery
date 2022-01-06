package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.primitives.Cylinder
import org.joml.Matrix3f
import org.joml.Vector3f
import java.util.*

/**
 * Class to compute the 3D structure of a circular molecule. We are using the simplest model, i.e., the ring is kept
 * flat.
 */
class CircularMolecularStructure(val root: BondTreeCycle, val initialAngle: Float, private val basis: Matrix3f, val bondLength: Float,
                                 positionalVector: Vector3f): Mesh("CircularMolecularStructure") {

    init {
        this.spatial().position = positionalVector

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
            val cosTheta = kotlin.math.cos(theta).toFloat()
            val sinTheta = kotlin.math.sin(theta).toFloat()
            var currentRoot = root
            var currentBasis = basis
            var currentPosition = positionalVector
            var i = 0
            while(cycle.drop(i).isNotEmpty()) {
                val x = Vector3f()
                currentBasis.getColumn(0, x)
                val y = Vector3f()
                currentBasis.getColumn(1, y)
                val z = Vector3f()
                currentBasis.getColumn(2, z)
                val newZ = if(i == 0) { Vector3f(0f, y.y*-sinTheta, z.z*cosTheta).normalize() }
                else {Vector3f(0f, y.y*sinTheta, z.z*cosTheta).normalize() }
                val newY = Vector3f(x).cross(newZ).normalize()
                val constituentPosition = Vector3f(currentPosition).add(Vector3f(newZ).mul(bondLength))
                currentBasis = Matrix3f(x, newY, newZ)

                //add the sphere
                val currentConstituent = cycle[i]
                val element = PeriodicTable().findElementBySymbol(currentConstituent.element)
                val elementSphere = if(element == PeriodicTable().findElementByNumber(1)) {
                    Icosphere(0.05f, 2) }
                else { Icosphere(0.15f, 2) }
                if (element.color != null) { elementSphere.ifMaterial { diffuse = element.color } }
                elementSphere.spatial().position = constituentPosition
                val c = Cylinder(0.025f, 1.0f, 10)
                c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
                c.spatial().orientBetweenPoints(currentPosition, elementSphere.spatial().position, true, true)
                this.addChild(c)
                currentPosition = constituentPosition
                i += 1
            }
            //last cylinder
            val c = Cylinder(0.025f, 1.0f, 10)
            c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
            c.spatial().orientBetweenPoints(this.spatial().position, currentPosition, true, true)
            this.addChild(c)
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
