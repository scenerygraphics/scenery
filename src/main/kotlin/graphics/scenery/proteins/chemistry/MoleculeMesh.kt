package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
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
class MoleculeMesh(val moleculeTree: MoleculeTree, var comingFromCycle: Boolean = false,
                   val initialBase: Matrix3f = Matrix3f(1f, 0f, 0f,
                                                                            0f, 1f, 0f,
                                                                            0f, 0f, 1f)): Mesh("3DStructure") {

    private val bondLength = 1f
    private val sin60 = sqrt(3f)/2
    private val cos60 = 0.5f
    private val seventyPointFiveRad = (70.5*kotlin.math.PI/180)
    private val sin70Point5 = sin(seventyPointFiveRad).toFloat()
    private val cos70Point5 = cos(seventyPointFiveRad).toFloat()
    private val initialX = initialBase.getColumn(0, Vector3f())
    private val initialY = initialBase.getColumn(1, Vector3f())
    private val initialZ = initialBase.getColumn(2, Vector3f())
    init {
        this.name = moleculeTree.id
        val rootElement = PeriodicTable().findElementBySymbol(moleculeTree.element)
        val atomSphere = if(rootElement == PeriodicTable().findElementByNumber(1))
        { Icosphere(0.05f, 5) } else { Icosphere(0.15f, 2)}
        atomSphere.name = moleculeTree.id
        if (rootElement.color != null) { atomSphere.ifMaterial { diffuse = rootElement.color } }
        val nodesToTravers = ArrayList<BondTreeNodeBasisAndParent>()
        nodesToTravers.add(BondTreeNodeBasisAndParent(moleculeTree, atomSphere, initialX))
        var i = 0
        while (nodesToTravers.drop(i).isNotEmpty()) {
            val newMolecule = nodesToTravers[i]
            val newRoots = calculate3DStructure(newMolecule)
            newRoots.forEach { nodesToTravers.add(it) }
            i += 1
        }
        this.addChild(atomSphere)

    }

    data class BondTreeNodeBasisAndParent(val moleculeTree: MoleculeTree, val node: Node, val newX: Vector3f, val treeParent: Node? = null)

    private fun calculate3DStructure(bondTreeNodeBasis: BondTreeNodeBasisAndParent): List<BondTreeNodeBasisAndParent> {
        val bondTree = bondTreeNodeBasis.moleculeTree
        val root = bondTreeNodeBasis.node
        val x = bondTreeNodeBasis.newX
        var treeRoot = false
        val rootPosition = Vector3f(root.spatialOrNull()?.worldPosition())
        val z = if(root.parent?.spatialOrNull() == null) {
            //if parent is null we are at the very root of our bond tree
            treeRoot = true
            //we cannot compute a z vector without a parent position
            initialZ
        } else { Vector3f(rootPosition).sub(Vector3f(bondTreeNodeBasis.treeParent?.spatialOrNull()?.worldPosition())).normalize() }
        val y = if(root.parent?.spatialOrNull() == null) { initialY }
        else { Vector3f(x).cross(Vector3f(z)).normalize() }
        if(bondTree is MoleculeTreeCycle) {
            val initialAngle = 0f
            //kotlin.math.acos(Vector3f(positions[bondTree.cyclesAndChildren.size-numberOfFreeElectronPairs-1].position).sub(z).normalize().dot(z))
            val cycle = CyclicMoleculeMesh(bondTree, initialAngle, Matrix3f(x, y, z), bondLength)
            root.addChild(cycle)
            return listOf()
        }
        val numberOfFreeElectronPairs = numberOfFreeElectronPairs(bondTree)
        if(y != null && z != null) {
            val necessaryPositions = if(bondTree is MoleculeTreeCycle) { bondTree.cyclesAndChildren.size + numberOfFreeElectronPairs }
                                        else { bondTree.boundMolecules.size + numberOfFreeElectronPairs }
            val positions = positions(necessaryPositions, x, y, z, rootPosition, treeRoot)
            val newNodes = ArrayList<BondTreeNodeBasisAndParent>(bondTree.boundMolecules.size + numberOfFreeElectronPairs)
            bondTree.boundMolecules.forEachIndexed { index, boundMolecule ->
                val element = PeriodicTable().findElementBySymbol(boundMolecule.element)
                val elementSphere = if(element == PeriodicTable().findElementByNumber(1)) {
                    Icosphere( 0.05f, 2) }
                    else { Icosphere(0.15f, 2) }
                elementSphere.name = boundMolecule.id
                if (element.color != null) { elementSphere.ifMaterial { diffuse = element.color } }
                val computedPosition = if(comingFromCycle) { positions[index+1].position }
                else { positions[index].position }
                elementSphere.spatial {
                    position = if (element == PeriodicTable().findElementByNumber(1)) {
                        Vector3f(rootPosition).add(
                            Vector3f(computedPosition).sub(rootPosition).normalize().mul(bondLength / 5f)
                        )
                    } else {
                        computedPosition
                    }
                }
                this.addChild(elementSphere)

                newNodes.add(BondTreeNodeBasisAndParent(boundMolecule, elementSphere, positions[index].x, root))
                //display bond
                if(boundMolecule.bondOrder > 1 && boundMolecule !is MoleculeTreeCycle) {
                    addMultipleBoundCylinder(rootPosition, Vector3f(elementSphere.spatial().worldPosition()), boundMolecule.bondOrder, x, boundMolecule.id + "Cyl")
                }
                else {
                    val c = Cylinder(0.025f, 1.0f, 10)
                    c.name = boundMolecule.id + "Cyl"
                    c.ifMaterial {
                        diffuse = if((bondTree.id.contains("Ca") && boundMolecule.id.contains("N") ||
                                (bondTree.id.contains("N") && boundMolecule.id.contains("C"))
                                || (bondTree.id.contains("C") && boundMolecule.id.contains("N")) ||
                                (bondTree.id.contains("Ca") && boundMolecule.id.contains("C")))) {
                            Vector3f(0f, 0f, 0f)

                        } else {
                            Vector3f(1.0f, 1.0f, 1.0f)
                        }
                    }
                    c.spatial().orientBetweenPoints(rootPosition, elementSphere.spatial().worldPosition(), true, true)
                    this.addChild(c)
                }
            }
            //after the first iteration we certainly don't come out of a circle anymore
            comingFromCycle = false
            return newNodes
        }
        else {
            logger.warn("Either the root or its parent became null. This should not happen.")
            return ArrayList()
        }
    }

    data class PositionAndX(val position: Vector3f, val x: Vector3f)

    /**
     * returns the positions of the atoms bounded to the respective molecule
     */
    fun positions(boundedMoleculesPlusFreeElectrons: Int, x: Vector3f, y: Vector3f, z: Vector3f, rootPosition: Vector3f,
    treeRoot: Boolean): List<PositionAndX> {
        val positions =  when (if(treeRoot && !comingFromCycle) { boundedMoleculesPlusFreeElectrons-1 } else { boundedMoleculesPlusFreeElectrons }) {
            0 -> {
                listOf()
            }
            1 -> {
                listOf(PositionAndX(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)), x))
            }
            2 -> {
                val newX1 =  Vector3f(x).mul(cos60).add(Vector3f(z).mul(-sin60)).normalize()
                    //Vector3f(x.x*cos60, 0f, -z.z*sin60).normalize()
                val newX2 = Vector3f(x).mul(cos60).add(Vector3f(z).mul(sin60)).normalize()
                    //Vector3f(x.x*cos60, 0f, z.z*sin60).normalize()
                listOf(
                    PositionAndX(Vector3f(rootPosition).add((Vector3f(z).mul(cos60))
                        .add(Vector3f(x).mul(sin60)).normalize().mul(bondLength)), newX1),
                    PositionAndX(Vector3f(rootPosition).add((Vector3f(z).mul(cos60))
                        .sub(Vector3f(x).mul(sin60)).normalize().mul(bondLength)), newX2)
                )
            }
            3 -> {
                val basis = invertedBasis(x, y, z)
                val toTransformVector1 = Vector3f(-cos60, sin60, 0f)
                val rotationVector1 = basis.transform(toTransformVector1)
                val toTransformVector2 = Vector3f(-cos60, -sin60, 0f)
                val rotationVector2 = basis.transform(toTransformVector2)
                val newX1 = Vector3f(x).mul(cos70Point5).add(Vector3f(z).mul(sin70Point5)).normalize()
                    //Vector3f(x.x*cos70Point5, 0f, z.z*sin70Point5).normalize()
                val newX2 = Vector3f(x).mul(cos70Point5).add(Vector3f(z).mul(-sin70Point5)).normalize()
                //Vector3f(x.x*cos70Point5, 0f, -z.z*sin70Point5).normalize()
                listOf(
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(z).mul(cos70Point5*bondLength)).add(Vector3f(x).mul(sin70Point5*bondLength).normalize()), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(z).mul(cos70Point5 * bondLength)).add(
                        Vector3f(rotationVector1.normalize()).mul(sin70Point5 * bondLength)), Vector3f(newX2)),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(z).mul(cos70Point5 * bondLength)).add(
                        Vector3f(rotationVector2.normalize()).mul(sin70Point5 * bondLength)), Vector3f(newX1))
                )
            }
            4 -> {
                val basis = invertedBasis(x, y, z)
                val toTransformVector1 = Vector3f(-cos60, sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector1 = basis.transform(toTransformVector1).normalize()
                val toTransformVector2 = Vector3f(-cos60, -sin60, 0f).times(sin70Point5 * bondLength)
                val rotationVector2 = basis.transform(toTransformVector2)
                listOf(
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(y).times(bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(rotationVector1).times(-cos70Point5 * bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(rotationVector2).times(cos70Point5 * bondLength)), z)
                )
            }
            5 -> {
                listOf(
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(y).times(bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(y).times(-bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(x).times(bondLength)), z),
                    PositionAndX(Vector3f(rootPosition).add(Vector3f(x).times(-bondLength)), z)
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
        val allPositions = ArrayList<PositionAndX>(boundedMoleculesPlusFreeElectrons)
        if(treeRoot) { allPositions.add(PositionAndX(Vector3f(rootPosition).add(Vector3f(z).times(-bondLength)), x))}
        allPositions.addAll(positions)
        return allPositions
    }

    /**
     * calculates the remaining free electron pairs after all bonds are taking into account
     */
    private fun numberOfFreeElectronPairs(moleculeTree: MoleculeTree): Int {
        val rootElement = PeriodicTable().findElementBySymbol(moleculeTree.element)
        val outerElectronsAndShellNumber = countOuterElectronNumber(rootElement.atomicNumber)
        val bondsFromCycle = if(moleculeTree is MoleculeTreeCycle) {
            var number = 0
            moleculeTree.cyclesAndChildren.filter { it.size >1 }.forEach {
                number += moleculeTree.bondOrder
                number += it[it.lastIndex].bondOrder}
            number
        } else { 0 }
        val numberOfBoundElectrons = moleculeTree.boundMolecules.fold(0) { acc, next -> acc + next.bondOrder } + bondsFromCycle
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
        val inverse = Matrix3f(x,y,z).invert().transpose()
        val inverseX = inverse.getColumn(0, Vector3f()).normalize()
        val inverseY = inverse.getColumn(1, Vector3f()).normalize()
        val inverseZ = inverse.getColumn(2, Vector3f()).normalize()
        return Matrix3f(inverseX, inverseY, inverseZ)
    }

    private fun addMultipleBoundCylinder(rootPosition: Vector3f, childPosition: Vector3f, bondOrder: Int, perpendicular: Vector3f, id: String = "") {
        val scalar = if(bondOrder == 2) { 0.05f } else { 0.08f }
        //translate both bonds so that the double bond becomes visible
        val positionRoot1 = Vector3f(rootPosition).add(Vector3f(perpendicular).mul(scalar))
        val positionRoot2 = Vector3f(rootPosition).sub(Vector3f(perpendicular).mul(scalar))
        val positionChild1 = Vector3f(childPosition).add(Vector3f(perpendicular).mul(scalar))
        val positionChild2 = Vector3f(childPosition).sub(Vector3f(perpendicular).mul(scalar))
        val c1 = Cylinder(0.025f, 1.0f, 10)
        c1.name = id
        c1.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
        c1.spatial().orientBetweenPoints(positionRoot1, positionChild1, true, true)
        this.addChild(c1)
        val c2 = Cylinder(0.025f, 1.0f, 10)
        c2.name = id
        c2.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
        c2.spatial().orientBetweenPoints(positionRoot2, positionChild2, true, true)
        this.addChild(c2)
        if(bondOrder == 3) {
            val c3 = Cylinder(0.025f, 1f, 10)
            c3.name = id
            c3.ifMaterial { diffuse = Vector3f(1f, 1f, 1f) }
            c3.spatial().orientBetweenPoints(rootPosition, childPosition, true, true)
        }
    }
}
