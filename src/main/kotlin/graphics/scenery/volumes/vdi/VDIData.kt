package graphics.scenery.volumes.vdi

import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f

data class VDIMetadata(
    val version: Int = 1,
    var index: Int = 0,
    var projection: Matrix4f = Matrix4f(),
    var view: Matrix4f = Matrix4f(),
    val model: Matrix4f = Matrix4f(),
    val volumeDimensions: Vector3f = Vector3f(),
    val windowDimensions: Vector2i = Vector2i(),
    val nw: Float = 0f
)

data class VDIBufferSizes(
    var colorSize: Long = 0,
    var depthSize: Long = 0,
    var accelGridSize: Long = 0
)

data class VDIData(
    val bufferSizes: VDIBufferSizes = VDIBufferSizes(),
    val metadata: VDIMetadata = VDIMetadata()
)
