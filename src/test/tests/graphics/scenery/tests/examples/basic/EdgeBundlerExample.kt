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
    var numClusters = 1
    private var colorMap: Array<GLVector> = arrayOf(GLVector(1.0f, 1.0f, 1.0f))

    override fun init() {
        System.setProperty("scenery.OpenCLDevice", "1,0"); // Set a custom device (if wanted)
        //var eb = EdgeBundler("""C:\Programming_meta\scenery\1\lines""") // From CSV
        var eb = EdgeBundler(createLines(800, 50, 10, 0.2f)) // From Line set
        eb.calculate()

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        initRender()
        numClusters = eb.paramNumberOfClusters // Look how many clusters were created, use this information for colormap
        colorMap = getRandomColors()
        renderLinePairs(eb.getLinePairs(), eb.getClusterOfTracks())
    }

    /**
     * Creates a set of lines with a specified number of points per line. The distance between points
     * can be set by [step] and the overall scale can be defined by [scale].
     * Note, the lines are generated with an additional random offset between them (to make the data
     * a bit less uniform).
     */
    private fun createLines(numLines: Int, numPositions: Int, step: Int, scale: Float): Array<Line> {
        var lines = Array<Line>(numLines) {i -> Line()}
        for(i in 0 until numLines) {
            var randOffset = Random.randomFromRange(0.0f, 2.0f) - (numLines/2).toFloat()
            for(j in -numPositions/2 until numPositions/2) {
                lines[i].addPoint(GLVector(scale * (randOffset + i.toFloat()), scale * (step * j).toFloat(), -100.0f))
                //logger.info("Add point" + (scale * i.toFloat()).toString() + ", " + (scale *(step * j).toFloat()).toString())
            }
        }
        return lines
    }

    private fun initRender() {
        val hull = Box(GLVector(250.0f, 250.0f, 250.0f), insideNormals = true)
        hull.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hull.material.cullingMode = Material.CullingMode.Front
        scene.addChild(hull)
        val lights = (0 until 3).map {
            val l = PointLight(radius = 240.0f)
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

        // Light show
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

        // Bundling ... de-bundling ... bundling ... de-bundling...
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
