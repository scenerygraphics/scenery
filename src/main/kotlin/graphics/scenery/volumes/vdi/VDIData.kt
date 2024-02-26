package graphics.scenery.volumes.vdi

import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f

/**
 * Defines the metadata that is required for reading and rendering a Volumetric Depth Image (VDI).
 *
 * [version] The version of the VDI generation code. Used to prevent errors due to code version mismatch.
 * [index] The index of the VDI streamed in an ongoing VDI streaming session
 * [projection] The projection matrix of the camera used to generate the VDI.
 * [view] The view matrix of the camera used to generate the VDI.
 * [model] The model matrix of the volume in the scene for which the VDI was generated.
 * [volumeDimensions] The dimensions (in voxels) of the volume on which the VDI was generated.
 * [windowDimensions] The display resolution for which the VDI was generated.
 * [nw] Parameter from BigDataViewer which defines voxel length in world space.
 *
 * @author Aryaman Gupta <argupta@mpi-cbg.de> and Ulrik Günther <hello@ulrik.is>
 */
data class VDIMetadata(
    val version: Int = 1,
    var index: Int = 0,
    var projection: Matrix4f = Matrix4f(),
    var view: Matrix4f = Matrix4f(),
    var model: Matrix4f = Matrix4f(),
    val volumeDimensions: Vector3f = Vector3f(),
    var windowDimensions: Vector2i = Vector2i(),
    var nw: Float = 0f
)

/**
 * The sizes (in bytes), after potential compression, of the VDI buffers generated for streaming or storage.
 *
 * [colorSize] The size of the buffer containing colors of the supersegments contained in the VDI.
 * [depthSize] The size of the buffer containing front and back depths of the supersegments contained in the VDI.
 * [accelGridSize] The size of the acceleration data structure generated on the VDI.
 */
data class VDIBufferSizes(
    var colorSize: Long = 0,
    var depthSize: Long = 0,
    var accelGridSize: Long = 0
)

/**
 * The buffer sizes ([VDIBufferSizes]) and the metadata ([VDIMetadata]) corresponding to the generated VDI.
 *
 * [bufferSizes] The sizes (in bytes), after potential compression, of the VDI buffers.
 * [metadata] The metadata that is required for reading and rendering a VDI.
 */
data class VDIData(
    val bufferSizes: VDIBufferSizes = VDIBufferSizes(),
    val metadata: VDIMetadata = VDIMetadata()
)
