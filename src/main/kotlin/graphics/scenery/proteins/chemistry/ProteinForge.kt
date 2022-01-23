package graphics.scenery.proteins.chemistry

import graphics.scenery.Box
import graphics.scenery.Icosphere
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.flythroughs.IUPACAbbreviationsReader
import graphics.scenery.flythroughs.ProteinBuilder
import graphics.scenery.numerics.Random
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.proteins.StickAndBallProteinModel
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AminoChainer(val scene: Scene, val proteinID: List<String> = listOf(""), private val hmd: OpenVRHMD? = null, val controller: Spatial? = null): ClickBehaviour {
    var showAnimation = true
    private var aminoAcidAbbreviations = ArrayList<String>()
    val abbreviations = IUPACAbbreviationsReader().abbrevations
    var aminoacidNumbersStored = 0
    private set
    private var rootAA = MoleculeTree("", 0)
    private var nextAaUP = true
    var currentCode = ""
    // all images of amino acids
    private val allImages = fillUpImageMap(abbreviations)
    private val camera = scene.activeObserver
    private var seenRes = 0
    private var nextProteinIndex = 0

    init {
        updateProtein()
    }

    private fun updateProtein() {
        if(proteinID.drop(nextProteinIndex).isEmpty()) {
            camera?.showMessage("No proteins left, congratulations!")
        }
        else {
            val protein = Protein.fromID(proteinID[nextProteinIndex])
            val structure = protein.structure
            val chains = structure.chains
            val groups = chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }
            aminoAcidAbbreviations = ArrayList<String>()
            groups.forEach { residue ->
                abbreviations.forEach {
                    if(it.value.threeLetters == residue.pdbName) {
                        aminoAcidAbbreviations.add(it.value.threeLetters)
                    }
                }
            }
            rootAA = AminoTreeList().aminoMap[aminoAcidAbbreviations.first()]!!
            rootAA.renameAminoAcidIds(0)
            val root = MoleculeMesh(rootAA)
            root.name = "poly0"
            scene.addChild(root)
            currentCode = aminoAcidAbbreviations.first()
            aminoacidNumbersStored = 0
            addAminoAcidPicture()
            seenRes = 0
            nextProteinIndex += 1
        }
    }
    override fun click(x: Int, y: Int) {
        if(proteinID.drop(nextProteinIndex).isEmpty()) {
            camera?.showMessage("No proteins left, congratulations!")
        }
        else {
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
                    val nitrogen  = aaMesh.findChildrenByNameRecursive("N$aminoacidNumbersStored")
                    val carbon = previous.findChildrenByNameRecursive("C${aminoacidNumbersStored - 1}")
                    val nitrogenPos = nitrogen!!.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val carbonPos = carbon!!.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val directionVector = Vector3f(carbonPos).sub(nitrogenPos).normalize()
                    val step = Vector3f(directionVector).mul(1/600f)
                    for (i in 0 until 1000) {
                        aaMesh.spatialOrNull()!!.position = Vector3f(aaMesh.spatialOrNull()!!.position) + step
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
                if(seenRes >= 3) {
                    removePrevious()
                    thread {
                        removePrevious()
                        camera?.showMessage("You correctly identified...", duration = 2500)
                        Thread.sleep(2500)
                        camera?.showMessage("10 residues!", duration = 2500)
                        val protein = Protein.fromID(proteinID[nextProteinIndex-1])
                        val stickAndBall = StickAndBallProteinModel(protein)
                        stickAndBall.spatial().scale = Vector3f(0.5f, 0.5f, 0.5f)
                        Thread.sleep(2500)
                        scene.addChild(stickAndBall)
                        camera?.showMessage("Those are all residues", duration = 5000)
                        Thread.sleep(5000)
                        camera?.showMessage("After they folded into a 3D structure.", duration = 5000)
                        Thread.sleep(20000)
                        camera?.showMessage("This is the structure's ribbon diagram", duration = 5000)
                        scene.removeChild(stickAndBall)
                        val ribbon = RibbonDiagram(protein)
                        scene.addChild(ribbon)
                        Thread.sleep(20000)
                        scene.removeChild(ribbon)
                        camera?.showMessage("To the next one!", duration = 3000)
                        Thread.sleep(3000)
                        updateProtein()
                    }
                }
                else {
                    addNextAminoAcid()
                    addAminoAcidPicture()
                    seenRes += 1
                }
                }
            }
            else {
                if (aminoacidNumbersStored == 0) {
                    aminoacidNumbersStored += 1
                    currentCode = aminoAcidAbbreviations[1]
                    addNextAminoAcid()
                    addAminoAcidPicture()
                    seenRes +=1
                }
            }
        }
    }

    private fun removePrevious() {
        //remove all previous children
        for(i in 0 .. aminoacidNumbersStored) {
            scene.removeChild("poly$i")
            scene.removeChild("box$i")
            scene.removeChild("aa$i")
            scene.update
        }
    }

    private fun addNextAminoAcid() {
        val poly = scene.find("poly${aminoacidNumbersStored-1}")!!
        poly.spatialOrNull()?.updateWorld(true)
        val cPrev = poly.findChildrenByNameRecursive("C" + "${aminoacidNumbersStored - 1}")!!
        val cPrevPos = Vector3f(cPrev.spatialOrNull()!!.world.getColumn(3, Vector3f()))
        val ohPrev = poly.findChildrenByNameRecursive("OH" + "${aminoacidNumbersStored - 1}")!!
        val ohPrevPos = Vector3f(ohPrev.spatialOrNull()!!.world.getColumn(3, Vector3f()))
        val nextAAPos = Vector3f(ohPrevPos).add(Vector3f(ohPrevPos).sub(cPrevPos).normalize().mul(1.61f))
        val nextAA = AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
        nextAA!!.renameAminoAcidIds(aminoacidNumbersStored)
        val nextAAMesh = MoleculeMesh(nextAA)
        nextAAMesh.name = "aa$aminoacidNumbersStored"
        nextAAMesh.spatial().position = nextAAPos
        scene.addChild(nextAAMesh)
    }

    private fun addAminoAcidPicture() {
        val aaImage = allImages[aminoAcidAbbreviations[aminoacidNumbersStored]]
        //update positions of old pictures
        for(i in 0 until aminoacidNumbersStored) {
            val previous = scene.find("box${i}")
            if (previous != null) {
                val poly = scene.find("poly${aminoacidNumbersStored-1}")!!
                val cPosition = Vector3f(
                    poly.findChildrenByNameRecursive("C${i}")!!
                        .spatialOrNull()!!.world.getColumn(3, Vector3f())
                )
                val oPosition = Vector3f(
                    poly.findChildrenByNameRecursive("O${i}")!!
                        .spatialOrNull()!!.world.getColumn(3, Vector3f())
                )
                val dir = Vector3f(oPosition).sub(cPosition).normalize()
                val newBoxPos = Vector3f(oPosition).add(Vector3f(dir).mul(0.625f))
                previous.update.add {
                    previous.spatialOrNull()!!.position = newBoxPos
                    if(i == aminoacidNumbersStored-1) {
                        val aaImage = allImages[aminoAcidAbbreviations[aminoacidNumbersStored-1]]
                        previous.spatialOrNull()!!.scale = Vector3f((aaImage!!.width/aaImage!!.height).toFloat()/3f, 1/3f, 0f)
                    }
                }
            }
        }

        //add the amino acid picture
        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "box$aminoacidNumbersStored"
        val height = aaImage?.height
        val width = aaImage?.width
        if (width != null && height != null) {
            box.spatial().scale = Vector3f((width / height.toFloat())/5f, 1/5f, 0f)
        }
        box.spatial {
            //VR mode, baby!
            if(hmd != null) {
                position = controller!!.worldPosition()
            }
            //nope? okay, lets go for 2D then
            else {
                rotation = Quaternionf(camera?.spatial()?.rotation).conjugate()
                position = Vector3f(camera?.spatial()?.position)
                val forwardTimesTwo = Vector3f()
                if (camera?.targeted == true) {
                    box.spatial().position.add(camera.target.mul(2f, forwardTimesTwo))
                } else {
                    box.spatial().position.add(camera?.forward?.mul(2f, forwardTimesTwo))
                }
            }
        }
        if(hmd != null) {
            box.update.add {
                box.spatial {
                    rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                    position = controller!!.worldPosition()
                }
            }
        }
        box.material {
            if (aaImage != null) {
                textures["diffuse"] = Texture.fromImage(aaImage)
            }
        }
        scene.addChild(box)
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
        fun fillUpImageMap(abbreviations: HashMap<String, IUPACAbbreviationsReader.IUPACAbbrevation>): HashMap<String, Image> {
            val images = HashMap<String, Image>(20)
            abbreviations.forEach { aminoAcid ->
                val chemicalCategory = aminoAcid.value.chemicalCategory.toString().lowercase()
                images[aminoAcid.key] = Image.fromResource("${chemicalCategory}/${aminoAcid.value.fullName}.png", ProteinBuilder::class.java)
            }
            return images
        }

        fun createAndSet(
            proteinID: List<String>, scene: Scene, hmd: OpenVRHMD, button: List<OpenVRHMD.OpenVRButton>,
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
