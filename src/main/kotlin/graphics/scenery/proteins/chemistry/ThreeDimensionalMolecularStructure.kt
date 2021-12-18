package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.InstancedNode
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.times
import org.joml.Matrix3f
import org.joml.Vector3f
import kotlin.collections.ArrayList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


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
class ThreeDimensionalMolecularStructure(val bondTree: BondTree, val lConfiguration: Boolean = true): Mesh("3DStructure") {
    private val yVector = Vector3f(0f, 1f, 0f)
    private val bondLength = 1f
    private val sin60 = sqrt(3f)/2
    private val sin120 = sin60
    private val cos60 = 0.5f
    private val cos120 = -cos60
    private val seventyPointFiveRad = (70.5*kotlin.math.PI/180)
    private val sin70Point5 = sin(seventyPointFiveRad).toFloat()
    private val cos70Point5 = cos(seventyPointFiveRad).toFloat()
    init {
        val rootElement = PeriodicTable().findElementBySymbol(bondTree.element)
        val atomSphere = if(rootElement == PeriodicTable().findElementByNumber(1))
        { Icosphere(0.05f, 5) } else { Icosphere(0.15f, 2)}
        if (rootElement.color != null) { atomSphere.ifMaterial { diffuse = rootElement.color } }
        val nodesToTravers = ArrayList<Pair<BondTree, Node>>()
        nodesToTravers.add(Pair(bondTree, atomSphere))
        var i = 0
        while(nodesToTravers.drop(i).isNotEmpty()) {
            val newMolecule = nodesToTravers[i]
            val newRoots = calculate3DStructure(newMolecule.first, newMolecule.second, yVector)
            newRoots.forEach { nodesToTravers.add(it) }
            i += 1
        }
        this.addChild(atomSphere)
    }


    fun calculate3DStructure(bondTree:BondTree, root: Node, y: Vector3f): List<Pair<BondTree, Node>> {
        y.normalize()
        var treeRoot = false
        val rootPosition = Vector3f(root.spatialOrNull()?.worldPosition())
        val z = if(root.parent?.spatialOrNull() == null) {
            //if parent is null we are at the very root of our bond tree
            treeRoot = true
            //we cannot compute a z vector without a parent position
            Vector3f(0f, 0f, 1f)
        } else { Vector3f(rootPosition).sub(Vector3f(root.parent?.spatialOrNull()?.worldPosition())).normalize() }
        val x = Vector3f(y).cross(z)?.normalize()
        val numberOfFreeElectronPairs = numberOfFreeElectronPairs(bondTree)
        if(x != null && y != null && z != null && rootPosition != null) {
            val necessaryPositions =  bondTree.boundMolecules.size + numberOfFreeElectronPairs
            val positions = positions(necessaryPositions, x,y,z,rootPosition, treeRoot)
            val newNodes = ArrayList<Pair<BondTree, Node>>(bondTree.boundMolecules.size + numberOfFreeElectronPairs)
            bondTree.boundMolecules.forEachIndexed { index, boundMolecule ->
                val element = PeriodicTable().findElementBySymbol(boundMolecule.element)
                val atomSphere = if(element == PeriodicTable().findElementByNumber(1)) {
                    Icosphere(0.05f, 2) }
                    else { Icosphere(0.15f, 2) }
                if (element.color != null) { atomSphere.ifMaterial { diffuse = element.color } }
                this.addChild(atomSphere)
                atomSphere.spatial().position = positions[index]
                newNodes.add(Pair(boundMolecule, atomSphere))
                //display bond
                if(boundMolecule.bondOrder > 1) {
                    addMultipleBoundCylinder(rootPosition, Vector3f(atomSphere.spatial().position), boundMolecule.bondOrder)
                }
                else {
                    val c = Cylinder(0.025f, 1.0f, 10)
                    c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
                    c.spatial().orientBetweenPoints(rootPosition, atomSphere.spatial().position, true, true)
                    this.addChild(c)
                }
            }
            return newNodes
        }
        else {
            logger.warn("Either the root or its parent became null. This should not happen.")
            return ArrayList()
        }
    }

