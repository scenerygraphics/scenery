package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.compute.OpenCLContext
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import org.jocl.Sizeof
import org.jocl.cl_mem
import org.junit.Test
import java.awt.Font
import java.awt.Point
import java.io.File
import java.io.IOException
import java.lang.Float.MAX_VALUE
import java.nio.*

import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max


class PointWithMeta {
    var y: Float
    var z: Float
    var x: Float
    var attributes : ArrayList<Float> = ArrayList()

    constructor(x : Float = 0.0f, y : Float = 0.0f, z : Float = 0.0f) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun addAttribute(v : Float) {
        this.attributes.add(v)
    }

    operator fun plus(v : PointWithMeta) : PointWithMeta
    {
        var newPoint = PointWithMeta(this.x + v.x, this.y + v.y, this.z + v.z)
        for(i in 0 until this.attributes.size)
        {
            newPoint.addAttribute(this.attributes[i] + v.attributes[i])
        }
        return newPoint
    }

    operator fun times(v : Float) : PointWithMeta
    {
        var newPoint = PointWithMeta(this.x * v, this.y * v, this.z * v)
        for(i in 0 until this.attributes.size)
        {
            newPoint.addAttribute(this.attributes[i] * v)
        }
        return newPoint
    }

    fun distanceTo(v : PointWithMeta) : Float
    {
        return sqrt( (this.x - v.x).pow(2) + (this.y - v.y).pow(2) + (this.z - v.z).pow(2))
    }

    fun clone() : PointWithMeta
    {
        return this * 1.0f // TODO - is this too hacky?
    }
}

/**
 * @author Johannes Waschke <jowaschke@cbs.mpg.de>
 */
class EdgeBundler : SceneryBase("EdgeBundler") {
    protected var trackSet : Array<Array<PointWithMeta>> = Array(0) {Array(0) {PointWithMeta()}}

    // Min/max of data, from these we derive a proposal for paramBundlingRadius
    var minX : Float = Float.MAX_VALUE
    var minY : Float = Float.MAX_VALUE
    var minZ : Float = Float.MAX_VALUE
    var maxX : Float = Float.MIN_VALUE
    var maxY : Float = Float.MIN_VALUE
    var maxZ : Float = Float.MIN_VALUE


    var resultClustersReverse : Array<Int> = arrayOf<Int>()
    var resultClusters : Array<ArrayList<Int>> = arrayOf()
    var colorMap : Array<GLVector> = arrayOf(GLVector(1.0f, 1.0f, 1.0f))

    // Arrays for containing the data flattened to basic Ints/Floats
    var oclPoints = arrayListOf<Float>()
    var oclPointsResult = arrayListOf<Float>()
    var oclTrackStarts = arrayListOf<Int>()
    var oclTrackLengths = arrayListOf<Int>()
    var oclClusterIndices = arrayListOf<Int>()
    var oclClusterInverse = arrayListOf<Int>()
    var oclClusterStarts = arrayListOf<Int>()
    var oclClusterLengths = arrayListOf<Int>()

