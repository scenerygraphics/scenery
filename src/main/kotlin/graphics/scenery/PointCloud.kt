package graphics.scenery

import org.lwjgl.system.MemoryUtil.memAlloc
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Constructs a point cloud.
 *
 * @author Kyle Harrington <kharrington@uidaho.edu>
 */
open class PointCloud(var radius: Float=0.1f, override var name: String = "PointCloud") : Node(name), HasGeometry {
    override var vertices: FloatBuffer = FloatBuffer.allocate(0)
    override var normals: FloatBuffer = FloatBuffer.allocate(0)
    override var texcoords: FloatBuffer = FloatBuffer.allocate(0)
    override var indices: IntBuffer = IntBuffer.allocate(0)

    override var vertexSize = 3;
    override var texcoordSize = 2;
    override var geometryType = GeometryType.POINTS;
    override var isBillboard = false;

    var pointRadius = radius;

    init {
        material = ShaderMaterial(arrayListOf("PointCloud.vert", "PointCloud.frag", "PointCloud.geom"))
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
}
