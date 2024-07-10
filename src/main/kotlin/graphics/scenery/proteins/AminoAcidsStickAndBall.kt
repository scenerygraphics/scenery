package graphics.scenery.proteins

import graphics.scenery.*
import graphics.scenery.primitives.Cylinder
import graphics.scenery.proteins.RibbonDiagram.Companion.getVector
import org.biojava.nbio.structure.Bond
import org.biojava.nbio.structure.BondImpl
import org.biojava.nbio.structure.Element
import org.joml.Vector3f

/**
 * This class allows for rendering of individual amino acids. This is different from a normal ball and stick representation
 * where we could use instancing for all the bonds and elements.
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class AminoAcidsStickAndBall(val protein: Protein, displayExternalMolecules: Boolean = false): Mesh("AminoAcids") {
    val structure = protein.structure
    private val residues = structure.chains.flatMap { it.atomGroups }.filter { it.hasAminoAtoms() }
    //calculate the centroid of the protein
    private val centroid = Axis.getCentroid(residues.flatMap { it.atoms }.filter{it.name == "CA"}.map{it.getVector()})
    companion object PerTab {
        private val periodicTable = PeriodicTable()
        private val aminoList = AminoList() }
    init {
        residues.forEachIndexed { index, group ->
            val aminoAcidMesh = Mesh("${group.pdbName} at $index")
            //display all atoms
            val atomMasters = HashMap<Element, Node>()
            group.atoms.distinctBy { it.element }.map { it.element }
                .forEach {
                    val sceneryElement = periodicTable.findElementByNumber(it.atomicNumber)
                    val s = Icosphere(0.15f, 2)
                    s.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
                    if (sceneryElement.color != null) {
                        s.ifMaterial { diffuse = sceneryElement.color }
                        //s.material.ambient = element.color
                        //s.material.specular = element.color
                    }
                    atomMasters[it] = s
                }
            group.atoms.filter {
                if (displayExternalMolecules) {
                    true
                } else {
                    it.group.hasAminoAtoms()
                }
            }.forEach {
                val element = periodicTable.findElementByNumber(it.element.atomicNumber)
                val master = atomMasters[it.element]
                val masterInstancedNode = InstancedNode(master!!)
                val s = masterInstancedNode.addInstance()
                val position = Vector3f()
                it.getVector().sub(centroid, position)
                spatial().position = position
                if (element.color != null) {
                    s.ifMaterial {  diffuse = element.color }
                    //s.material.ambient = element.color
                    //s.material.specular = element.color
                }
            }

            atomMasters.filter { InstancedNode(it.value).instances.isNotEmpty() }
                .forEach { aminoAcidMesh.addChild(it.value) }
            //get all bonds
            val storedAAList = aminoList.filter { it.name == group.pdbName }
            if(storedAAList.isNotEmpty()) {
                val storedAA = storedAAList[0]
                val bonds = ArrayList<Bond>(storedAA.bonds.size)
                //TODO: reduce complexity, has to be possible
                storedAA.bonds.forEach { triple ->
                    group.atoms.forEach { atom1 ->
                        group.atoms.forEach { atom2 ->
                            if ((atom1.name + "'") == triple.first && (atom2.name + "'") == triple.second) {
                                val bond = BondImpl(atom1, atom2, triple.third)
                                bonds.add(bond)
                            }
                        }
                    }
                }
                //display bonds
                val c = Cylinder(0.025f, 1.0f, 10)
                c.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag"))
                c.ifMaterial {
                    diffuse = Vector3f(1.0f, 1.0f, 1.0f)
                }
                val cInstancedNode = InstancedNode(c)
                //stores second bond of double bonds
                bonds.forEach {
                    val bondInstanced = cInstancedNode.addInstance()
                    bondInstanced.parent = this
                    val atomA = it.atomA
                    val atomB = it.atomB
                    val positionA = Vector3f()
                    atomA.getVector().sub(centroid, positionA)
                    val positionB = Vector3f()
                    atomB.getVector().sub(centroid, positionB)
                    //create double bonds
                    if(it.bondOrder == 2) {
                        //create vector perpendicular to bond cylinder
                        val aTob = Vector3f()
                        positionB.sub(positionA, aTob).normalize()
                        val perpendicular = Vector3f()
                        aTob.cross(Vector3f(1f, 0f, 0f), perpendicular).normalize().cross(Vector3f(0f, 1f, 0f)).normalize()

                        //translate both bonds so that the double bond becomes visible
                        val positionA1 = Vector3f()
                        positionA.sub(perpendicular.mul(0.05f, positionA1), positionA1)
                        val positionB1 = Vector3f()
                        positionB.sub(perpendicular.mul(0.05f, positionB1), positionB1)
                        spatial().orientBetweenPoints(positionA1, positionB1, true, true)
                        val bond2 = cInstancedNode.addInstance()
                        bond2.parent = InstancedNode(c)
                        val positionA2 = Vector3f()
                        positionA.add(perpendicular.mul(0.05f, positionA2), positionA2)
                        val positionB2 = Vector3f()
                        positionB.add(perpendicular.mul(0.05f, positionB2), positionB2)
                        spatial().orientBetweenPoints(positionA2, positionB2, true, true)
                    }
                    else {
                        spatial().orientBetweenPoints(positionA, positionB, true, true)
                    }
                }
                aminoAcidMesh.addChild(c)
            }
            else {
                logger.info("This amino acid is not stored: ${group.pdbName}")
            }
            // sidechains for ribbons are not visible by default
            aminoAcidMesh.visible = false
            this.addChild(aminoAcidMesh)
        }

    }
}