    // Anything to be set up by user. Import are
    // - paramCsvPath. A folder full of csv files. Each file is a track, each line must be "x,y,z[,attribute1[,attribute2[, ...]]]"
    // - paramNumberOfClusters. The more clusters, the lower is the (quadratic) runtime per "data piece".
    // - paramBundlingRadius. The distance in which magnetic forces work. Should be something like 5% of the data width
    var paramCsvPath = """C:\Programming_meta\scenery\2\lines"""
    var paramResampleTo = 30                          // Length of streamlines for edge bundling (and the result)
    var paramNumberOfClusters = 1                     // Number of clusters during edge bundling. More are quicker.
    var paramClusteringTrackSize = 6                  // Length of the reference track for edge bundling.
    var paramClusteringIterations = 20                // Iterations for defining the clusters.
    var paramBundlingIterations = 20                  // Iterations for edge bundling. Each iteration includes one smoothing step! Hence, for more iterations, reduce smoothing.
    var paramBundlingRadius: Float = 10.0f            // Radius in which magnet forces apply. Should be something link 5% of data space width
    var paramBundlingStepsize: Float = 1.0f           // Length of "magnet step". Just 1.0 is fine. Small steps require more iterations, larger might step too far.
    var paramBundlingAngleMin: Float = 0.0f           //
    var paramBundlingAngleStick: Float = 0.8f         // Defines how much non-parallel tracks stick together.
    var paramBundlingChunksize: Int = 10000           // Divides the calculation into pieces of this size
    var paramBundlingIncludeEndpoints: Int = 0        // 1 If lines should be bundled up to the last point; 0 if endpoints should stay at original position
    var paramBundlingSmoothingRadius: Int = 1         // Number of neighbors being considered by the smoothing window
    var paramBundlingSmoothingIntensity: Float = 0.5f // Degree how much to mix the smoothed result with the unsmooth data (1 = full smooth), 0.5 = 50:50)
    var paramAlpha: Float = 0.1f                      // Opacity of the lines while rendering

    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))
        initRender()
        thread {
            readTrajectories(paramCsvPath)
            this.trackSet = resampleTracks(trackSet, paramResampleTo)
            quickBundles()
            colorMap = getRandomColors()
            renderResultFirstTime()
            runEdgeBundling()
        }
    }

    /**
     * Create the environment for the 3D output
     */
    fun initRender() {
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
    }

    /**
     * Create a basic, empty line with the opacity defined by [paramAlpha] and a color from the [colorMap], depending
     * on its [trackId] and the corresponding cluster.
     */
    private fun makeLine(trackId : Int): Line {
        val renderLine = Line(transparent = true)
        val clusterId = resultClustersReverse[trackId]
        renderLine.name = trackId.toString()
        renderLine.material.ambient = colorMap[clusterId]
        renderLine.material.diffuse = colorMap[clusterId]
        renderLine.material.specular = colorMap[clusterId]
        renderLine.material.blending.opacity = paramAlpha
        renderLine.position = GLVector(0.0f, 0.0f, 0.0f)
        renderLine.edgeWidth = 0.01f
        return renderLine
    }

    /**
     * Creates a new set of lines from the currently stored [trackSet]
     */
    private fun renderResultFirstTime() {
        for(t in 0 until trackSet.size) {
            var renderLine = makeLine(t)
            val vertices = List<GLVector>(trackSet[t].size) {i -> GLVector(trackSet[t][i].x, trackSet[t][i].y, trackSet[t][i].z)}
            // val tangents = List<GLVector>(trackSet[t].size) {i -> (vertices[min(vertices.size - 1, i+1)] - vertices[max(0, i-1)]).normalized}
            renderLine.addPoints(vertices)
            scene.addChild(renderLine)
        }
    }

    /**
     * Removes existing lines and replaces them by the current state of them.
     */
    private fun renderResultUpdate() {
        for(t in 0 until trackSet.size) {
            // The next lines show the "boring" way [for the smarter one, see below] :
            scene.removeChild(t.toString())
            var renderLine = makeLine(t)
            val vertices = List<GLVector>(trackSet[t].size) {i -> GLVector(trackSet[t][i].x, trackSet[t][i].y, trackSet[t][i].z)}
            renderLine.addPoints(vertices)
            scene.addChild(renderLine)
            continue

            // TODO see my initial idea below. I wanted to replace the FloatBuffer but I couldn't succeed (crashes, or nothing to see anymore)
            val verticesFloat = FloatArray(trackSet[t].size * 3) {i ->
                if(i % 3 == 0) trackSet[t][i/3].x else if(i % 3 == 1) trackSet[t][i/3].y else trackSet[t][i/3].z
            }

            var lines = scene.getChildrenByName(t.toString())

            for(line in lines) {
                if(line is Line) {
                    var lineCasted = line as Line
                    var vertexBuffer = lineCasted.vertices.duplicate()
                    vertexBuffer.rewind()
                    vertexBuffer.put(verticesFloat)
                }
            }
        }
        scene.dirty = true
    }

    /**
     * Some parameters must be set up according to data properties. We try some basic guessing here.
     */
    fun estimateGoodParameters() {
        paramBundlingRadius = max(maxX - minX, max(maxY - minY, maxZ - minZ)) * 0.05f
        paramNumberOfClusters = ceil(trackSet.size.toFloat() / 500.0f).toInt()
        logger.info("Divide the data into " + paramNumberOfClusters.toString() + " clusters, magnetic forces over a distance of " + paramBundlingRadius)
    }

    /**
     * Creates basic Int/Float-Arrays from the hierarchical structures (e.g. the cluster-track relationship) and 3D data points
     */
    fun prepareFlattenedData() {
        oclPoints.clear()
        oclPointsResult.clear()
        oclTrackStarts.clear()
        oclTrackLengths.clear()
        oclClusterInverse.clear()
        oclClusterIndices.clear()
        oclClusterStarts.clear()
        oclClusterLengths.clear()
        var pointCounter : Int = 0
        for(t in 0 until trackSet.size) {
            oclTrackStarts.add(pointCounter)
            oclTrackLengths.add(trackSet[t].size)
            for(p in 0 until trackSet[t].size) {
                oclPoints.addAll(arrayOf(trackSet[t][p].x, trackSet[t][p].y, trackSet[t][p].z, 0.0f))
                oclPointsResult.addAll(arrayOf(trackSet[t][p].x, trackSet[t][p].y, trackSet[t][p].z, 0.0f))
                oclClusterInverse.add(0) // TODO I just want to prepare a list with n elements. I think there is a (much) better way
                pointCounter++
            }
        }

        var trackCounter : Int = 0
        for(c in 0 until resultClusters.size) {
            oclClusterStarts.add(trackCounter)
            oclClusterLengths.add(resultClusters[c].size)
            for(t in 0 until resultClusters[c].size) {
                oclClusterIndices.add(resultClusters[c][t])
                oclClusterInverse[resultClusters[c][t]] = c
            }
            trackCounter += resultClusters[c].size
        }
    }

    /**
     * Debug: print buffer contents
     */
    fun printIntBuffer(b : IntBuffer) {
        var b2: IntBuffer = b.duplicate()
        b.rewind()
        while(b.hasRemaining()) {
            print(b.get().toString() + ",")
        }
    }

    /**
     * Debug: print buffer contents
     */
    fun printFloatBuffer(b : FloatBuffer) {
        var b2: FloatBuffer = b.duplicate()
        b.rewind()
        while(b.hasRemaining()) {
            print(b.get().toString() + ",")
        }
    }

    /**
     * Create a new IntBuffer from an array of Ints, and report the result
     */
    fun createIntBuffer(values: ArrayList<Int>) : IntBuffer {
        var b: IntBuffer = BufferUtils.allocateIntAndPut(values.toIntArray())
        // println("Class of integer buffer " + b.javaClass.kotlin.qualifiedName)
        printIntBuffer(b)
        println()
        return b
    }

    /**
     * Create a new FloatBuffer from an array of Floats, and report the result
     */
    fun createFloatBuffer(values: ArrayList<Float>) : FloatBuffer {
        var b: FloatBuffer = BufferUtils.allocateFloatAndPut(values.toFloatArray())
        // println("Class of integer buffer " + b.javaClass.kotlin.qualifiedName)
        printFloatBuffer(b)
        println()
        return FloatBuffer.wrap(values.toFloatArray())
    }

    /**
     * Create working packages according to the chosen [paramBundlingChunksize]. For 25.000 tracks and a chunk size of
     * 10.000 this function would return [10.000, 10.000, 5.000].
     */
    fun getChunkSizes(): Array<Int> {
        val num: Int = trackSet.size / paramBundlingChunksize
        val remainder: Int =  trackSet.size % paramBundlingChunksize
        var chunksizes: Array<Int> = Array(num + 1) {i -> paramBundlingChunksize}
        chunksizes[num] = remainder
        return chunksizes
    }

    /**
     * The whole OpenCL-pipeline. Converting the data, create buffers, perform the calculation multiple times (according
     * to [paramBundlingIterations]) and splitted into chunks according to [paramBundlingChunksize]. For each iteration,
     * edge bundling is performed first and smoothing is performed afterwards.
     */
    fun runEdgeBundling() : Boolean {
        prepareFlattenedData()

        var ocl: OpenCLContext?
        try {
            ocl = OpenCLContext(hub)
        } catch (e: Exception) {
            ocl = null
        }

        if (ocl == null) {
            logger.warn("Could not create OpenCL compute context -- Do you have the necessary OpenCL libraries installed? Will fall back to default font.")
            return false
        } else {
            // Create buffers based on the flattened data arrays.
            // Note: [pointsResult] is also wrapped as input! The result is only calculated for positions affected by
            // the currently chosen parameters [...]. That means, we have to init the result with the full valid data
            // to avoid gaps in our results.
            var oclPointsInAndOut = createFloatBuffer(oclPoints)
            var points: cl_mem = ocl.wrapInput(oclPointsInAndOut)
            var pointsResult: cl_mem = ocl.wrapInput(oclPointsInAndOut)
            var trackStarts: cl_mem = ocl.wrapInput(createIntBuffer(oclTrackStarts), true)
            var trackLengths: cl_mem = ocl.wrapInput(createIntBuffer(oclTrackLengths), true)
            var clusterStarts: cl_mem = ocl.wrapInput(createIntBuffer(oclClusterStarts), true)
            var clusterLengths: cl_mem = ocl.wrapInput(createIntBuffer(oclClusterLengths), true)
            var clusterIndices: cl_mem = ocl.wrapInput(createIntBuffer(oclClusterIndices), true)
            var clusterInverse: cl_mem = ocl.wrapInput(createIntBuffer(oclClusterInverse), true)
            var magnetRadius: cl_mem = ocl.wrapInput(createFloatBuffer(arrayListOf(paramBundlingRadius)), true)
            var stepsize: cl_mem = ocl.wrapInput(createFloatBuffer(arrayListOf(paramBundlingStepsize)), true)
            var angleMin: cl_mem = ocl.wrapInput(createFloatBuffer(arrayListOf(paramBundlingAngleMin)), true)
            var angleStick: cl_mem = ocl.wrapInput(createFloatBuffer(arrayListOf(paramBundlingAngleStick)), true)
            var offset: cl_mem = ocl.wrapInput(createIntBuffer(arrayListOf(5)))
            var bundleEndPoints: cl_mem = ocl.wrapInput(createIntBuffer(arrayListOf(paramBundlingIncludeEndpoints)), true)
            var radius: cl_mem = ocl.wrapInput(createIntBuffer(arrayListOf(paramBundlingSmoothingRadius)), true)
            var intensity: cl_mem = ocl.wrapInput(createFloatBuffer(arrayListOf(paramBundlingSmoothingIntensity)), true)

            // Get kernels for edge bundling and smoothing
            ocl.loadKernel(EdgeBundler::class.java.getResource("EdgeBundler.cl"), "edgeBundling")
            ocl.loadKernel(EdgeBundler::class.java.getResource("EdgeBundler.cl"), "smooth")
            val chunksizes = getChunkSizes()

            logger.info("Starting OpenCL edge bundling")
            for(i in 0 until paramBundlingIterations) {
                for(c in 0 until chunksizes.size) {
                    statusPrint(i * chunksizes.size + c) // Current status; Will be called paramBundlingIterations * chunksizes.size times
                    var offsetBuffer = createIntBuffer(arrayListOf(c * paramBundlingChunksize))
                    offsetBuffer.rewind()
                    ocl.writeBuffer(offsetBuffer, offset)
                    offsetBuffer.rewind()

                    ocl.runKernel("edgeBundling", chunksizes[c],
                        trackStarts,
                        trackLengths,
                        clusterStarts,
                        clusterLengths,
                        clusterIndices,
                        clusterInverse,
                        points,
                        pointsResult,
                        magnetRadius,
                        stepsize,
                        angleMin,
                        angleStick,
                        offset,
                        bundleEndPoints)
                }

                // Read result and copy it back to input, for the smoothing
                ocl.readBuffer(pointsResult, oclPointsInAndOut)
                oclPointsInAndOut.rewind()
                ocl.writeBuffer(oclPointsInAndOut, points)
                oclPointsInAndOut.rewind()

                for(c in 0 until chunksizes.size) {
                    statusPrint(i * chunksizes.size + c)
                    var offsetBuffer = createIntBuffer(arrayListOf(c * paramBundlingChunksize))
                    offsetBuffer.rewind()
                    ocl.writeBuffer(offsetBuffer, offset)
                    offsetBuffer.rewind()

                    ocl.runKernel("smooth", chunksizes[c],
                        trackStarts,
                        trackLengths,
                        points,
                        pointsResult,
                        radius,
                        intensity,
                        offset)
                }

                // Read result and copy it back to input, for the next round
                ocl.readBuffer(pointsResult, oclPointsInAndOut)
                oclPointsInAndOut.rewind()
                ocl.writeBuffer(oclPointsInAndOut, points)
                oclPointsInAndOut.rewind()
            }

            // Now convert the flat arrays back to "sorted" ones. TODO: renderResultUpdate each iteration?
            processOpenClResult(oclPointsInAndOut)
            renderResultUpdate()

            logger.info("Finished OpenCL edge bundling.")
        }
        return true
    }

    /**
     *
     */
    fun processOpenClResult(buffer: FloatBuffer) {
        var b = buffer.duplicate()
        b.rewind()
        var posCounter: Int = 0
        var localCounter: Int = 0
        var trackCounter: Int = 0

        while(b.hasRemaining()) {
            val x = b.get()
            val y = b.get()
            val z = b.get()
            b.get() // The fourth dim is (currently) 0, we throw it away
            // print("($x, $y, $z)")
            trackSet[trackCounter][localCounter].x = x
            trackSet[trackCounter][localCounter].y = y
            trackSet[trackCounter][localCounter].z = z
            posCounter++
            localCounter++
            if(localCounter >= trackSet[trackCounter].size) {
                trackCounter++
                localCounter = 0
            }
        }
        // println()
    }

    /**
     * Look for csv files in [path]. Eacb file should contain exactly one track. The values are comma-separated. The
     * first three columns are x/y/z respectively. Every additional column is stored as attribute, but currently not
     * further processed/used.
     */
    fun readTrajectories(path: String) {
        var trackSetTemp : ArrayList<Array<PointWithMeta>> = ArrayList()
        File(path).walkBottomUp().forEach {file ->
            if(file.absoluteFile.extension.toLowerCase() == "csv") {
                if (file.absoluteFile.exists()) {
                    val trackTemp = arrayListOf<PointWithMeta>()
                    file.absoluteFile.forEachLine {line ->
                        val entry = line.split(",")
                        val point = PointWithMeta(entry[0].toFloat(), entry[1].toFloat(), entry[2].toFloat())
                        minX = min(minX, point.x)
                        minY = min(minY, point.y)
                        minZ = min(minZ, point.z)
                        maxX = max(maxX, point.x)
                        maxY = max(maxY, point.y)
                        maxZ = max(maxZ, point.z)

                        // Use every column behind the third one as attribute (TODO: however, so far we don't use the attributes)
                        for(i in 3 until entry.size)
                        {
                            point.addAttribute(entry[i].toFloat())
                        }
                        trackTemp.add(point)
                    }
                    var track = Array<PointWithMeta>(trackTemp.size, {i -> trackTemp[i]})
                    trackSetTemp.add(track)
                }
            }
        }
        this.trackSet = Array(trackSetTemp.size, {i -> trackSetTemp[i]})
        estimateGoodParameters()
    }

    /**
     * Creates a random color for each cluster
     */
    fun getRandomColors() : Array<GLVector> {
        var result : Array<GLVector> = Array(paramNumberOfClusters) { GLVector(0.0f, 0.0f, 0.0f) }
        val values : FloatArray = FloatArray(1000) {i -> i.toFloat() / 1000.0f}
        for(i in 0 until paramNumberOfClusters)
        {
            result[i] = GLVector(values.random(), values.random(), values.random())
        }
        return result
    }

    /**
     * Resizes tracks to a fixed size. Needed for Quickbundles, also optionally used for (quicker) processing of
     * edge bundling.
     */
    fun resampleTracks(tracks: Array<Array<PointWithMeta>>, numElements : Int) : Array<Array<PointWithMeta>> {
        if(numElements < 2) {
            return tracks // Do nothing if it doesn't make sense
        }

        var result : Array<Array<PointWithMeta>> = Array(tracks.size) {Array(0,{ PointWithMeta() })}
        for(i in 0 until tracks.size) {
            result[i] = resampleTrack(tracks[i], numElements)
        }

        return result
    }

    /**
     * Resamples a single track by linear interpolation of new positions between two original positons.
     */
    fun resampleTrack(track : Array<PointWithMeta>, numElements : Int) : Array<PointWithMeta> {
        val trackOut : Array<PointWithMeta> = Array(numElements) {PointWithMeta()}
        trackOut[0] = track[0]
        val scale = track.size.toFloat() / numElements.toFloat()
        for(i in 1 until numElements) {
            val iScale = i.toFloat() * scale
            val lower : Int = floor(iScale).toInt()
            val upper : Int = ceil(iScale).toInt()
            val ratio = iScale - lower.toFloat()
            val result = track[lower] * (1.0f - ratio) + track[upper] * ratio
            trackOut[i] = result
        }
        trackOut[numElements - 1] = track[track.size - 1]
        return trackOut
    }

    /**
     * Calculates a set of averaged tracks (each of size [paramClusteringTrackSize]), used for track comparison in
     * Quickbundles
     */
    fun calculateMeanTracks(tracks : Array<Array<PointWithMeta>>,
                            clustersReverse :  Array<Int>) : Array<Array<PointWithMeta>> {
        // We create a list of reference tracks for each cluster. The tracks have a fixed number of elements and their
        // positions, so far, are 0/0/0. Furthermore, we create a counter. With the counter we can update a mean track
        // without calculating the mean based on all tracks, but just by adding 1/nth of the next track. This allows
        // clustering in linear time (Quickbundles, Garyfallidis 2012)
        var meanTracks : Array<Array<PointWithMeta>> = Array(paramNumberOfClusters, {Array(paramClusteringTrackSize, {PointWithMeta()})})
        var meanTrackCounter : Array<Int> = Array(paramNumberOfClusters, {0})

        // Now add all the tracks to the mean track of their respective cluster
        for(i in 0 until tracks.size) {
            meanTrackCounter[clustersReverse[i]] += 1
            val ratio = 1.0f / meanTrackCounter[clustersReverse[i]].toInt()
            for(j in 0 until paramClusteringTrackSize) {
                meanTracks[clustersReverse[i]][j] = meanTracks[clustersReverse[i]][j] * (1.0f - ratio) + tracks[i][j] * ratio
            }
        }

        return meanTracks
    }

    /**
     * Sum of distances between the points of two tracks (pairwise comparison of ith point with ith point)
     */
    fun distanceBetweenTracks(t1 : Array<PointWithMeta>, t2 : Array<PointWithMeta>) : Float {
        var dist = 0.0f
        for(i in 0 until t2.size) {
            dist += t1[i].distanceTo(t2[i])
        }
        return dist
    }

    /**
     * Prints a single "*" as a cheap status bar. If [printEvery] is set to n, only every nth "*" is printed. This
     * reduces printing for high number of calls.
     */
    fun statusPrint(i : Int, printEvery : Int = 1) {
        if(i % printEvery == 0) {
            print("*")
        }
    }

    /**
     * Quickbundles, a relatively simple clustering algorithm that subdivides the tracks based on their spatial
     * similarity. The reason we used it is to have a spatial structure while performing the edge bundling. With this
     * spatial structure, we can massively reduce the runtime of the edge bundling.
     */
    fun quickBundles() {
        logger.info("Starting quickbundles")

        // First prepare a (random) starting state
        var smallTracks : Array<Array<PointWithMeta>> = Array(trackSet.size, {Array(paramClusteringTrackSize, {PointWithMeta(0.0f,0.0f,0.0f)})})
        var clustersReverse : Array<Int> = Array(trackSet.size, {0})
        for(i in 0 until trackSet.size) {
            smallTracks[i] = resampleTrack(trackSet[i], paramClusteringTrackSize)
        }
        var clusters : Array<ArrayList<Int>> = Array(paramNumberOfClusters, {ArrayList<Int>()})
        for(i in 0 until paramNumberOfClusters) {
            clusters[i] = ArrayList<Int>()
        }

        for(i in 0 until smallTracks.size) {
            val randomCluster = (0 until paramNumberOfClusters).random()
            clusters[randomCluster].add(i)
            clustersReverse[i] = randomCluster
        }
        var meanTracks = calculateMeanTracks(smallTracks, clustersReverse)

        // Now do the actual clustering
        for(i in 0 until paramClusteringIterations) {
            statusPrint(i)
            // Make current mapping empty
            for(i in 0 until paramNumberOfClusters) {
                clusters[i] = ArrayList<Int>()
            }

            // Sort tracks into the clusters
            for(t in 0 until smallTracks.size) {
                var lowestDistance : Float = MAX_VALUE
                var bestIndex = -1
                for(c in 0 until paramNumberOfClusters) {
                    var dist = distanceBetweenTracks(smallTracks[t], meanTracks[c])
                    if(dist < lowestDistance) {
                        lowestDistance = dist
                        bestIndex = c
                    }
                }
                clusters[bestIndex].add(t)
                clustersReverse[t] = bestIndex
            }
            meanTracks = calculateMeanTracks(smallTracks, clustersReverse)
        }
        println()
        resultClusters = clusters
        resultClustersReverse = clustersReverse
    }

    override fun inputSetup() {
        setupCameraModeSwitching(keybinding = "C")
    }

    @Test
    override fun main() {
        super.main()
    }
}
