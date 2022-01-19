package graphics.scenery.tests.examples.proteins

import org.joml.*
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.flythroughs.IUPACAbbreviationsReader
import graphics.scenery.proteins.Protein
import graphics.scenery.proteins.chemistry.*
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import kotlin.concurrent.thread

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class AminoAcidChainer: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {

    private val protein = Protein.fromID("3nir")
    private val structure = protein.structure
    private val chains = structure.chains
    val groups = chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }
    private val aminoAcidAbbreviations = ArrayList<String>(groups.size)
    val abbreviations = IUPACAbbreviationsReader().abbrevations
    private var aminoacidNumbersStored = 0
    lateinit var rootAA: MoleculeTree
    private var combineTwoAcids = false
    private var nextAaUP = false
    override fun init() {
        groups.forEach { residue ->
            abbreviations.forEach {
                if(it.value.threeLetters == residue.pdbName) {
                    aminoAcidAbbreviations.add(it.value.threeLetters)
                }
            }
        }
        rootAA = AminoTreeList().aminoMap[aminoAcidAbbreviations.first()]!!
        rootAA!!.renameAminoAcidIds(aminoacidNumbersStored)
        val root = MoleculeMesh(rootAA!!)
        root.name = "poly$aminoacidNumbersStored"
        scene.addChild(root)
        aminoacidNumbersStored += 1
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f

        val lightbox = Box(Vector3f(500.0f, 500.0f, 500.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material {
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            roughness = 1.0f
            metallic = 0.0f
            cullingMode = Material.CullingMode.None
        }
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 80.0f)
            l.spatial {
                position = Vector3f(
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(1.0f, 5.0f)
                )
            }
            l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }

        lights.forEach { lightbox.addChild(it) }

        val stageLight = PointLight(radius = 350.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 15f
        stageLight.spatial {
            position = Vector3f(0.0f, 0.0f, 5.0f)
        }
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 0.0f)
        cameraLight.intensity = 0.8f

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        cam.addChild(cameraLight)
    }

    override fun inputSetup() {
        val action: (Scene.RaycastResult, Int, Int) -> Unit = { _, _, _ ->
            if(aminoacidNumbersStored < aminoAcidAbbreviations.size) {
                if(!combineTwoAcids) {
                    val previous = scene.find("poly${aminoacidNumbersStored - 1}")
                    val cPrev = previous!!.findChildrenByNameRecursive("C" + "${aminoacidNumbersStored - 1}")?.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val ohPrev = previous.findChildrenByNameRecursive("OH" + "${aminoacidNumbersStored - 1}")?.spatialOrNull()!!.world.getColumn(3, Vector3f())
                    val nextAAPos = Vector3f(ohPrev).add(Vector3f(ohPrev).sub(cPrev).normalize().mul(1.61f))
                    val nextAminoBondTree =
                        AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                    nextAminoBondTree!!.renameAminoAcidIds(aminoacidNumbersStored)
                    val nextAA = MoleculeMesh(nextAminoBondTree)
                    nextAA.spatial().position = nextAAPos
                    nextAA.name = "aa$aminoacidNumbersStored"
                    scene.addChild(nextAA)
                    combineTwoAcids = !combineTwoAcids
                }
                else {
                    thread {
                        val nextAA = scene.find("aa$aminoacidNumbersStored")
                        val prevPolyPep = scene.find("poly${aminoacidNumbersStored - 1}")
                        nextAA?.findChildrenByNameRecursive("HN${aminoacidNumbersStored}Cyl")?.visible = false
                        prevPolyPep?.findChildrenByNameRecursive("OH${aminoacidNumbersStored - 1}Cyl")?.visible = false
                        prevPolyPep?.findChildrenByNameRecursive("HO${aminoacidNumbersStored - 1}Cyl")?.visible = false
                        //add new nodes for the water, using the "bounded" result in false positions
                        val hNode = nextAA?.findChildrenByNameRecursive("HN$aminoacidNumbersStored")
                        val oNode = prevPolyPep?.findChildrenByNameRecursive("OH${aminoacidNumbersStored - 1}")
                        val h2Node = prevPolyPep?.findChildrenByNameRecursive("HO${aminoacidNumbersStored - 1}")

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
                            //Thread.sleep(2)
                        }


                        //water molecule is emitted
                        val waterPosition =
                            Vector3f(o.spatialOrNull()!!.position).add(Vector3f(waterDirection).mul(0.2f))
                        o.spatialOrNull()!!.position = waterPosition
                        val down = Vector3f(scene.findObserver()!!.up).mul(-1f).normalize()
                        val side = Vector3f(down).cross(scene.findObserver()!!.forward).normalize()
                        val sin60 = kotlin.math.sin(kotlin.math.PI.toFloat() / 3f)
                        val cos60 = kotlin.math.cos(kotlin.math.PI.toFloat() / 3f)
                        val newHPos = Vector3f(waterPosition).add(
                            Vector3f(down).mul(cos60).add(Vector3f(side).mul(sin60)).normalize().mul(0.2f)
                        )
                        val newH2Pos = Vector3f(waterPosition).add(
                            Vector3f(down).mul(cos60).add(Vector3f(side).mul(-sin60)).normalize().mul(0.2f)
                        )
                        h.spatialOrNull()!!.position = newHPos
                        h2.spatialOrNull()!!.position = newH2Pos
                        Thread.sleep(300)
                        for (i in 0 until 1000) {
                            val randomVector = Random.random3DVectorFromRange(-0.005f, 0.005f)
                            h.spatialOrNull()!!.position = newHPos + randomVector
                            o.spatialOrNull()!!.position = waterPosition + randomVector
                            h2.spatialOrNull()!!.position = newH2Pos + randomVector
                           //Thread.sleep(2)
                        }
                        scene.removeChild(h)
                        scene.removeChild(o)
                        scene.removeChild(h2)
                        val n = nextAA.findChildrenByNameRecursive("N$aminoacidNumbersStored")
                        val nPos = Vector3f(n?.spatialOrNull()!!.position)
                        val c = prevPolyPep.findChildrenByNameRecursive("C${aminoacidNumbersStored - 1}")
                        val cPos = Vector3f(c?.spatialOrNull()!!.position)
                        for (i in 0 until 1000) {
                            val randomVector = Random.random3DVectorFromRange(-0.005f, 0.005f)
                            n.spatialOrNull()!!.position = nPos + randomVector
                            c.spatialOrNull()!!.position = cPos + randomVector
                            //Thread.sleep(2)
                        }
                        val nextAminoBondTree =
                            AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                        nextAminoBondTree!!.renameAminoAcidIds(aminoacidNumbersStored)
                        rootAA!!.removeByID("OH" + "${aminoacidNumbersStored - 1}")
                        nextAaUP = if (nextAaUP) {
                            //would break in case its Proline
                            if (nextAminoBondTree is MoleculeTreeCycle) {
                                nextAminoBondTree.removeByID("HN$aminoacidNumbersStored")
                            } else {
                                nextAminoBondTree.removeByID("HNB$aminoacidNumbersStored")
                            }
                            !nextAaUP
                        } else {
                            nextAminoBondTree.removeByID("HN$aminoacidNumbersStored")
                            !nextAaUP
                        }

                        rootAA!!.addAtID("C${aminoacidNumbersStored - 1}", nextAminoBondTree)
                        val newRoot = rootAA!!
                        val polypeptide = MoleculeMesh(newRoot)
                        polypeptide.name = "poly${aminoacidNumbersStored}"
                        if (aminoacidNumbersStored == 1) {
                            scene.removeChild("aa" + "${aminoacidNumbersStored - 1}")
                        }
                        scene.removeChild("aa$aminoacidNumbersStored")
                        scene.removeChild("poly${aminoacidNumbersStored - 1}")
                        scene.addChild(polypeptide)
                        aminoacidNumbersStored += 1
                        combineTwoAcids = !combineTwoAcids
                    }
                }
            }
            /*
            //rootAminoAcid
            val glutamineBondTree = AminoAcidBondTreeMap().aminoMap["GLN"]
            val tryptophaneBondTree = AminoAcidBondTreeMap().aminoMap["TRP"]
            val glutamine = ThreeDimensionalMolecularStructure(glutamineBondTree!!)
            glutamine.name = "gln"
            scene.addChild(glutamine)
            val n = glutamine.getChildrenByName("N").first().spatialOrNull()!!.position
            val hn = glutamine.getChildrenByName("HN").first().spatialOrNull()!!.position
            val position1 = Vector3f(hn).sub(n).normalize().mul(4f)
            val tryptophane = ThreeDimensionalMolecularStructure(tryptophaneBondTree!!)
            tryptophane.name = "trp"
            tryptophane.spatial().position = position1
            scene.addChild(tryptophane)


            if(glutamineBondTree != null && tryptophaneBondTree != null) {
                glutamineBondTree.removeByID("HN")
                tryptophaneBondTree.removeByID("OH")
                glutamineBondTree.addAtID("N", tryptophaneBondTree)
                val dipeptide = ThreeDimensionalMolecularStructure(glutamineBondTree)
                dipeptide.name = "dip"
                dipeptide.visible = false
                scene.addChild(dipeptide)
            }

             */

        }
        renderer?.let { r ->
            inputHandler?.addBehaviour(
                "select", SelectCommand(
                    "select", r, scene,
                    { scene.findObserver() }, action = action , debugRaycast = false
                )
            )
            inputHandler?.addKeyBinding("select", "K")
        }
        super.inputSetup()
        setupCameraModeSwitching()
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
        @JvmStatic
        fun main(args: Array<String>) {
            AminoAcidChainer().main()
        }
    }
}


