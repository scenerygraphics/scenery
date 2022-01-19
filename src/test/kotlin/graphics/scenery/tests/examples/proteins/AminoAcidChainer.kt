package graphics.scenery.tests.examples.proteins


import org.joml.*
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.proteins.chemistry.AminoTreeList
import graphics.scenery.proteins.chemistry.MoleculeTreeCycle
import graphics.scenery.proteins.chemistry.MoleculeMesh

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class AminoAcidChainer: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {

    private val aminoAcidAbbreviations = listOf("ALA", "PRO", "ALA", "TYR", "ASP", "ARG", "THR")
    private var aminoacidNumbersStored = 0
    private var rootAA = AminoTreeList().aminoMap[aminoAcidAbbreviations.first()]
    private var combineTwoAcids = false
    private var nextAaUP = false
    override fun init() {
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
                    val nextAminoBondTree =
                        AminoTreeList().aminoMap[aminoAcidAbbreviations[aminoacidNumbersStored]]
                    nextAminoBondTree!!.renameAminoAcidIds(aminoacidNumbersStored)
                    rootAA!!.removeByID("OH" + "${aminoacidNumbersStored-1}")
                    nextAaUP = if(nextAaUP) {
                        //would break in case its Proline
                        if(nextAminoBondTree is MoleculeTreeCycle) {
                            nextAminoBondTree.removeByID("HN$aminoacidNumbersStored")
                        }
                        else {
                            nextAminoBondTree.removeByID("HNB$aminoacidNumbersStored")
                        }
                        !nextAaUP
                    } else {
                        nextAminoBondTree.removeByID("HN$aminoacidNumbersStored")
                        !nextAaUP
                    }

                    rootAA!!.addAtID("C${aminoacidNumbersStored-1}", nextAminoBondTree)
                    val newRoot = rootAA!!
                    val polypeptide = MoleculeMesh(newRoot)
                    polypeptide.name = "poly${aminoacidNumbersStored}"
                    if(aminoacidNumbersStored == 1) {
                        scene.removeChild("aa" + "${aminoacidNumbersStored-1}")
                    }
                    scene.removeChild("aa$aminoacidNumbersStored")
                    scene.removeChild("poly${aminoacidNumbersStored - 1}")
                    scene.addChild(polypeptide)
                    aminoacidNumbersStored += 1
                    combineTwoAcids = !combineTwoAcids
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


