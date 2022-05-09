package graphics.scenery.volumes.vdi

import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import java.nio.ByteBuffer

data class VDIMetadata(
    val version: Int = 1,
    val projection: Matrix4f = Matrix4f(),
    val view: Matrix4f = Matrix4f(),
    val model: Matrix4f = Matrix4f(),
    val volumeDimensions: Vector3f = Vector3f(),
    val windowDimensions: Vector2i = Vector2i(),
    val nw: Float = 0f
)

data class VDIData(
//    val vdiDepth: ByteBuffer = ByteBuffer.allocate(1),
//    val vdiColor: ByteBuffer = ByteBuffer.allocate(1),
//    val gridCells: ByteBuffer = ByteBuffer.allocate(1),
    val metadata: VDIMetadata = VDIMetadata()
)