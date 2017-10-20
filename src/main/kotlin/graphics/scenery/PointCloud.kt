package graphics.scenery

import cleargl.GLVector
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil.memAlloc
import org.slf4j.Logger
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.FileSystems
import java.nio.file.Files

/**
 * Constructs a point cloud.
 *
 * @author Kyle Harrington <kharrington@uidaho.edu>
 */
open class PointCloud(var radius: Float=0.1f, override var name: String = "PointCloud") : Node(name), HasGeometry {
    protected val logger: Logger by LazyLogger()
    override var vertices: FloatBuffer = FloatBuffer.allocate(0)
    override var normals: FloatBuffer = FloatBuffer.allocate(0)
    override var texcoords: FloatBuffer = FloatBuffer.allocate(0)
    override var indices: IntBuffer = IntBuffer.allocate(0)

    override var vertexSize = 3
    override var texcoordSize = 2
    override var geometryType = GeometryType.POINTS
    override var isBillboard = false

    var pointRadius = radius

    init {
//        material = ShaderMaterial(arrayListOf("PointCloud.vert", "PointCloud.frag", "PointCloud.geom"))
        useClassDerivedShader = true
        this.pointRadius = radius
    }

    fun setupPointCloud() {
        this.texcoords = memAlloc(vertices.limit() * texcoordSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        var i = 0
        while (i < this.texcoords.limit() - 1) {
            this.texcoords.put(i, this.pointRadius)
            this.texcoords.put(i+1, this.pointRadius)
            i += 3
        }
    }

    fun readFromPALM(filename: String) {

        val count = Files.lines(FileSystems.getDefault().getPath(filename)).count()
        this.vertices = BufferUtils.allocateFloat(count.toInt()*3)
        this.normals = BufferUtils.allocateFloat(count.toInt()*3)
        this.texcoords = BufferUtils.allocateFloat(count.toInt()*2)

        logger.info("PointCloud: Reading ${count/3} locations from $filename...")

        Files.lines(FileSystems.getDefault().getPath(filename)).forEach {
            val arr = it.split("\t")
            if (!arr[0].startsWith("\"")) {
                this.vertices.put(arr[1].toFloat())
                this.vertices.put(arr[2].toFloat())
                this.vertices.put(arr[3].toFloat())

                this.boundingBoxCoords[0] = minOf(arr[1].toFloat(), boundingBoxCoords[0])
                this.boundingBoxCoords[2] = minOf(arr[2].toFloat(), boundingBoxCoords[2])
                this.boundingBoxCoords[4] = minOf(arr[3].toFloat(), boundingBoxCoords[4])

                this.boundingBoxCoords[1] = maxOf(arr[1].toFloat(), boundingBoxCoords[1])
                this.boundingBoxCoords[3] = maxOf(arr[2].toFloat(), boundingBoxCoords[3])
                this.boundingBoxCoords[5] = maxOf(arr[3].toFloat(), boundingBoxCoords[5])

                this.normals.put(arr[1].toFloat())
                this.normals.put(arr[2].toFloat())
                this.normals.put(arr[3].toFloat())

                this.texcoords.put(arr[4].toFloat())
                this.texcoords.put(arr[5].toFloat())
            }
        }

        logger.info("PointCloud: Finished reading")

        this.vertices.flip()
        this.normals.flip()
        this.texcoords.flip()
    }
}
