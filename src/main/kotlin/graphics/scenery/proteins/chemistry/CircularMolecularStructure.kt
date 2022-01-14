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
 * circles. However, in chemistry such molecules are unstable, hence, almost never displayed. This class does not cover
 * those edge cases, as it would make the code unnecessarily complicated.
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
class CircularMolecularStructure(val root: BondTreeCycle, initialAngle: Float, basis: Matrix3f, val bondLength: Float,
                                 positionalVector: Vector3f, comesFromACycle: Boolean = false): Mesh("CircularMolecularStructure") {

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
            //TODO
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

            val initialOutwardVector = Vector3f(intermediateZ).mul(-1f)
            val initialYOutward = Vector3f(initialOutwardVector).cross(x).normalize()
            cycle.dropLast(1).forEach { _ ->
                initialOutwardVector.set(
                    (Vector3f(initialYOutward).mul(cosTheta).add(Vector3f(y).mul(-sinTheta))).normalize()
                )
                vectorsPointingOutwards.add(initialOutwardVector)
            }

            var currentPosition = positionalVector
            var i = 0
            while (cycle.drop(i).isNotEmpty()) {

                val substituentPosition = Vector3f(currentPosition).add(Vector3f(z).mul(bondLength))

                val currentSubstituent = cycle[i]

                // add secondary cyclic molecule with a geometry like the on described at the top
                if (currentSubstituent is BondTreeCycle) {
                    this.addChild(CircularMolecularStructure(currentSubstituent, initialAngle,
                            Matrix3f(x, y, z), bondLength, substituentPosition, true))
                }
                /*
                Add the next elements of the tree, i.e., all the molecules bound to the constituents of the respective
                circle.
                 */
                addSubstituentChildren(x, currentSubstituent, substituentPosition, i)

                //add the sphere
                addAtomSphere(PeriodicTable().findElementBySymbol(currentSubstituent.element), substituentPosition)
                //add the cylinder
                addCylinder(substituentPosition, currentPosition)

                //change values for the next iteration
                currentPosition = substituentPosition
                val newZ = (Vector3f(z).mul(cosTheta).add(Vector3f(y).mul(-sinTheta))).normalize()
                z.set(newZ)
                y.set(Vector3f(x).cross(Vector3f(z))).normalize()
                i += 1
            }
            //last cylinder
            addCylinder(this.spatial().position, currentPosition)
        }
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
