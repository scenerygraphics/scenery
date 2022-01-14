package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.primitives.Cylinder
import org.joml.Matrix3f
import org.joml.Vector3f

/**
 * Class to compute the 3D structure of a circular molecule. We are using the simplest model, i.e., the ring is kept
 * flat.
 *
 * [root]  can be part of two circles. There are numerous geometric variants possible, e.g., the root being part of three
 * circles. However, in chemistry such molecules are unstable, hence, almost never displayed. [CyclicMolecularStructure]
 * does not cover those edge cases as it would make the code unnecessarily complicated.
 * In fact, we only consider one possible geometry which is a fairly common geometry in organic chemistry.
 * Namely, a root which is part of two circles in the following way:
 *     _     _
 *   /  \  /   \
 *       *
 *  |    |     |
 *  \ _ / \ _ /
 *
 * Those two egg shapes are supposed to represent circles (the rendering looks better) and the star * is to represent the
 * root position.
 */
class CyclicMolecularStructure(val root: BondTreeCycle, initialAngle: Float, basis: Matrix3f, private val bondLength: Float,
                               positionalVector: Vector3f): Mesh("CircularMolecularStructure") {

    private val vectorsPointingOutwards = ArrayList<Vector3f>(root.cyclesAndChildren.filter{ it.size > 1}.flatten().size)

    init {
        this.spatial().position = positionalVector

        //set initial vectors
        val initialX = basis.getColumn(0, Vector3f())
        val initialY = basis.getColumn(1, Vector3f())
        val initialZ = basis.getColumn(2, Vector3f())

        //turn the axes according to initial angle
        val sinInitial = kotlin.math.sin(initialAngle)
        val cosInitial = kotlin.math.cos(initialAngle)
        val x = Vector3f(initialX).mul(cosInitial).add(Vector3f(initialZ).mul(sinInitial)).normalize()
        //first z Vector pointing from root to center of the circle
        val intermediateZ = Vector3f(Vector3f(x).cross(Vector3f(initialY))).normalize()

        //add sphere for the root
        addAtomSphere(PeriodicTable().findElementBySymbol(this.root.element), this.spatial().position)

        //exclude all the children which are no circles and continue the calculation according to the number of circles
        val allCycles = root.cyclesAndChildren.filter{it.size > 1}
        if(allCycles.isEmpty()) {
            logger.warn("Circle without children")
        }
        //more than one circle attached to the root
        else if(allCycles.size > 1) {
            val firstPosition = Vector3f(initialY).mul(bondLength).add(this.spatial().position)
            //common element gets the first atomSphere
            allCycles[0].forEach { firstCycleElement ->
                allCycles[1].forEach { secondCycleElement ->
                    if(firstCycleElement == secondCycleElement) {
                        addAtomSphere(PeriodicTable().findElementBySymbol(firstCycleElement.element), firstPosition)
                    }
                }
            }
            //...and will be connected to the root
            addCylinder(this.spatial().position, firstPosition)
            //then add each cycle
            addSubCircle(listOf(allCycles[0]), this.spatial().position, firstPosition,
                Vector3f(initialY).mul(-1f).normalize())
            addSubCircle(listOf(allCycles[1]), this.spatial().position, firstPosition,
                Vector3f(initialY).mul(-1f).normalize(), true)
        }
        else {
            val cycle = allCycles[0]
            //angle around which to turn each new z axis
            val theta = 2 * kotlin.math.PI / (cycle.size + 1)
            val cosTheta = kotlin.math.cos(theta).toFloat()
            val sinTheta = kotlin.math.sin(theta).toFloat()

            //intial z and y vector need to be turned such that the circle is symmetric to the original z axis
            val alpha = (kotlin.math.PI - theta) / 2f
            val cosAlpha = kotlin.math.cos(alpha).toFloat()
            val sinAlpha = kotlin.math.sin(alpha).toFloat()
            val z = Vector3f(intermediateZ).mul(cosAlpha).add(Vector3f(initialY).mul(sinAlpha)).normalize()
            val y = Vector3f(x).cross(Vector3f(z)).normalize()

            //add vectors pointing out of the circle; bisectors of the respective corner
            val initialOutwardVector = Vector3f(intermediateZ).mul(-1f)
            val initialYOutward = Vector3f(initialOutwardVector).cross(x).normalize()
            cycle.dropLast(1).forEach { _ ->
                initialOutwardVector.set((Vector3f(initialYOutward).mul(cosTheta).add(Vector3f(y).mul(-sinTheta))).normalize())
                vectorsPointingOutwards.add(initialOutwardVector)
            }

            val lastPosition = circle(cycle, positionalVector, x,y,z, cosTheta, sinTheta)
            //last cylinder
            addCylinder(this.spatial().position, lastPosition)
        }
    }

    /**
     * Calculates the atom positions and adds children along the way, returns the last position
     */
    private fun circle(cycle: List<BondTree>, positionalVector: Vector3f, x: Vector3f, y: Vector3f, z: Vector3f,
                       cosTheta: Float, sinTheta: Float, changeDir: Boolean = false): Vector3f {
        var currentPosition = positionalVector
        cycle.forEachIndexed { index, currentSubstituent ->

            val substituentPosition = Vector3f(currentPosition).add(Vector3f(z).mul(bondLength))

            // verify the substituent is no subcycle
            if(currentSubstituent is BondTreeCycle) {
                addSubCircle(currentSubstituent.cyclesAndChildren, currentPosition, substituentPosition, x)
            }
            /*
            Add the next elements of the tree, i.e., all the molecules bound to the constituents of the respective
            circle.
             */
            addSubstituentChildren(x, currentSubstituent, substituentPosition, index)

            //add the sphere
            addAtomSphere(PeriodicTable().findElementBySymbol(currentSubstituent.element), substituentPosition)
            //add the cylinder
            addCylinder(substituentPosition, currentPosition)

            //change values for the next iteration
            currentPosition = substituentPosition
            val newZ = if(changeDir) { (Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(sinTheta))).normalize() }
            else { (Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(-sinTheta))).normalize() }
            z.set(newZ)
            y.set(Vector3f(x).cross(Vector3f(z))).normalize()
        }
        return currentPosition
    }

    /**
     * Adds the children of a substituent of the circle to the mesh
     */
    private fun addSubstituentChildren(x: Vector3f, substituent: BondTree, substituentPosition: Vector3f, index: Int) {
        val childrenOfConstituent = substituent.boundMolecules
        if (childrenOfConstituent.isNotEmpty()) {
            //bisector of z and y serves as the new z
            val newZ = vectorsPointingOutwards[index]
            val newY = Vector3f(x).cross(newZ).normalize()
            when (childrenOfConstituent.size) {
                1 -> {
                    val newPosition = Vector3f(newZ).mul(bondLength).add(Vector3f(substituentPosition))
                    val child = ThreeDimensionalMolecularStructure(
                        childrenOfConstituent[0],
                        initialBase = Matrix3f(x, newY, newZ)
                    )
                    child.spatial().position = newPosition
                    this.addChild(child)
                    //add cylinder to connect to new child
                    addCylinder(substituentPosition, newPosition)
                }
                2 -> {
                    //first new child
                    val firstNewPosition = Vector3f(x).mul(bondLength).add(Vector3f(substituentPosition))
                    val newBasis = Matrix3f(newZ, newY, x)
                    val firstChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[0], initialBase = newBasis)
                    firstChild.spatial().position = firstNewPosition
                    this.addChild(firstChild)
                    addCylinder(substituentPosition, firstNewPosition)
                    //second
                    val secondNewPosition = Vector3f(x).mul(-bondLength).add(Vector3f(substituentPosition))
                    val newBasis2 = Matrix3f(newZ, newZ, x)
                    val secondChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[1], initialBase = newBasis2)
                    secondChild.spatial().position = secondNewPosition
                    this.addChild(secondChild)
                    addCylinder(substituentPosition, secondNewPosition)
                }
                3 -> {
                    //first
                    val newPosition = Vector3f(newZ).mul(bondLength).add(Vector3f(substituentPosition))
                    val child = ThreeDimensionalMolecularStructure(
                        childrenOfConstituent[0],
                        initialBase = Matrix3f(x, newY, newZ)
                    )
                    child.spatial().position = newPosition
                    addCylinder(substituentPosition,newPosition)
                    this.addChild(child)
                    //second
                    val firstNewPosition = Vector3f(x).mul(bondLength).add(Vector3f(substituentPosition))
                    val newBasis = Matrix3f(newZ, newY, x)
                    val firstChild = ThreeDimensionalMolecularStructure(childrenOfConstituent[1], initialBase = newBasis)
                    firstChild.spatial().position = firstNewPosition
                    this.addChild(firstChild)
                    addCylinder(substituentPosition, firstNewPosition)
                    //third
                    val secondNewPosition = Vector3f(x).mul(-bondLength).add(Vector3f(substituentPosition))
                    val newBasis2 = Matrix3f(newZ, newZ, x)
                    val secondChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[2], initialBase = newBasis2)
                    secondChild.spatial().position = secondNewPosition
                    this.addChild(secondChild)
                    addCylinder(substituentPosition, secondNewPosition)
                }
                else -> {
                    logger.warn("Too many children for one element!")
                }
            }
        }
    }

    /**
     * Calculates the coordinates for a cycle attached to the original cycle and adds the respective Spheres and cylinders
     */
    private fun addSubCircle(cycles: List<List<BondTree>>, rootPosition: Vector3f, firstPoint: Vector3f, x: Vector3f,
                             changeDirection: Boolean = false) {
        if(cycles.filter{it.size > 1}.size == 1) {
            val cycle = cycles.filter { it.size > 1}[0]
            val theta = 2 * kotlin.math.PI / (cycle.size + 1)
            val cosTheta = kotlin.math.cos(theta).toFloat()
            val sinTheta = kotlin.math.sin(theta).toFloat()
            val z = Vector3f(firstPoint).sub(rootPosition).normalize()
            val y = Vector3f(z).cross(x).normalize()
            if(changeDirection) {
                z.set((Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(sinTheta))).normalize())
            }
            else {
                z.set((Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(sinTheta))).normalize())
            }
            y.set(Vector3f(z).cross(x).normalize())

            val lastPosition = if(changeDirection) {
                circle(cycle.drop(1), rootPosition, x, y, z, cosTheta, sinTheta, changeDir = true) }
                else { circle(cycle.drop(1), rootPosition, x, y, Vector3f(z).mul(-1f), cosTheta, sinTheta) }

            addCylinder(firstPoint, lastPosition)
        }
    }

    /**
     * creates a sphere, representing an atom of a certain element at a given position
     */
    private fun addAtomSphere(element: ChemicalElement, position: Vector3f) {
        //make smaller spheres for hydrogen
        val elementSphere = if (element == PeriodicTable().findElementByNumber(1)) {
            Icosphere(0.05f, 2)
        } else {
            Icosphere(0.15f, 2)
        }
        if (element.color != null) {
            elementSphere.ifMaterial { diffuse = element.color }
        }
        elementSphere.spatial().position = position
        this.addChild(elementSphere)
    }

    /**
     * Adds a cylinder, representing the covalent bond between to atoms
     */
    private fun addCylinder(atomPosition1: Vector3f, atomPosition2: Vector3f) {
        val c = Cylinder(0.025f, 1.0f, 10)
        c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
        c.spatial().orientBetweenPoints(atomPosition1, atomPosition2,rescale = true, reposition = true)
        this.addChild(c)
    }
}
