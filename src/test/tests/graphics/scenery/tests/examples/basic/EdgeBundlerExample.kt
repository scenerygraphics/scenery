package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.EdgeBundler
import org.junit.Test
import kotlin.concurrent.thread

/**
 * Simple example to demonstrate Edge Bundling
 * @author Johannes Waschke <jowaschke@cbs.mpg.de>
 */
class EdgeBundlerExample : SceneryBase("EdgeBundlerExample") {

    override fun init() {
        val eb = EdgeBundler(Line.createLines(800, 50, 10.0f, 0.2f), hub) // From Line set

        // Do the actual work. Optionally, you can change parameters before calling this function
        eb.calculate()

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        initScene()

        // Look how many clusters were created, use this information for colormap
        val colorMap = getRandomColors(eb.numberOfClusters)
        renderLinePairs(eb.getLinePairs(), eb.getClusterOfTracks(), colorMap)
    }



    /**
     * Prepare the 3D environment
     */
    private fun initScene() {
        val hull = Box(Vector3f(250.0f, 250.0f, 250.0f), insideNormals = true)
        hull.material.diffuse = Vector3f(0.2f, 0.2f, 0.2f)
        hull.material.cullingMode = Material.CullingMode.Front
        scene.addChild(hull)
        val lights = (0 until 3).map {
            val l = PointLight(radius = 150.0f)
            l.intensity = 0.5f
            l.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            scene.addChild(l)
            l
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = Vector3f(0.0f, 0.0f, 15.0f)
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        cam.target = Vector3f(0.0f, 0.0f, 0.0f)
        scene.addChild(cam)

        // Light show
        thread {
            while(true) {
                val t = runtime/100.0f
                lights.forEachIndexed { i, pointLight ->
                    pointLight.position = Vector3f(
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
    private fun getRandomColors(numClusters: Int): Array<Vector3f> {
        val result: Array<Vector3f> = Array(numClusters) { Vector3f(0.0f, 0.0f, 0.0f) }
        val values = FloatArray(1000) {i -> i.toFloat() / 1000.0f}
        for(i in 0 until numClusters)
        {
            result[i] = Vector3f(values.random(), values.random(), values.random())
        }
        return result
    }

    /**
     * Renders the lines and gives them colors based on their cluster
     * @param lines The array of lines
     * @param cluster The mapping between lineId and clusterId
     * @param colorMap Color map with a color for each cluster
     */
    private fun renderLinePairs(lines: Array<LinePair>, cluster: Array<Int>, colorMap: Array<Vector3f>) {
        for(t in lines.indices) {
            val track = lines[t]
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
