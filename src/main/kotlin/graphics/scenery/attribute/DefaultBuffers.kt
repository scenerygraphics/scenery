package graphics.scenery.attribute

import graphics.scenery.BufferUtils
import graphics.scenery.Node
import graphics.scenery.attribute.geometry.Geometry
import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.utils.LazyLogger
import java.nio.ByteBuffer

open class DefaultBuffers(private var node : Node) : Buffers {

    @Transient override var buffers = HashMap<BufferType, ByteBuffer>()


    private val logger by LazyLogger()

    // this class could do the same as DefaultGeometry does now: Instanciate Position, Normals, TexCoords, Indices buffers, so they can be filled inside an actual
    // Object (Example shown in Mesh -> Cone


}
