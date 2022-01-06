package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.primitives.Cylinder
import org.joml.Matrix3f
import org.joml.Vector3f

/**
 * Class to compute the 3D structure of a circular molecule. We are using the simplest model, i.e., the ring is kept
 * flat.
 */
class CircularMolecularStructure(val root: BondTreeCycle, initialAngle: Float, basis: Matrix3f, bondLength: Float,
                                 positionalVector: Vector3f): Mesh("CircularMolecularStructure") {

    init {
        this.spatial().position = positionalVector

        //change the basis according to initial angle
        val initialX = Vector3f()
        basis.getColumn(0, initialX)
        val initialY = Vector3f()
        basis.getColumn(1, initialY)
        val initialZ = Vector3f()
        basis.getColumn(2, initialZ)

        //turn the axes according to inital angle
        val sinInitial = kotlin.math.sin(initialAngle)
        val cosInitial = kotlin.math.cos(initialAngle)
        val x = Vector3f(initialX).mul(cosInitial).add(Vector3f(initialZ).mul(sinInitial)).normalize()
        val intermediateZ = Vector3f(Vector3f(x).cross(Vector3f(initialY))).normalize()


        val element = PeriodicTable().findElementBySymbol(root.element)
        val elementSphere = if(element == PeriodicTable().findElementByNumber(1)) {
            Icosphere(0.05f, 2) }
        else { Icosphere(0.15f, 2) }
        if (element.color != null) { elementSphere.ifMaterial { diffuse = element.color } }
        this.addChild(elementSphere)


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
            val theta = 2*kotlin.math.PI/(cycle.size+1)
            val cosTheta = kotlin.math.cos(theta).toFloat()
            val sinTheta = kotlin.math.sin(theta).toFloat()

            //intial z and y vector need to be turned such that the circle is symmetric to the original z axis
            val alpha = (kotlin.math.PI-theta)/2f
            val cosAlpha = kotlin.math.cos(alpha).toFloat()
            val sinAlpha = kotlin.math.sin(alpha).toFloat()
            val z = Vector3f(intermediateZ).mul(cosAlpha).add(Vector3f(initialY).mul(sinAlpha)).normalize()
            val y = Vector3f(x).cross(Vector3f(z)).normalize()

            var currentPosition = positionalVector
            var i = 0
            while(cycle.drop(i).isNotEmpty()) {

                val constituentPosition = Vector3f(currentPosition).add(Vector3f(z).mul(bondLength))

                //add the sphere
                val currentConstituent = cycle[i]
                val element = PeriodicTable().findElementBySymbol(currentConstituent.element)
                val elementSphere = if(element == PeriodicTable().findElementByNumber(1)) {
                    Icosphere(0.05f, 2) }
                else { Icosphere(0.15f, 2) }
                if (element.color != null) { elementSphere.ifMaterial { diffuse = element.color } }
                elementSphere.spatial().position = constituentPosition
                this.addChild(elementSphere)
                //add the cylinder
                val c = Cylinder(0.025f, 1.0f, 10)
                c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
                c.spatial().orientBetweenPoints(currentPosition, elementSphere.spatial().position, true, true)
                this.addChild(c)
                currentPosition = constituentPosition
                val newZ = (Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(-sinTheta))).normalize()
                z.set(newZ)
                y.set(Vector3f(x).cross(Vector3f(z))).normalize()
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
