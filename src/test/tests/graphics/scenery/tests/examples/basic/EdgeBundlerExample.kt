package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.EdgeBundler
import graphics.scenery.numerics.Random
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Simple example to demonstrate Edge Bundling
 * @author Johannes Waschke <jowaschke@cbs.mpg.de>
 */
class EdgeBundlerExample : SceneryBase("EdgeBundlerExample") {
    var path =  """C:\Programming_meta\scenery\1\lines"""
    var numClusters = 3
    private var colorMap: Array<GLVector> = arrayOf(GLVector(1.0f, 1.0f, 1.0f))

    override fun init() {
        System.setProperty("scenery.OpenCLDevice", "1,0");
        var eb = EdgeBundler(path, numClusters)
        // eb.estimateGoodParameters() // This OR set everything manually.
        eb.calculate()

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        initRender()
        colorMap = getRandomColors()
        renderLinePairs(eb.getLinePairs(), eb.getClusterOfTracks())
    }

    /*



     */

    private fun initRender() {
        val hull = Box(GLVector(50.0f, 50.0f, 50.0f), insideNormals = true)
        hull.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hull.material.cullingMode = Material.CullingMode.Front
        scene.addChild(hull)
        val lights = (0 until 3).map {
            val l = PointLight(radius = 40.0f)
            l.intensity = 0.5f
            l.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            scene.addChild(l)
            l
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.target = GLVector(0.0f, 0.0f, 0.0f)
        cam.active = true
        scene.addChild(cam)

        thread {
            while(true) {
                val t = runtime/100
                lights.forEachIndexed { i, pointLight ->
                    pointLight.position = GLVector(
                        33.0f*Math.sin(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat(),
                        0.0f,
                        -33.0f*Math.cos(2*i*Math.PI/3.0f+t*Math.PI/50).toFloat())
                }
                Thread.sleep(20)
            }
        }


        thread {
            while (true) {
                val t = Math.sin(runtime * Math.PI/2000.0) * 0.5 + 0.5
                scene.children.forEach() { n-> if(n is LinePair) {n.interpolationState = t.toFloat()} }
                Thread.sleep(20)
            }
        }

    }

    /**
     * Creates a random color for each cluster
     */
    private fun getRandomColors(): Array<GLVector> {
        var result: Array<GLVector> = Array(numClusters) { GLVector(0.0f, 0.0f, 0.0f) }
        val values: FloatArray = FloatArray(1000) {i -> i.toFloat() / 1000.0f}
        for(i in 0 until numClusters)
        {
            result[i] = GLVector(values.random(), values.random(), values.random())
        }
        return result
    }

    private fun renderLines(lines: Array<Line>, cluster: Array<Int>) {
        for(t in 0 until lines.size) {
            var track = lines[t]
            val clusterId = cluster[t]
            track.material.ambient = colorMap[clusterId]
            track.material.diffuse = colorMap[clusterId]
            track.material.specular = colorMap[clusterId]
            scene.addChild(track)
        }
        scene.dirty = true
    }

    private fun renderLinePairs(lines: Array<LinePair>, cluster: Array<Int>) {
        for(t in 0 until lines.size) {
            var track = lines[t]
            val clusterId = cluster[t]
            track.material.ambient = colorMap[clusterId]
            track.material.diffuse = colorMap[clusterId]
            track.material.specular = colorMap[clusterId]
            scene.addChild(track)
        }
        scene.dirty = true
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test override fun main() {
        super.main()
    }
}
