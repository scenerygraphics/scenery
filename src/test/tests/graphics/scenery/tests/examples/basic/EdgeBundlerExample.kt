package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.EdgeBundler
import graphics.scenery.numerics.Random
import org.junit.Test
import java.nio.FloatBuffer
import kotlin.concurrent.thread

/**
 * Simple example to demonstrate Edge Bundling
 * @author Johannes Waschke <jowaschke@cbs.mpg.de>
 */
class EdgeBundlerExample : SceneryBase("EdgeBundlerExample") {

    override fun init() {
        System.setProperty("scenery.OpenCLDevice", "1,0"); // Set a custom device (if wanted)

        //var eb = EdgeBundler("""C:\Programming_meta\scenery\1\lines""") // From CSV
        var eb = EdgeBundler(createLines(800, 50, 10.0f, 0.2f)) // From Line set
        eb.calculate() // Do the actual work. Optionally, you can change parameters before calling this function

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        initRender()
        var numClusters = eb.paramNumberOfClusters // Look how many clusters were created, use this information for colormap
        var colorMap = getRandomColors(numClusters)
        renderLinePairs(eb.getLinePairs(), eb.getClusterOfTracks(), colorMap)
    }

    /**
     * Creates a set of lines with a specified number of points per line. The distance between points
     * can be set by [step] and the overall scale can be defined by [scale].
     * Note, the lines are generated with an additional random offset between them (to make the data
     * a bit less uniform).
     * @param numLines Number of lines to be generated
     * @param numPositions Number of positions to be generated per line
     * @param step The distance between two positions (i.e. to make a line longer)
     * @param scale The overall scale of the whole line set
     * @return An array of Line objects
     */
    private fun createLines(numLines: Int, numPositions: Int, step: Float, scale: Float): Array<Line> {
        var lines = Array<Line>(numLines) {i -> Line()}
        for(i in 0 until numLines) {
            var randOffset = Random.randomFromRange(0.0f, 2.0f) - (numLines/2).toFloat()
            for(j in -numPositions/2 until numPositions/2) {
                lines[i].addPoint(GLVector(scale * (randOffset + i.toFloat()), scale * step * j.toFloat(), -100.0f))
            }
        }
        return lines
    }

    /**
     * Prepare the 3D environment
     */
    private fun initRender() {
        val hull = Box(GLVector(250.0f, 250.0f, 250.0f), insideNormals = true)
        hull.material.diffuse = GLVector(0.2f, 0.2f, 0.2f)
        hull.material.cullingMode = Material.CullingMode.Front
        scene.addChild(hull)
        val lights = (0 until 3).map {
            val l = PointLight(radius = 150.0f)
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
                val t = runtime/100.0f
                lights.forEachIndexed { i, pointLight ->
                    pointLight.position = GLVector(
                        33.0f*Math.sin(2.0f*i*Math.PI/3.0f+t*Math.PI/50.0f).toFloat(),
                        0.0f,
                        -33.0f*Math.cos(2.0f*i*Math.PI/3.0f+t*Math.PI/50.0f).toFloat())
                }
                Thread.sleep(20)
            }
        }

        // Bundling ... de-bundling ... bundling ... de-bundling...
        thread {
            while (true) {
                val t = Math.sin(runtime * Math.PI/2000.0f) * 0.5f + 0.5f
                scene.children.forEach() { n-> if(n is LinePair) {n.interpolationState = t.toFloat()} }
                Thread.sleep(20)
            }
        }
    }

    /**
     * Creates a random color for each cluster
     * @return An array of RGB colors
     */
    private fun getRandomColors(numClusters: Int): Array<GLVector> {
        var result: Array<GLVector> = Array(numClusters) { GLVector(0.0f, 0.0f, 0.0f) }
        val values = FloatArray(1000) {i -> i.toFloat() / 1000.0f}
        for(i in 0 until numClusters)
        {
            result[i] = GLVector(values.random(), values.random(), values.random())
        }
        return result
    }

    /**
     * Renders the lines and gives them colors based on their cluster
     * @param lines The array of lines
     * @param cluster The mapping between lineId and clusterId
     * @param colorMap Color map with a color for each cluster
     */
    private fun renderLinePairs(lines: Array<LinePair>, cluster: Array<Int>, colorMap: Array<GLVector>) {
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
