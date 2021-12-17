package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.ShaderMaterial
import graphics.scenery.primitives.Cylinder
import graphics.scenery.utils.extensions.times
import org.joml.Matrix3f
import org.joml.Vector3f
import java.util.*
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
class ThreeDimensionalMolecularStructure(bondTree: BondTree, val lConfiguration: Boolean = true): Mesh("3DStructure") {
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
        val atomSphere = Icosphere(0.15f, 2)
        atomSphere.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
        if (rootElement.color != null) { atomSphere.ifMaterial { diffuse = rootElement.color } }
        this.addChild(atomSphere)
        var nodesToTravers = ArrayList<Pair<BondTree, Node>>().toMutableList()
        bondTree.children.forEach{ nodesToTravers.add(Pair(it, atomSphere)) }
        while(nodesToTravers.filter{ it.first.children.isNotEmpty()}.isNotEmpty()) {
            val newMolecule = nodesToTravers[0]
            nodesToTravers.removeAt(0)
            val newRoots = calculate3DStrucutre(newMolecule.first, newMolecule.second, yVector, bondLength)
            newMolecule.first.children.forEachIndexed { index, molecule ->
                nodesToTravers.add(Pair(molecule, newRoots[index]))
            }
        }
    }


    fun calculate3DStrucutre(bondTree:BondTree,root: Node, y: Vector3f, bondLength: Float): List<Node> {
        y.normalize()
        val z = if(root.parent?.spatialOrNull() == null) {
            Vector3f(0f, 0f, 1f)
        } else { Vector3f(root.parent?.spatialOrNull()?.worldPosition()?.sub(root.spatialOrNull()?.position)).normalize() }
        val x = y.cross(z)?.normalize()
        val rootPosition = root.spatialOrNull()?.position
        val numberOfFreeElectronPairs = numberOfFreeElectronPairs(bondTree)
        if(x != null && y != null && z != null && rootPosition != null) {
            val positions = when(bondTree.children.size + numberOfFreeElectronPairs) {
                0 -> { listOf() }
                1 -> { listOf(Vector3f(rootPosition).add(Vector3f(z).times(bondLength))) }
                2 -> { listOf(Vector3f(rootPosition).add(Vector3f(z).times(sin60*bondLength)).add(Vector3f(x).times(cos60*bondLength)),
                                Vector3f(rootPosition).add(Vector3f(z).times(sin60*bondLength)).sub(Vector3f(x).times(cos60*bondLength))) }
                3 -> {  val basis = invertedBasis(x,y,z)
                        val toTransformRotationVector = Vector3f(sin120, cos120, 0f)
                        val rotationVector = basis.transform(toTransformRotationVector)
                        listOf(Vector3f(rootPosition).add(Vector3f(z).times(sin70Point5)).add(Vector3f(x).times(cos70Point5)),
                                Vector3f(rootPosition).add(Vector3f(z).times(sin70Point5*bondLength)).add(Vector3f(rotationVector)
                                    .times(-cos70Point5*bondLength)),
                                Vector3f(rootPosition).add(Vector3f(z).times(sin70Point5*bondLength)).add(Vector3f(rotationVector)
                                    .times(cos70Point5*bondLength))) }
                4 -> {  val basis = invertedBasis(x,y,z)
                        val toTransformRotationVector = Vector3f(sin120, cos120, 0f)
                        val rotationVector = basis.transform(toTransformRotationVector)
                        listOf(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)),
                                Vector3f(rootPosition).add(Vector3f(y).times(bondLength)),
                                Vector3f(rootPosition).add(Vector3f(rotationVector).times(-cos70Point5*bondLength)),
                                Vector3f(rootPosition).add(Vector3f(rotationVector).times(cos70Point5*bondLength)))}
                5 -> { listOf(Vector3f(rootPosition).add(Vector3f(z).times(bondLength)),
                                Vector3f(rootPosition).add(Vector3f(y).times(bondLength)),
                                Vector3f(rootPosition).add(Vector3f(y).times(-bondLength)),
                                Vector3f(rootPosition).add(Vector3f(x).times(bondLength)),
                                Vector3f(rootPosition).add(Vector3f(x).times(-bondLength)))}
                else -> { logger.warn("Too many binding partners. There might be an error in the provided Molecule." +
                    "Please consider that scenery supports only organic molecules at the moment.")
                    listOf() }
            }
            val newNodes = ArrayList<Node>(bondTree.children.size + numberOfFreeElectronPairs)
            bondTree.children.forEachIndexed {index, boundMolecule ->
                val element = PeriodicTable().findElementBySymbol(boundMolecule.element)
                val atomSphere = Icosphere(0.15f, 2)
                atomSphere.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
                if (element.color != null) { atomSphere.ifMaterial { diffuse = element.color } }
                atomSphere.spatial().position = positions[index]
                root.addChild(atomSphere)
                newNodes.add(atomSphere)
                //display bond
                val c = Cylinder(0.025f, 1.0f, 10)
                c.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
                c.ifMaterial { diffuse = Vector3f(1.0f, 1.0f, 1.0f) }
                c.spatial().orientBetweenPoints(rootPosition, atomSphere.spatial().position)
                root.addChild(c)
            }
            return newNodes
        }
        else {
            logger.warn("Either the root or its parent became null. This should not happen.")
            return ArrayList()
        }
    }

    private fun numberOfFreeElectronPairs(bondTree: BondTree): Int {
        val rootElement = PeriodicTable().findElementBySymbol(bondTree.element)
        val outerElectronsAndShellNumber = countOuterElectronNumber(rootElement.atomicNumber)
        val numberOfBoundElectrons = bondTree.children.fold(0) { acc, next -> acc + next.bondOrder }
        return (outerElectronsAndShellNumber.numberOfOuterElectrons - numberOfBoundElectrons)/2
    }

    data class OuterElectronsAndShellNumber(val numberOfOuterElectrons: Int, val shellNumber: Int)
    /**
     * Calculate the number of electrons on the outermost shell of each element
     */
    private fun countOuterElectronNumber(atomNumber: Int): OuterElectronsAndShellNumber {
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
}
