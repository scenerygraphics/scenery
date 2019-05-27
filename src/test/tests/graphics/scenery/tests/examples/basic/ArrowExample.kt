package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Simple example to demonstrate the drawing of 3D arrows/vectors.
 *
 * This example will draw a circle made of vectors using
 * the [Arrow] class. One vector will, however, always
 * illuminate differently, and the position of such will
 * circulate...
 *
 * @author Vladimir Ulman <ulman@mpi-cbg.de>
 */
class ArrowExample : SceneryBase("ArrowExample") {

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        setupScene()
        useScene()
    }


    private fun setupScene() {
        //boundaries of our world
        val hull = Box(GLVector(50.0f, 50.0f, 50.0f), insideNormals = true)
        hull.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hull.material.cullingMode = Material.CullingMode.Front
        scene.addChild(hull)

        //lights and camera
        var pl = emptyArray<PointLight>()
        for (i in 0..3)
        {
            val l = PointLight(radius = 200.0f)
            l.intensity = 5.0f
            l.emissionColor = GLVector(1.0f,3)

            scene.addChild(l)
            pl = pl.plus(l)
        }
        pl[0].position = GLVector(0f,10f,0f)
        pl[1].position = GLVector(0f,-10f,0f)
        pl[2].position = GLVector(-10f,0f,0f)
        pl[3].position = GLVector(10f,0f,0f)

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.active = true
        scene.addChild(cam)
    }


    private fun useScene() {
        //we shall have faint and bright vectors...
        val matBright = Material()
        matBright.diffuse  = GLVector(0.0f, 1.0f, 0.0f)
        matBright.ambient  = GLVector(1.0f, 1.0f, 1.0f)
        matBright.specular = GLVector(1.0f, 1.0f, 1.0f)
        matBright.cullingMode = Material.CullingMode.None

        val matFaint = Material()
        matFaint.diffuse  = GLVector(0.0f, 0.6f, 0.6f)
        matFaint.ambient  = GLVector(1.0f, 1.0f, 1.0f)
        matFaint.specular = GLVector(1.0f, 1.0f, 1.0f)
        matFaint.cullingMode = Material.CullingMode.None

        //... arranged along a circle
        val circleCentre = GLVector(0.0f,3)
        val circleRadius = 6.0f

        val arrowsInCircle = 30


        //create the circle of vectors
        var al = emptyArray<Arrow>()
        var lastPos = GLVector(circleRadius,0f,0f)
        var currPos : GLVector
        for (i in 1..arrowsInCircle)
        {
            val curAng = (i*2.0f* PI/arrowsInCircle).toFloat()
            currPos = GLVector(circleRadius*cos(curAng) +circleCentre.x(),
                               circleRadius*sin(curAng) +circleCentre.y(),
                               circleCentre.z())

            // ========= this is how you create an Arrow =========
            val a = Arrow(currPos.minus(lastPos))  //shape of the vector itself
            a.position = lastPos                   //position/base of the vector
            a.material = matFaint                  //usual stuff follows...
            a.edgeWidth = 0.5f
            scene.addChild(a)

            // if you want to change the shape and position of the vector later,
            // you can use this (instead of creating a new vector)
            // a.reshape( newVector )
            // a.position = newBase
            // ========= this is how you create an Arrow =========

            al = al.plus(a)

            lastPos = currPos
        }


        //finally, have some fun...
        thread {
            var i = 0
            while (true) {
                al[i].material = matFaint
                i = (i+1).rem(arrowsInCircle)
                al[i].material = matBright

                Thread.sleep(150)
            }
        }
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
