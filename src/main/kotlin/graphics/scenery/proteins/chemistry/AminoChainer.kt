package graphics.scenery.proteins.chemistry

import graphics.scenery.Icosphere
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.flythroughs.IUPACAbbreviationsReader
import graphics.scenery.numerics.Random
import graphics.scenery.proteins.Protein
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AminoChainer(val scene: Scene, proteinID: String = "", private val hmd: OpenVRHMD? = null, val controller: Spatial? = null): ClickBehaviour {
    var showAnimation = true
    private val protein = Protein.fromID(proteinID)
    private val structure = protein.structure
    private val chains = structure.chains
    val groups = chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }
    private val aminoAcidAbbreviations = ArrayList<String>(groups.size)
    val abbreviations = IUPACAbbreviationsReader().abbrevations
    private var aminoacidNumbersStored = 0
    var rootAA: MoleculeTree
    private var nextAaUP = true
    var currentCode: String

    init {
        groups.forEach { residue ->
            abbreviations.forEach {
                if(it.value.threeLetters == residue.pdbName) {
                    aminoAcidAbbreviations.add(it.value.threeLetters)
                }
            }
        }
        rootAA = AminoTreeList().aminoMap[aminoAcidAbbreviations.first()]!!
        rootAA.renameAminoAcidIds(aminoacidNumbersStored)
        val root = MoleculeMesh(rootAA)
        root.name = "poly$aminoacidNumbersStored"
        scene.addChild(root)
        currentCode = aminoAcidAbbreviations.first()
    }
    override fun click(x: Int, y: Int) {
        if(aminoacidNumbersStored < aminoAcidAbbreviations.size && aminoacidNumbersStored != 0) {
            thread {
                if(showAnimation) {
                    val previous = scene.getChildrenByName("poly${aminoacidNumbersStored - 1}").first()
                    val aaMesh = scene.getChildrenByName("aa$aminoacidNumbersStored").first()
                    aaMesh.findChildrenByNameRecursive("HN${aminoacidNumbersStored}Cyl")?.visible = false
                    previous.findChildrenByNameRecursive("OH${aminoacidNumbersStored - 1}Cyl")?.visible = false
                    previous.findChildrenByNameRecursive("HO${aminoacidNumbersStored - 1}Cyl")?.visible = false
                    //add new nodes for the water, using the "bounded" result in false positions
                    val hNode = aaMesh.findChildrenByNameRecursive("HN$aminoacidNumbersStored")
                    val oNode = previous.findChildrenByNameRecursive("OH${aminoacidNumbersStored - 1}")
                    val h2Node = previous.findChildrenByNameRecursive("HO${aminoacidNumbersStored - 1}")

                    //first oxygen
                    val oPos = Vector3f(oNode?.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val o = Icosphere(0.15f, 2)
                    o.material { diffuse = PeriodicTable().findElementByNumber(8).color!! }
                    o.spatial().position = oPos
                    o.name = "O"
                    scene.addChild(o)
                    oNode.visible = false
                    //then hydrogen number 1
                    val hPos = Vector3f(hNode?.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val h = Icosphere(0.05f, 2)
                    h.material { diffuse = PeriodicTable().findElementByNumber(1).color!! }
                    h.spatial().position = hPos
                    h.name = "HN"
                    scene.addChild(h)
                    hNode.visible = false
                    // and hydrogen number 2
                    val h2Pos = Vector3f(h2Node?.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val h2 = Icosphere(0.05f, 2)
                    h2.material { diffuse = PeriodicTable().findElementByNumber(1).color!! }
                    h2.spatial().position = h2Pos
                    h2.name = "HO"
                    scene.addChild(h2)
                    h2Node.visible = false

                    val waterDirection = Vector3f(hPos).sub(Vector3f(h2Pos)).normalize()

                    val randomVector = Vector3f(waterDirection).mul(1 / 1610f)
                    //let them wiggle around to show that they are going to be emitted
                    for (i in 0 until 1000) {
                        //Random.random3DVectorFromRange(-0.005f, 0.005f)
                        h.spatial().position = h.spatial().worldPosition() - randomVector
                        o.spatial().position = o.spatial().worldPosition() + randomVector
                        h2.spatial().position = h2.spatial().worldPosition() + randomVector
                        Thread.sleep(2)
                    }


                    //water molecule is emitted
                    val waterPosition =
                        Vector3f(o.spatialOrNull()!!.position).add(Vector3f(waterDirection).mul(0.2f))
                    o.spatialOrNull()!!.position = waterPosition
                    val down = Vector3f(scene.findObserver()!!.up).mul(-1f).normalize()
                    val side = Vector3f(down).cross(scene.findObserver()!!.forward).normalize()
                    val sin60 = sin(PI.toFloat() / 3f)
                    val cos60 = cos(PI.toFloat() / 3f)
                    val newHPos = Vector3f(waterPosition).add(
                        Vector3f(down).mul(cos60).add(Vector3f(side).mul(sin60)).normalize().mul(0.2f)
                    )
                    val newH2Pos = Vector3f(waterPosition).add(
                        Vector3f(down).mul(cos60).add(Vector3f(side).mul(-sin60)).normalize().mul(0.2f)
                    )
                    h.spatialOrNull()!!.position = newHPos
                    h2.spatialOrNull()!!.position = newH2Pos
                    Thread.sleep(300)
                    val randomVectorWiggle = Random.random3DVectorFromRange(-0.005f, 0.005f)
                    for (i in 0 until 1000) {
                        h.spatialOrNull()!!.position = newHPos + randomVectorWiggle
                        o.spatialOrNull()!!.position = waterPosition + randomVectorWiggle
                        h2.spatialOrNull()!!.position = newH2Pos + randomVectorWiggle
                        Thread.sleep(2)
                    }
                    scene.removeChild(h)
                    scene.removeChild(o)
                    scene.removeChild(h2)
                    val n = aaMesh.findChildrenByNameRecursive("N$aminoacidNumbersStored")
                    val nPos = Vector3f(n?.spatialOrNull()!!.position)
                    val c = previous.findChildrenByNameRecursive("C${aminoacidNumbersStored - 1}")
                    val cPos = Vector3f(c?.spatialOrNull()!!.position)

                    for (i in 0 until 1000) {
                        //n.spatialOrNull()!!.position = nPos + randomVectorWiggle
                        //c.spatialOrNull()!!.position = cPos + randomVectorWiggle
                        Thread.sleep(2)
                    }

                }
                val aminoTree =
                    AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                aminoTree!!.renameAminoAcidIds(aminoacidNumbersStored)
                rootAA.removeByID("OH" + "${aminoacidNumbersStored - 1}")
                nextAaUP = if (nextAaUP) {
                    //would break in case its Proline
                    if (aminoAcidAbbreviations[aminoacidNumbersStored] == "PRO") {
                        aminoTree.removeByID("HN$aminoacidNumbersStored")
                    } else {
                        aminoTree.removeByID("HNB$aminoacidNumbersStored")
                    }
                    !nextAaUP
                } else {
                    aminoTree.removeByID("HN$aminoacidNumbersStored")
                    !nextAaUP
                }

                rootAA.addAtID("C${aminoacidNumbersStored - 1}", aminoTree)
                val newRoot = rootAA
                val polypeptide = MoleculeMesh(newRoot)
                polypeptide.name = "poly${aminoacidNumbersStored}"
                scene.removeChild("aa$aminoacidNumbersStored")
                scene.removeChild("poly${aminoacidNumbersStored - 1}")
                scene.addChild(polypeptide)
                scene.update
                aminoacidNumbersStored += 1
                currentCode = aminoAcidAbbreviations[aminoacidNumbersStored]
                val poly2 = scene.find("poly${aminoacidNumbersStored-1}")!!
                val cPrev = poly2.findChildrenByNameRecursive("C" + "${aminoacidNumbersStored - 1}")!!
                val cPrevPos = Vector3f(cPrev.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                val ohPrev = poly2.findChildrenByNameRecursive("OH" + "${aminoacidNumbersStored - 1}")!!
                val ohPrevPos = Vector3f(ohPrev.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                val nextAAPos = Vector3f(ohPrevPos).add(Vector3f(ohPrevPos).sub(cPrevPos).normalize().mul(1.61f))
                val nextAA = AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                nextAA!!.renameAminoAcidIds(aminoacidNumbersStored)
                val nextAAMesh = MoleculeMesh(nextAA)
                nextAAMesh.name = "aa$aminoacidNumbersStored"
                nextAAMesh.spatial().position = nextAAPos
                scene.addChild(nextAAMesh)
                }
            }
            else {
                if (aminoacidNumbersStored == 0) {
                    aminoacidNumbersStored += 1
                    currentCode = aminoAcidAbbreviations[1]
                    val previous = scene.getChildrenByName("poly${aminoacidNumbersStored - 1}").first()
                    val nextAA = AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                    nextAA!!.renameAminoAcidIds(aminoacidNumbersStored)
                    val nextAAMesh = MoleculeMesh(nextAA)
                    nextAAMesh.name = "aa$aminoacidNumbersStored"
                    val cPrev = previous.findChildrenByNameRecursive("C" + "${aminoacidNumbersStored - 1}")
                        ?.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val ohPrev = previous.findChildrenByNameRecursive("OH" + "${aminoacidNumbersStored - 1}")
                        ?.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val nextAAPos = Vector3f(ohPrev).add(Vector3f(ohPrev).sub(cPrev).normalize().mul(1.61f))
                    nextAAMesh.spatial().position = nextAAPos
                    scene.addChild(nextAAMesh)
                }
            }
        }

    private fun Node.findChildrenByNameRecursive(name: String): Node? {
        val childrenToTravers = this.children
        var i = 0
        while(childrenToTravers.drop(i).isNotEmpty()) {
            if(childrenToTravers[i].name == name) {
                return childrenToTravers[i]
            }
            childrenToTravers.addAll(childrenToTravers[i].children)
            i += 1
        }
        return null
    }

    companion object {
        fun createAndSet(
            proteinID: String, scene: Scene, hmd: OpenVRHMD, button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    if (controllerSide.contains(device.role)) {
                        device.model?.let { controller ->
                            val proteinBuilder = AminoChainer(
                                scene, proteinID, hmd,
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial.")
                            )
                            val name = "aaForge"
                            hmd.addBehaviour(name, proteinBuilder)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
        }
    }
}
