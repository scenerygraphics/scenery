package graphics.scenery

import org.lwjgl.system.MemoryUtil.memAlloc
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.FileSystems
import java.nio.file.Files

/**
 * Constructs a point cloud, with a given base [pointRadius].
 *
 * @author Kyle Harrington <kharrington@uidaho.edu>
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class PointCloud(var pointRadius: Float = 0.1f, override var name: String = "PointCloud") : Node(name), HasGeometry {
    /** Array for the stored localisations. */
    override var vertices: FloatBuffer = FloatBuffer.allocate(0)
    /** Normal buffer, here (ab)used to store size and sigmas. */
    override var normals: FloatBuffer = FloatBuffer.allocate(0)
    /** Texcoords buffer, unused at the moment. */
    override var texcoords: FloatBuffer = FloatBuffer.allocate(0)
    /** Indices, not used for PointClouds. */
    override var indices: IntBuffer = IntBuffer.allocate(0)

    /** Vertex size, 3 in our case. */
    override var vertexSize = 3
    /** Texcoord size, 2 in our case. */
    override var texcoordSize = 2
    /** [PointCloud]s are rendered as point geometry. */
    override var geometryType = GeometryType.POINTS
    /** [PointClouds] do not get billboarded. */
    override var isBillboard = false

    init {
        // we are going to use shader files whose name is derived from the class name.
        // -> PointCloud.vert, PointCloud.frag
        useClassDerivedShader = true
    }

    /**
     * Sets up normal and texcoord buffers from the vertex buffers.
     */
    fun setupPointCloud() {
        if( this.texcoords.limit() == 0 ) {// Only preinitialize if texcoords has not been preinialized
            this.texcoords = memAlloc(vertices.limit() * texcoordSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            var i = 0
            while (i < this.texcoords.limit() - 1) {
                this.texcoords.put(i, this.pointRadius)
                this.texcoords.put(i + 1, this.pointRadius)
                i += 3
            }
        }
        if( this.normals.limit() == 0 ) {// Only preinitialize if need be
            this.normals = BufferUtils.allocateFloatAndPut( FloatArray(vertices.limit()*2/3, {1.0f} ) )
        }
    }

    /**
     * Reads the [PointCloud] from [filename], assuming the ThunderSTORM format.
     * See http://www.neurocytolab.org/tscolumns/ for format documentation.
     *
     * This function automatically determines the used separator char, and supports
     * comma (,), semicolon (;), and tab as separators.
     */
    fun readFromPALM(filename: String) {

        val count = Files.lines(FileSystems.getDefault().getPath(filename)).count()
        this.vertices = BufferUtils.allocateFloat(count.toInt()*3)
        this.normals = BufferUtils.allocateFloat(count.toInt()*3)
        this.texcoords = BufferUtils.allocateFloat(count.toInt()*2)

        logger.info("Reading ${count/3} locations from $filename...")

        val boundingBoxCoords = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        var separator = ""

        Files.lines(FileSystems.getDefault().getPath(filename)).forEach {
            // try to figure out separator char
            if(separator == "") {
                separator = when {
                    it.split(",").size >= 6 -> ","
                    it.split("\t").size >= 6 -> "\t"
                    it.split(";").size >= 6 -> ";"
                    else -> throw IllegalStateException("Could not determine separator char for file $filename.")
                }

                logger.debug("Determined <$separator> as separator char for $filename.")
            }

            val arr = it.split(separator)
            if (!arr[0].startsWith("\"")) {
                this.vertices.put(arr[1].toFloat())
                this.vertices.put(arr[2].toFloat())
                this.vertices.put(arr[3].toFloat())

                boundingBoxCoords[0] = minOf(arr[1].toFloat(), boundingBoxCoords[0])
                boundingBoxCoords[2] = minOf(arr[2].toFloat(), boundingBoxCoords[2])
                boundingBoxCoords[4] = minOf(arr[3].toFloat(), boundingBoxCoords[4])

                boundingBoxCoords[1] = maxOf(arr[1].toFloat(), boundingBoxCoords[1])
                boundingBoxCoords[3] = maxOf(arr[2].toFloat(), boundingBoxCoords[3])
                boundingBoxCoords[5] = maxOf(arr[3].toFloat(), boundingBoxCoords[5])

                this.normals.put(arr[1].toFloat())
                this.normals.put(arr[2].toFloat())
                this.normals.put(arr[3].toFloat())

                this.texcoords.put(arr[4].toFloat())
                this.texcoords.put(arr[5].toFloat())
            }
        }

        logger.info("Finished reading. Found ${vertices.capacity()/3} locations.")

        this.vertices.flip()
        this.normals.flip()
        this.texcoords.flip()

        boundingBox = OrientedBoundingBox(boundingBoxCoords)
    }
}
