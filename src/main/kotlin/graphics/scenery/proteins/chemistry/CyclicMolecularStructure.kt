package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.primitives.Arrow
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.minus
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

    //flips the order of singular hydrogens added to the circle
    var flipAddedHydrogens = true
    init {
        //set initial vectors
        val initialX = basis.getColumn(0, Vector3f())
        val initialY = basis.getColumn(1, Vector3f())
        val initialZ = basis.getColumn(2, Vector3f())

        //turn the axes according to initial angle
        val sinInitial = kotlin.math.sin(initialAngle)
        val cosInitial = kotlin.math.cos(initialAngle)
        //first z Vector pointing from root to center of the circle
        val intermediateZ  = Vector3f(initialY).mul(sinInitial).add(Vector3f(initialZ).mul(cosInitial)).normalize()

        val x = Vector3f(Vector3f(intermediateZ).cross(Vector3f(initialX))).normalize()

        //add substituents of the root
        addSubstituentChildren(Vector3f(x), root, positionalVector, Vector3f(intermediateZ).mul(-1f), false)
        //exclude all the children which are no circles and continue the calculation according to the number of circles
        val allCycles = root.cyclesAndChildren.filter{it.size > 1}
        if(allCycles.isEmpty()) {
            logger.warn("Circle without children")
        }
        //more than one circle attached to the root
        else if(allCycles.size > 1) {
            val firstPosition = Vector3f(initialX).mul(bondLength).add(this.spatial().position)
            //common element gets the first atomSphere
            allCycles[0].reversed().forEach { firstCycleElement ->
                allCycles[1].reversed().forEach { secondCycleElement ->
                    if(firstCycleElement == secondCycleElement) {
                        addAtomSphere(PeriodicTable().findElementBySymbol(firstCycleElement.element), firstPosition, firstCycleElement.id)
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
            val cycle = allCycles[0].reversed()
            //angle around which to turn each new z axis
            val theta = 2 * kotlin.math.PI / (cycle.size + 1)
            val cosTheta = kotlin.math.cos(theta).toFloat()
            val sinTheta = kotlin.math.sin(theta).toFloat()

            //intial z and y vector need to be turned such that the circle is symmetric to the original z axis
            val alpha = (kotlin.math.PI - theta) / 2f
            val cosAlpha = kotlin.math.cos(alpha).toFloat()
            val sinAlpha = kotlin.math.sin(alpha).toFloat()
            val z = Vector3f(intermediateZ).mul(cosAlpha).add(Vector3f(initialX).mul(sinAlpha)).normalize()
            val y = Vector3f(x).cross(Vector3f(z)).normalize()

            val lastPosition = circle(cycle, positionalVector, x,y,z, cosTheta, sinTheta, cosAlpha, sinAlpha, root.bondOrder)
            //last cylinder
            addCylinder(positionalVector, lastPosition, cycle.first().bondOrder, Vector3f(positionalVector).sub(lastPosition).cross(x).normalize())
        }
    }

    /**
     * Calculates the atom positions and adds children along the way, returns the last position
     */
    private fun circle(cycle: List<BondTree>, positionalVector: Vector3f, x: Vector3f, y: Vector3f, z: Vector3f,
                       cosTheta: Float, sinTheta: Float, cosAlpha: Float, sinAlpha: Float, rootBondOrder: Int = 1, changeDir: Boolean = false): Vector3f {
        var currentPosition = positionalVector
        cycle.forEachIndexed { index, currentSubstituent ->

            val substituentPosition = Vector3f(currentPosition).add(Vector3f(z).mul(bondLength))

            // verify the substituent is no subcycle
            if(currentSubstituent is BondTreeCycle) {
                addSubCircle(currentSubstituent.cyclesAndChildren, currentPosition, substituentPosition, x)
            }

            val outwardVector = (Vector3f(z).mul(cosAlpha).add(Vector3f(y).mul(sinAlpha))).normalize()

            /*
            Add the next elements of the tree, i.e., all the molecules bound to the constituents of the respective
            circle.
             */
            //flag to not use the outward vector if the substituent is the beginning of a new cycle
            val pointDown = if(index != cycle.lastIndex) { currentSubstituent is BondTreeCycle || cycle[index + 1] is BondTreeCycle }
            else { currentSubstituent is BondTreeCycle }
            addSubstituentChildren(x, currentSubstituent, substituentPosition, outwardVector, pointDown)

            //add the sphere
            addAtomSphere(PeriodicTable().findElementBySymbol(currentSubstituent.element), substituentPosition, currentSubstituent.id)
            //add the cylinder
            if(index != 0) {
                addCylinder(substituentPosition, currentPosition, cycle[index-1].bondOrder, y)
            }
            else {
                addCylinder(substituentPosition, currentPosition, rootBondOrder, y)
            }

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
    private fun addSubstituentChildren(x: Vector3f, substituent: BondTree, substituentPosition: Vector3f, outwardVector: Vector3f, pointDown: Boolean) {
        val childrenOfConstituent = substituent.boundMolecules
        if (childrenOfConstituent.isNotEmpty()) {
            //bisector of z and y serves as the new z
            val newZ = outwardVector.normalize()
            val newY = Vector3f(x).cross(newZ).normalize()
            val scalars = ArrayList<Float>(childrenOfConstituent.size)
            childrenOfConstituent.forEach {
               if(PeriodicTable().findElementBySymbol(it.element).atomicNumber == 1) {
                    scalars.add(bondLength/5f)
                }
                else {
                    scalars.add(bondLength)
                }
            }
            when (childrenOfConstituent.size) {
                1 -> {
                    val newPosition = if(!pointDown) { Vector3f(newZ).mul(scalars[0]).add(Vector3f(substituentPosition)) }
                    else { val directedBondlength = if(flipAddedHydrogens) { scalars[0] }
                        else { -scalars[0] }
                        flipAddedHydrogens = !flipAddedHydrogens
                        Vector3f(x).mul(directedBondlength).add(Vector3f(substituentPosition))
                    }
                    val child = ThreeDimensionalMolecularStructure(
                        childrenOfConstituent[0], true,
                        initialBase = Matrix3f(x, newY, newZ)
                    )
                    child.spatial().position = newPosition
                    this.addChild(child)
                    //add cylinder to connect to new child
                    addCylinder(substituentPosition, newPosition, childrenOfConstituent[0].bondOrder, newY)
                }
                2 -> {
                    //first new child
                    val firstNewPosition = Vector3f(x).mul(scalars[0]).add(Vector3f(substituentPosition))
                    val newBasis = Matrix3f(newY, newZ, x)
                    val firstChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[0], true, initialBase = newBasis)
                    firstChild.spatial().position = firstNewPosition
                    this.addChild(firstChild)
                    addCylinder(substituentPosition, firstNewPosition, childrenOfConstituent[0].bondOrder, newY)
                    //second
                    val secondNewPosition = Vector3f(x).mul(-scalars[1]).add(Vector3f(substituentPosition))
                    val newBasis2 = Matrix3f(newY, newZ, Vector3f(x).mul(-1f))
                    val secondChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[1], true, initialBase = newBasis2)
                    secondChild.spatial().position = secondNewPosition
                    this.addChild(secondChild)
                    addCylinder(substituentPosition, secondNewPosition, childrenOfConstituent[1].bondOrder, newY)
                }
                3 -> {
                    //first
                    val newPosition = Vector3f(newZ).mul(scalars[0]).add(Vector3f(substituentPosition))
                    val child = ThreeDimensionalMolecularStructure(
                        childrenOfConstituent[0],
                        true, initialBase = Matrix3f(x, newY, newZ)
                    )
                    child.spatial().position = newPosition
                    addCylinder(substituentPosition,newPosition, childrenOfConstituent[0].bondOrder, newY)
                    this.addChild(child)
                    //second
                    val firstNewPosition = Vector3f(x).mul(scalars[1]).add(Vector3f(substituentPosition))
                    val newBasis = Matrix3f(newZ, newY, x)
                    val firstChild = ThreeDimensionalMolecularStructure(childrenOfConstituent[1], true, initialBase = newBasis)
                    firstChild.spatial().position = firstNewPosition
                    this.addChild(firstChild)
                    addCylinder(substituentPosition, firstNewPosition, childrenOfConstituent[1].bondOrder, newY)
                    //third
                    val secondNewPosition = Vector3f(x).mul(-scalars[2]).add(Vector3f(substituentPosition))
                    val newBasis2 = Matrix3f(newZ, newZ, x)
                    val secondChild =
                        ThreeDimensionalMolecularStructure(childrenOfConstituent[2], true, initialBase = newBasis2)
                    secondChild.spatial().position = secondNewPosition
                    this.addChild(secondChild)
                    addCylinder(substituentPosition, secondNewPosition, childrenOfConstituent[2].bondOrder, newY)
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

            val alpha = (kotlin.math.PI - theta) / 2f
            val cosAlpha = kotlin.math.cos(alpha).toFloat()
            val sinAlpha = kotlin.math.sin(alpha).toFloat()
            val lastPosition = if(changeDirection) {
                circle(cycle.drop(1), rootPosition, x, y, z, cosTheta, sinTheta, cosAlpha, sinAlpha, cycle.first().bondOrder, changeDir = true) }
                else { circle(cycle.drop(1), rootPosition, x, y, Vector3f(z).mul(-1f), cosTheta, sinTheta, cosAlpha, sinAlpha, cycle.first().bondOrder) }

            addCylinder(firstPoint, lastPosition)
        }
    }

    /**
     * creates a sphere, representing an atom of a certain element at a given position
     */
    private fun addAtomSphere(element: ChemicalElement, position: Vector3f, id:String = "") {
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
        elementSphere.name = id
        this.addChild(elementSphere)
    }

    /**
     * Adds a cylinder, representing the covalent bond between to atoms
     */
    private fun addCylinder(atomPosition1: Vector3f, atomPosition2: Vector3f, bondOrder: Int = 1, perpendicular: Vector3f = Vector3f()) {
        if(bondOrder == 1) { val c = Cylinder(0.025f, 1.0f, 10)
        c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
        c.spatial().orientBetweenPoints(atomPosition1, atomPosition2,rescale = true, reposition = true)
        this.addChild(c) }
        else {
            val scalar = if(bondOrder == 2) { 0.05f } else { 0.08f }
            //translate both bonds so that the double bond becomes visible
            val positionRoot1 = Vector3f(atomPosition1).add(Vector3f(perpendicular).mul(scalar))
            val positionRoot2 = Vector3f(atomPosition1).sub(Vector3f(perpendicular).mul(scalar))
            val positionChild1 = Vector3f(atomPosition2).add(Vector3f(perpendicular).mul(scalar))
            val positionChild2 = Vector3f(atomPosition2).sub(Vector3f(perpendicular).mul(scalar))
            val c1 = Cylinder(0.025f, 1.0f, 10)
            c1.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
            c1.spatial().orientBetweenPoints(positionRoot1, positionChild1, true, true)
            this.addChild(c1)
            val c2 = Cylinder(0.025f, 1.0f, 10)
            c2.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
            c2.spatial().orientBetweenPoints(positionRoot2, positionChild2, true, true)
            this.addChild(c2)
            if(bondOrder == 3) {
                val c3 = Cylinder(0.025f, 1f, 10)
                c3.ifMaterial { diffuse = Vector3f(1f, 1f, 1f) }
                c3.spatial().orientBetweenPoints(atomPosition1, atomPosition2, true, true)
            }
        }
    }
}
