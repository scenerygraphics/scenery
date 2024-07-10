package graphics.scenery.geometry.curve

import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import java.nio.FloatBuffer

/**
 * Each child of the curve must be, per definition, another Mesh. Therefore, this class turns a List of
 * vertices into a Mesh.
 *
 * @param verticesBuffer: corresponds to the vertices buffer of Mesh
 * @param normalVectors: corresponds to the normals buffer of Mesh
 *
 */
class PartialCurve(verticesBuffer: FloatBuffer, normalVectors: FloatBuffer) : Mesh("PartialCurve") {
    init {
        geometry {
            vertices = verticesBuffer.duplicate()
            vertices.flip()
            texcoords = BufferUtils.allocateFloat(verticesBuffer.capacity() * 2)
            normals = normalVectors.duplicate()
            normals.flip()

            boundingBox = generateBoundingBox()
        }
        boundingBox = generateBoundingBox()
    }
}
