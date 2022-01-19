package graphics.scenery.tests.examples.proteins

import org.joml.*
import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.behaviours.SelectCommand
import graphics.scenery.primitives.Arrow
import graphics.scenery.proteins.chemistry.AminoTreeList
import graphics.scenery.proteins.chemistry.PeriodicTable
import graphics.scenery.proteins.chemistry.MoleculeMesh
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.xyz
import kotlin.concurrent.thread

/**
 *
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 */
class PeptideBondAnimationExample: SceneryBase("RainbowRibbon", windowWidth = 1280, windowHeight = 720) {
    override fun init() {

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val rowSize = 20f
        val glutamineBondTree = AminoTreeList().aminoMap["GLN"]
        val tryptophaneBondTree = AminoTreeList().aminoMap["TRP"]
        val glutamine = MoleculeMesh(glutamineBondTree!!)
        glutamine.name = "gln"
        scene.addChild(glutamine)
        val n = glutamine.getChildrenByName("OH").first().spatialOrNull()!!.worldPosition()
        val hn = glutamine.getChildrenByName("HO").first().spatialOrNull()!!.worldPosition()
        val position1 = Vector3f(hn).sub(Vector3f(n)).normalize().mul(4f)
        val tryptophane = MoleculeMesh(tryptophaneBondTree!!)
        tryptophane.name = "trp"
        scene.addChild(tryptophane)
        tryptophane.spatial().position = position1


        if(glutamineBondTree != null && tryptophaneBondTree != null) {
            tryptophaneBondTree.removeByID("HN")
            glutamineBondTree.removeByID("OH")
            tryptophaneBondTree.renameAminoAcidIds(1)
            glutamineBondTree.addAtID("C", tryptophaneBondTree)
            val dipeptide = MoleculeMesh(glutamineBondTree)
            dipeptide.name = "dip"
            dipeptide.visible = false
            scene.addChild(dipeptide)
        }



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
            val action: (Scene.RaycastResult, Int, Int) -> Unit = { result, _, _ ->
                thread {
                    val tryptophane = scene.getChildrenByName("trp").first()
                    val glutamine = scene.getChildrenByName("gln").first()
                    tryptophane.getChildrenByName("HNCyl").first().visible = false
                    glutamine.getChildrenByName("OHCyl").first().visible = false
                    glutamine.getChildrenByName("HOCyl").first().visible = false
                    //add new nodes for the water, using the "bounded" result in false positions
                    val hNode = tryptophane.getChildrenByName("HN").first()
                    val oNode = glutamine.getChildrenByName("OH").first()
                    val h2Node = glutamine.getChildrenByName("HO").first()

                    //first oxygen
                    val oPos = Vector3f(oNode.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val o = Icosphere(0.15f, 2)
                    o.material { diffuse = PeriodicTable().findElementByNumber(8).color!! }
                    o.spatial().position = oPos
                    o.name = "O"
                    scene.addChild(o)
                    oNode.visible = false
                    //then hydrogen number 1
                    val hPos = Vector3f(hNode.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val h = Icosphere(0.05f, 2)
                    h.material { diffuse = PeriodicTable().findElementByNumber(1).color!! }
                    h.spatial().position = hPos
                    h.name = "HN"
                    scene.addChild(h)
                    hNode.visible = false
                    // and hydrogen number 2
                    val h2Pos = Vector3f(h2Node.spatialOrNull()!!.world.getColumn(3, Vector3f()))
                    val h2 = Icosphere(0.05f, 2)
                    h2.material { diffuse = PeriodicTable().findElementByNumber(1).color!! }
                    h2.spatial().position = h2Pos
                    h2.name = "HO"
                    scene.addChild(h2)
                    h2Node.visible = false

                    val waterDirection = Vector3f(hPos).sub(Vector3f(h2Pos)).normalize()
                    //debug arrow
                    val matFaint = DefaultMaterial()
                    matFaint.diffuse  = Vector3f(0.0f, 0.6f, 0.6f)
                    matFaint.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
                    matFaint.specular = Vector3f(1.0f, 1.0f, 1.0f)
                    matFaint.cullingMode = Material.CullingMode.None
                    val a = Arrow(waterDirection)  //shape of the vector itself
                    a.spatial {
                        position = h2Pos                 //position/base of the vector
                    }
                    a.addAttribute(Material::class.java, matFaint)                  //usual stuff follows...
                    a.edgeWidth = 0.5f
                    scene.addChild(a)

                    val randomVector = Vector3f(waterDirection).mul(1/1000f)
                    //let them wiggle around to show that they are going to be emitted
                    for (i in 0 until 1000) {
                        //Random.random3DVectorFromRange(-0.005f, 0.005f)
                        h.spatial().position = h.spatial().worldPosition() -  randomVector
                        o.spatial().position = o.spatial().worldPosition()+  randomVector
                        h2.spatial().position = h2.spatial().worldPosition() + randomVector
                        Thread.sleep(2)
                    }


                    //water molecule is emitted
                    val waterPosition = Vector3f(o.spatialOrNull()!!.position).add(Vector3f(waterDirection).mul(0.2f))
                    o.spatialOrNull()!!.position = waterPosition
                    val down = Vector3f(scene.findObserver()!!.up).mul(-1f).normalize()
                    val side = Vector3f(down).cross(scene.findObserver()!!.forward).normalize()
                    val sin60 = kotlin.math.sin(kotlin.math.PI.toFloat()/3f)
                    val cos60 = kotlin.math.cos(kotlin.math.PI.toFloat()/3f)
                    val newHPos = Vector3f(waterPosition).add(Vector3f(down).mul(cos60).add(Vector3f(side).mul(sin60)).normalize().mul(0.2f))
                    val newH2Pos  = Vector3f(waterPosition).add(Vector3f(down).mul(cos60).add(Vector3f(side).mul(-sin60)).normalize().mul(0.2f))
                    h.spatialOrNull()!!.position = newHPos
                    h2.spatialOrNull()!!.position = newH2Pos
                    Thread.sleep(300)
                    for (i in 0 until 5000) {
                        val randomVector = Random.random3DVectorFromRange(-0.005f, 0.005f)
                        h.spatialOrNull()!!.position = newHPos +  randomVector
                        o.spatialOrNull()!!.position = waterPosition +  randomVector
                        h2.spatialOrNull()!!.position = newH2Pos +  randomVector
                        Thread.sleep(2)
                    }
                    scene.removeChild(h)
                    scene.removeChild(o)
                    scene.removeChild(h2)
                    val c = tryptophane.getChildrenByName("N").first()
                    val cPos = Vector3f(c.spatialOrNull()!!.position)
                    val n = glutamine.getChildrenByName("C").first()
                    val nPos = Vector3f(n.spatialOrNull()!!.position)
                    for (i in 0 until 5000) {
                        val randomVector = Random.random3DVectorFromRange(-0.005f, 0.005f)
                        n.spatialOrNull()!!.position = nPos +  randomVector
                        c.spatialOrNull()!!.position = cPos +  randomVector
                        Thread.sleep(2)
                    }

                    scene.getChildrenByName("trp").forEach { it.visible = !it.visible}
                    scene.getChildrenByName("gln").forEach { it.visible = !it.visible }
                    scene.getChildrenByName("dip").forEach { it.visible = !it.visible }
                }

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

    private fun Node.transposeWorldPosition(): Vector3f? {
        val target = this.spatialOrNull()?.position
        return if(target == null) {
            null
        } else {
            Matrix4f(this.spatialOrNull()?.world!!).transpose().transform(Vector4f().set(target, 1.0f)).xyz()
        }

    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PeptideBondAnimationExample().main()
        }
    }
}