    /**
     * returns the positions of the atoms bounded to the respective molecule
     */
    fun positions(boundedMoleculesPlusFreeElectrons: Int, x: Vector3f, y:  Vector3f, z: Vector3f, rootPosition: Vector3f,
    treeRoot: Boolean): List<Vector3f> {
        val positions =  when (if(treeRoot) { boundedMoleculesPlusFreeElectrons-1 } else { boundedMoleculesPlusFreeElectrons }) {
            0 -> {
                listOf()
            }
            1 -> {
                listOf(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)))
            }
            2 -> {
                listOf(
                    Vector3f(rootPosition).add(Vector3f(z).times(cos60 * bondLength))
                        .add(Vector3f(x).times(sin60 * bondLength)),
                    Vector3f(rootPosition).add(Vector3f(z).times(cos60 * bondLength))
                        .sub(Vector3f(x).times(sin60 * bondLength))
                )
            }
            3 -> {
                val basis = invertedBasis(x, y, z)
                val toTransformVector1 = Vector3f(-cos60, sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector1 = basis.transform(toTransformVector1).normalize()
                val toTransformVector2 = Vector3f(-cos60, -sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector2 = basis.transform(toTransformVector2)
                listOf(
                    Vector3f(rootPosition).add(Vector3f(z).times(cos70Point5)).add(Vector3f(x).times(sin70Point5)),
                    Vector3f(rootPosition).add(Vector3f(z).times(cos70Point5 * bondLength)).add(
                        Vector3f(rotationVector1)),
                    Vector3f(rootPosition).add(Vector3f(z).times(cos70Point5 * bondLength)).add(
                        Vector3f(rotationVector2))
                )
            }
            4 -> {
                val basis = invertedBasis(x, y, z)
                val toTransformVector1 = Vector3f(-cos60, sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector1 = basis.transform(toTransformVector1).normalize()
                val toTransformVector2 = Vector3f(-cos60, -sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector2 = basis.transform(toTransformVector2)
                listOf(
                    Vector3f(rootPosition).add(Vector3f(z).times(bondLength)),
                    Vector3f(rootPosition).add(Vector3f(y).times(bondLength)),
                    Vector3f(rootPosition).add(Vector3f(rotationVector1).times(-cos70Point5 * bondLength)),
                    Vector3f(rootPosition).add(Vector3f(rotationVector2).times(cos70Point5 * bondLength))
                )
            }
            5 -> {
                listOf(
                    Vector3f(rootPosition).add(Vector3f(z).times(bondLength)),
                    Vector3f(rootPosition).add(Vector3f(y).times(bondLength)),
                    Vector3f(rootPosition).add(Vector3f(y).times(-bondLength)),
                    Vector3f(rootPosition).add(Vector3f(x).times(bondLength)),
                    Vector3f(rootPosition).add(Vector3f(x).times(-bondLength))
                )
            }
            else -> {
                logger.warn(
                    "Too many binding partners. There might be an error in the provided Molecule." +
                        "Please consider that scenery supports only organic molecules at the moment."
                )
                listOf()
            }
        }
        val allPositions = ArrayList<Vector3f>(boundedMoleculesPlusFreeElectrons)
        if(treeRoot) { allPositions.add(Vector3f(rootPosition).add(Vector3f(z).times(-bondLength))) }
        allPositions.addAll(positions)
        return allPositions
    }

    /**
     * calculates the remaining free electron pairs after all bonds are taking into account
     */
    private fun numberOfFreeElectronPairs(bondTree: BondTree): Int {
        val rootElement = PeriodicTable().findElementBySymbol(bondTree.element)
        val outerElectronsAndShellNumber = countOuterElectronNumber(rootElement.atomicNumber)
        val numberOfBoundElectrons = bondTree.boundMolecules.fold(0) { acc, next -> acc + next.bondOrder }
        return (outerElectronsAndShellNumber.numberOfOuterElectrons - numberOfBoundElectrons)/2
    }

    /**
     * Stores the number of electrons in the outer most shell and the total number of electron shells of an atom
     */
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

    private fun invertedBasis(x: Vector3f, y: Vector3f, z: Vector3f): Matrix3f {
        val inverse = Matrix3f(x,y,z).invert()
        val inverseX = inverse.getColumn(0, Vector3f()).normalize()
        val inverseY = inverse.getColumn(1, Vector3f()).normalize()
        val inverseZ = inverse.getColumn(2, Vector3f()).normalize()
        return Matrix3f(inverseX, inverseY, inverseZ)
    }

    private fun addMultipleBoundCylinder(rootPosition: Vector3f, childPosition: Vector3f, bondOrder: Int) {
        val scalar = if(bondOrder == 2) { 0.05f } else { 0.08f }
        //create vector perpendicular to bond cylinder
        val rootToChild = Vector3f(rootPosition).sub(Vector3f(childPosition)).normalize()
        val perpendicular = Vector3f()
        rootToChild.cross(Vector3f(1f, 0f, 0f), perpendicular).normalize().cross(Vector3f(0f, 1f, 0f))
            .normalize()
        //translate both bonds so that the double bond becomes visible
        val positionRoot1 = Vector3f(rootPosition).add(Vector3f(perpendicular).mul(scalar))
        val positionRoot2 = Vector3f(rootPosition).sub(Vector3f(perpendicular).mul(scalar))
        val positionChild1 = Vector3f(childPosition).add(Vector3f(perpendicular).mul(scalar))
        val positionChild2 = Vector3f(childPosition).sub(Vector3f(perpendicular).mul(scalar))
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
            c3.spatial().orientBetweenPoints(rootPosition, childPosition, true, true)
        }
    }
}
