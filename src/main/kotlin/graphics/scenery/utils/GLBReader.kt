package graphics.scenery.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reader for binary glTF (GLB) data, which scenery's [Mesh] readers do not cover -- they only
 * handle OBJ and STL. Supports the subset of glTF needed for mesh geometry: node hierarchies,
 * float vertex attributes and byte, short or int indices, including interleaved buffer views.
 *
 * Materials, textures and animations are not read.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
object GLBReader {
    private val logger by lazyLogger()

    /** `glTF` in ASCII, the magic number at the start of every GLB file. */
    private const val GLB_MAGIC = 0x46546C67

    /** GLB chunk type tags. */
    private const val GLB_CHUNK_JSON = 0x4E4F534A
    private const val GLB_CHUNK_BIN = 0x004E4942

    /** glTF accessor component types. */
    private const val GLTF_UNSIGNED_BYTE = 5121
    private const val GLTF_UNSIGNED_SHORT = 5123
    private const val GLTF_UNSIGNED_INT = 5125
    private const val GLTF_FLOAT = 5126

    /**
     * Parses a binary glTF (GLB) [buffer] into a [Mesh].
     *
     * The JSON chunk describes accessors into the single binary chunk, from which positions,
     * normals, texture coordinates and indices are read. Each glTF mesh primitive becomes a
     * child node, with the node hierarchy's transforms baked into the vertices.
     *
     * The [buffer] is only read, never freed -- freeing it stays with the caller that
     * allocated it. Returns null if the data is not valid GLB.
     */
    fun parseGLB(buffer: ByteBuffer): Mesh? {
        try {
            val glb = buffer.order(ByteOrder.LITTLE_ENDIAN)

            if (glb.remaining() < 12 || glb.getInt(0) != GLB_MAGIC) {
                logger.warn("Buffer is not a GLB file, ignoring.")
                return null
            }

            // Header is magic/version/length, followed by length-prefixed, type-tagged chunks.
            var offset = 12
            var json: JsonNode? = null
            var binary: ByteBuffer? = null

            while (offset + 8 <= glb.limit()) {
                val chunkLength = glb.getInt(offset)
                val chunkType = glb.getInt(offset + 4)
                val chunkStart = offset + 8

                if (chunkLength < 0 || chunkStart + chunkLength > glb.limit()) {
                    break
                }

                when (chunkType) {
                    GLB_CHUNK_JSON -> {
                        val bytes = ByteArray(chunkLength)
                        glb.position(chunkStart)
                        glb.get(bytes)
                        json = ObjectMapper().readTree(String(bytes, Charsets.UTF_8))
                    }

                    GLB_CHUNK_BIN -> {
                        binary = glb.slice(chunkStart, chunkLength).order(ByteOrder.LITTLE_ENDIAN)
                    }
                }

                // Chunks are padded to four-byte boundaries.
                offset = chunkStart + ((chunkLength + 3) / 4) * 4
            }

            val gltf = json ?: run {
                logger.warn("GLB asset has no JSON chunk, ignoring.")
                return null
            }

            val bin = binary ?: run {
                logger.warn("GLB asset has no binary chunk, ignoring.")
                return null
            }

            val root = Mesh("rendermodel")
            val nodes = gltf["nodes"] ?: return null
            val meshes = gltf["meshes"] ?: return null

            // Walk the scene graph so each primitive gets its accumulated transform.
            val sceneIndex = gltf["scene"]?.asInt() ?: 0
            val roots = gltf["scenes"]?.get(sceneIndex)?.get("nodes")
                ?: return null

            roots.forEach { nodeIndex ->
                addGLTFNode(root, gltf, nodes, meshes, bin, nodeIndex.asInt(), Matrix4f())
            }

            return if (root.children.isEmpty()) {
                logger.warn("GLB asset contained no usable geometry.")
                null
            } else {
                root
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse GLB render model: $e")
            return null
        }
    }

    /**
     * Recursively converts the glTF node at [nodeIndex] and its children into scenery nodes under
     * [parent], accumulating [transform] down the hierarchy.
     */
    private fun addGLTFNode(
        parent: Mesh, gltf: JsonNode, nodes: JsonNode, meshes: JsonNode,
        bin: ByteBuffer, nodeIndex: Int, transform: Matrix4f
    ) {
        val node = nodes.get(nodeIndex) ?: return
        val local = Matrix4f(transform).mul(node.toLocalTransform())

        node["mesh"]?.let { meshRef ->
            meshes.get(meshRef.asInt())?.get("primitives")?.forEach { primitive ->
                buildPrimitive(gltf, primitive, bin, local)?.let { parent.addChild(it) }
            }
        }

        node["children"]?.forEach { child ->
            addGLTFNode(parent, gltf, nodes, meshes, bin, child.asInt(), local)
        }
    }

    /** Reads a glTF node's local transform, from either a matrix or TRS components. */
    private fun JsonNode.toLocalTransform(): Matrix4f {
        this["matrix"]?.let { m ->
            val values = FloatArray(16) { m.get(it).floatValue() }
            return Matrix4f().set(values)
        }

        val translation = this["translation"]?.let {
            Vector3f(it.get(0).floatValue(), it.get(1).floatValue(), it.get(2).floatValue())
        } ?: Vector3f(0.0f)

        val rotation = this["rotation"]?.let {
            Quaternionf(
                it.get(0).floatValue(), it.get(1).floatValue(),
                it.get(2).floatValue(), it.get(3).floatValue()
            )
        } ?: Quaternionf()

        val scale = this["scale"]?.let {
            Vector3f(it.get(0).floatValue(), it.get(1).floatValue(), it.get(2).floatValue())
        } ?: Vector3f(1.0f)

        return Matrix4f().translationRotateScale(
            translation.x, translation.y, translation.z,
            rotation.x, rotation.y, rotation.z, rotation.w,
            scale.x, scale.y, scale.z
        )
    }

    /**
     * Builds a [Mesh] from a single glTF primitive, baking [transform] into the vertex positions
     * so the result needs no further node hierarchy.
     */
    private fun buildPrimitive(gltf: JsonNode, primitive: JsonNode, bin: ByteBuffer, transform: Matrix4f): Mesh? {
        val attributes = primitive["attributes"] ?: return null
        val positionAccessor = attributes["POSITION"]?.asInt() ?: return null

        val positions = readAccessor(gltf, bin, positionAccessor, 3) ?: return null
        val mesh = Mesh("primitive")

        // Bake the transform in, as the vertices are handed to the renderer directly.
        val normalMatrix = Matrix4f(transform).invert().transpose()
        val position = Vector3f()

        val vertices = BufferUtils.allocateFloat(positions.size)
        var i = 0
        while (i + 2 < positions.size) {
            position.set(positions[i], positions[i + 1], positions[i + 2])
            transform.transformPosition(position)
            vertices.put(position.x).put(position.y).put(position.z)
            i += 3
        }
        vertices.flip()

        val normalValues = attributes["NORMAL"]?.asInt()?.let { readAccessor(gltf, bin, it, 3) }
        val normals = if (normalValues != null && normalValues.size == positions.size) {
            val n = BufferUtils.allocateFloat(normalValues.size)
            var j = 0
            while (j + 2 < normalValues.size) {
                position.set(normalValues[j], normalValues[j + 1], normalValues[j + 2])
                normalMatrix.transformDirection(position).normalize()
                n.put(position.x).put(position.y).put(position.z)
                j += 3
            }
            n.flip()
            n
        } else {
            null
        }

        val texcoordValues = attributes["TEXCOORD_0"]?.asInt()?.let { readAccessor(gltf, bin, it, 2) }

        mesh.geometry {
            this.vertices = vertices

            if (normals != null) {
                this.normals = normals
            }

            if (texcoordValues != null) {
                val t = BufferUtils.allocateFloat(texcoordValues.size)
                texcoordValues.forEach { t.put(it) }
                t.flip()
                this.texcoords = t
            }

            primitive["indices"]?.asInt()?.let { accessor ->
                readIndexAccessor(gltf, bin, accessor)?.let { values ->
                    val idx = BufferUtils.allocateInt(values.size)
                    values.forEach { idx.put(it) }
                    idx.flip()
                    this.indices = idx
                }
            }

            // Normals are required to be non-empty whenever vertices are.
            if (normals == null) {
                recalculateNormals()
            }
        }

        mesh.ifMaterial { diffuse = Vector3f(0.8f, 0.8f, 0.8f) }

        return mesh
    }

    /**
     * Reads a float accessor of [componentsPerElement] components, resolving its buffer view into
     * [bin]. Returns null for accessors this parser does not support.
     */
    private fun readAccessor(gltf: JsonNode, bin: ByteBuffer, accessorIndex: Int, componentsPerElement: Int): FloatArray? {
        val accessor = gltf["accessors"]?.get(accessorIndex) ?: return null

        if (accessor["componentType"]?.asInt() != GLTF_FLOAT) {
            logger.debug("Unsupported accessor component type for float data, skipping primitive.")
            return null
        }

        val count = accessor["count"]?.asInt() ?: return null
        val viewIndex = accessor["bufferView"]?.asInt() ?: return null
        val view = gltf["bufferViews"]?.get(viewIndex) ?: return null

        val viewOffset = view["byteOffset"]?.asInt() ?: 0
        val accessorOffset = accessor["byteOffset"]?.asInt() ?: 0
        val stride = view["byteStride"]?.asInt() ?: (componentsPerElement * 4)
        val base = viewOffset + accessorOffset

        val result = FloatArray(count * componentsPerElement)

        for (element in 0 until count) {
            val elementStart = base + element * stride

            for (component in 0 until componentsPerElement) {
                val at = elementStart + component * 4
                if (at + 4 > bin.limit()) {
                    return null
                }
                result[element * componentsPerElement + component] = bin.getFloat(at)
            }
        }

        return result
    }

    /** Reads an index accessor, which may use byte, short or int components. */
    private fun readIndexAccessor(gltf: JsonNode, bin: ByteBuffer, accessorIndex: Int): IntArray? {
        val accessor = gltf["accessors"]?.get(accessorIndex) ?: return null
        val componentType = accessor["componentType"]?.asInt() ?: return null
        val count = accessor["count"]?.asInt() ?: return null
        val viewIndex = accessor["bufferView"]?.asInt() ?: return null
        val view = gltf["bufferViews"]?.get(viewIndex) ?: return null

        val componentSize = when (componentType) {
            GLTF_UNSIGNED_BYTE -> 1
            GLTF_UNSIGNED_SHORT -> 2
            GLTF_UNSIGNED_INT -> 4
            else -> return null
        }

        val base = (view["byteOffset"]?.asInt() ?: 0) + (accessor["byteOffset"]?.asInt() ?: 0)
        val stride = view["byteStride"]?.asInt() ?: componentSize
        val result = IntArray(count)

        for (element in 0 until count) {
            val at = base + element * stride
            if (at + componentSize > bin.limit()) {
                return null
            }

            // Index components are unsigned, so they are widened rather than sign-extended.
            result[element] = when (componentType) {
                GLTF_UNSIGNED_BYTE -> bin.get(at).toInt() and 0xff
                GLTF_UNSIGNED_SHORT -> bin.getShort(at).toInt() and 0xffff
                else -> bin.getInt(at)
            }
        }

        return result
    }

}

/**
 * Returns a copy of this mesh sharing its geometry buffers, so a parsed model can be attached
 * in multiple places without being re-read.
 */
fun Mesh.duplicateGeometry(): Mesh {
    val copy = Mesh(this.name)

    this.geometryOrNull()?.let { source ->
        copy.geometry {
            vertices = source.vertices.duplicate()
            normals = source.normals.duplicate()
            texcoords = source.texcoords.duplicate()
            indices = source.indices.duplicate()
        }
    }

    this.materialOrNull()?.let { source ->
        copy.ifMaterial { diffuse = Vector3f(source.diffuse) }
    }

    this.children.forEach { child ->
        (child as? Mesh)?.let { copy.addChild(it.duplicateGeometry()) }
    }

    return copy
}
