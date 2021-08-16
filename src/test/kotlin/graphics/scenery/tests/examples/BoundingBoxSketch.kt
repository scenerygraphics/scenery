package graphics.scenery.tests.examples

import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.Arrow

import graphics.scenery.utils.extensions.minus
import org.joml.Vector3f

class BoundingBoxSketch : SceneryBase("BoundingBoxSketch") {

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setupScene()
        useScene()
    }


    private fun setupScene() {
        //boundaries of our world
        val hull = Box(Vector3f(50.0f, 50.0f, 50.0f), insideNormals = true)
        hull.material().diffuse = Vector3f(0.2f, 0.2f, 0.2f)
        hull.material().cullingMode = Material.CullingMode.Front
        scene.addChild(hull)

        //we shall have faint and bright vectors...
        val matBright = DefaultMaterial()
        matBright.diffuse  = Vector3f(1.0f, 0.0f, 0.0f)
        matBright.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matBright.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matBright.cullingMode = Material.CullingMode.None

        val matFaint = DefaultMaterial()
        matFaint.diffuse  = Vector3f(0.0f, 0.6f, 0.6f)
        matFaint.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.cullingMode = Material.CullingMode.None

        val boundingBox = hull.getMaximumBoundingBox()
        val a = Arrow(boundingBox.max - Vector3f())  //shape of the vector itself
        a.position = Vector3f()                  //position/base of the vector
        a.addAttribute(Material::class.java, matFaint)                  //usual stuff follows...
        a.edgeWidth = 0.5f
        scene.addChild(a)

        val b = Arrow(boundingBox.min - Vector3f())  //shape of the vector itself
        b.position = Vector3f()                  //position/base of the vector
        b.addAttribute(Material::class.java, matFaint) //ual stuff follows...
        b.edgeWidth = 0.5f
        scene.addChild(b)

        //lights and camera
        var pl = emptyArray<PointLight>()
        for (i in 0..3)
        {
            val l = PointLight(radius = 200.0f)
            l.intensity = 5.0f
            l.emissionColor = Vector3f(1.0f)

            scene.addChild(l)
            pl = pl.plus(l)
        }
        pl[0].position = Vector3f(0f,10f,0f)
        pl[1].position = Vector3f(0f,-10f,0f)
        pl[2].position = Vector3f(-10f,0f,0f)
        pl[3].position = Vector3f(10f,0f,0f)

        val cam: Camera = DetachedHeadCamera()
        cam.position = Vector3f(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)
    }


    private fun useScene() {

    }

    override fun inputSetup() {
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BoundingBoxSketch().main()
        }
    }
}
