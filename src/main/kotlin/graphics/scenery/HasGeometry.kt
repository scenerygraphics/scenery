package graphics.scenery

import cleargl.GLVector
import org.lwjgl.system.MemoryUtil.memAlloc
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.*

/**
 * Interface for any [Node] that stores geometry in the form of vertices,
 * normals, texcoords, or indices.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface HasGeometry : Serializable {
    /** How many elements does a vertex store? */
    val vertexSize: Int
    /** How many elements does a texcoord store? */
    val texcoordSize: Int
    /** The [GeometryType] of the [Node] */
    var geometryType: GeometryType

    /** Array of the vertices */
    var vertices: FloatBuffer
    /** Array of the normals */
    var normals: FloatBuffer
    /** Array of the texcoords */
    var texcoords: FloatBuffer
    /** Array of the indices */
    var indices: IntBuffer

    /**
     * PreDraw function, to be called before the actual rendering, useful for
     * per-timestep preparation.
     */
    fun preDraw() {

    }

    fun readFrom(filename: String, useMaterial: Boolean = false) {
        val logger = LoggerFactory.getLogger("Node")
        val ext = filename.substringAfterLast(".").toLowerCase()

        when(ext) {
            "obj" -> readFromOBJ(filename, useMaterial)
            "stl" -> readFromSTL(filename)
            else -> {
                logger.error("Unknown file format .$ext for file $filename.")
            }
        }
    }

    /**
     * Reads an OBJ file's material properties from the corresponding MTL file
     *
     * @param[filename] The filename of the MTL file, stored in the OBJ usually
     * @return A HashMap storing material name and [Material].
     */
    fun readFromMTL(filename: String): HashMap<String, Material> {
        val logger = LoggerFactory.getLogger("OBJImporter")

        val materials = HashMap<String, Material>()

        val f = File(filename)
        if (!f.exists()) {
            logger.error("Could not read materials from $filename, file does not exist.")

            vertices = ByteBuffer.allocateDirect(0).asFloatBuffer()
            normals = ByteBuffer.allocateDirect(0).asFloatBuffer()
            texcoords = ByteBuffer.allocateDirect(0).asFloatBuffer()
            indices = ByteBuffer.allocateDirect(0).asIntBuffer()
            return materials
        }

        val lines = Files.lines(FileSystems.getDefault().getPath(filename))
        var currentMaterial: Material? = Material()

        logger.info("Importing materials from MTL file $filename")
        // The MTL file is read line-by-line and tokenized, based on spaces.
        // The first non-whitespace token encountered will be evaluated as command to
        // set up the properties of the [Material], which include e.g. textures and colors.
        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().split(" ").filter(String::isNotEmpty)
            if (tokens.isNotEmpty()) {
                when (tokens[0]) {
                    "#" -> {
                    }
                    "newmtl" -> {
                        currentMaterial = Material()
                        currentMaterial?.name = tokens[1]

                        materials.put(tokens[1], currentMaterial!!)
                    }
                    "Ka" -> currentMaterial?.ambient = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "Kd" -> currentMaterial?.diffuse = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "Ks" -> currentMaterial?.specular = GLVector(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                    "d" -> currentMaterial?.opacity = tokens[1].toFloat()
                    "Tr" -> currentMaterial?.opacity = 1.0f - tokens[1].toFloat()
                    "illum" -> {
                    }
                    "map_Ka" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("ambient", mapfile)
                    }
                    "map_Ks" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("specular", mapfile)
                    }
                    "map_Kd" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("diffuse", mapfile)
                    }
                    "map_d" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("alphamask", mapfile)
                    }
                    "disp" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("displacement", mapfile)
                    }
                    "map_bump", "bump" -> {
                        val mapfile = filename.substringBeforeLast("/") + "/" + tokens[1].replace('\\', '/')
                        currentMaterial!!.textures.put("normal", mapfile)
                    }
                    "Tf" -> {
                    }
                }
            }
        }

        return materials
    }

    /**
     * Read the [Node]'s geometry from an OBJ file, possible including materials
     *
     * @param[filename] The filename to read from.
     * @param[useMTL] Whether a accompanying MTL file shall be used, defaults to true.
     */
    fun readFromOBJ(filename: String, useMTL: Boolean = true) {
        val logger = LoggerFactory.getLogger("OBJImporter")

        var name: String = ""

        var boundingBox: FloatArray? = null

        var vertexCount = 0
        var normalCount = 0
        var uvCount = 0

        var materials = HashMap<String, Material>()

        /**
         * Recalculates normals, assuming CCW winding order.
         *
         * @param[vertexBuffer] The vertex list to use
         * @param[normalBuffer] The buffer to store the normals in
         */
        fun calculateNormals(vertexBuffer: FloatBuffer, normalBuffer: FloatBuffer) {
            var i = 0
            while (i < vertexBuffer.limit() - 1) {
                val v1 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val v2 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val v3 = GLVector(vertexBuffer[i], vertexBuffer[i + 1], vertexBuffer[i + 2])
                i += 3

                val a = v2 - v1
                val b = v3 - v1

                val n = a.cross(b).normalized

                normalBuffer.put(n.x())
                normalBuffer.put(n.y())
                normalBuffer.put(n.z())

                normalBuffer.put(n.x())
                normalBuffer.put(n.y())
                normalBuffer.put(n.z())

                normalBuffer.put(n.x())
                normalBuffer.put(n.y())
                normalBuffer.put(n.z())
            }
        }

        val f = File(filename)
        if (!f.exists()) {
            logger.error("Could not read from $filename, file does not exist.")

            return
        }

        val start = System.nanoTime()
        var lines = Files.lines(FileSystems.getDefault().getPath(filename))

        var count = 0

        var targetObject = this

        val triangleIndices = intArrayOf(0, 1, 2)
        val quadIndices = intArrayOf(0, 1, 2, 0, 2, 3)
        val quintIndices = intArrayOf(0, 1, 2, 0, 2, 3, 0, 3, 4)

        logger.info("Importing geometry from OBJ file $filename")
        // OBJ files are read line-by-line, then tokenized after removing trailing
        // and leading whitespace. The first non-whitespace string encountered is
        // evaluated as command, according to the OBJ spec.
        var currentName = name
        val vertexCountMap = HashMap<String, Int>()

        val preparseStart = System.nanoTime()
        logger.info("Starting preparse")
        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().fastSplit(" ", skipEmpty = true)
            if (tokens.isNotEmpty()) {
                when (tokens[0]) {
                    "" -> {
                    }
                    "#" -> {
                    }
                    "f" -> {
                        vertexCount += when (tokens.count() - 1) {
                            3 -> 6
                            4 -> 9
                            else -> 0
                        }
                    }
                    "g", "o" -> {
                        vertexCountMap.put(currentName, vertexCount)
                        vertexCount = 0
                        currentName = tokens[1]
                    }
                }
            }
        }
        val preparseDuration = (System.nanoTime() - preparseStart)/10e5
        logger.info("Preparse took $preparseDuration ms")

        vertexCountMap.put(currentName, vertexCount)
        vertexCount = 0

        val vertexBuffers = HashMap<String, Triple<FloatBuffer, FloatBuffer, FloatBuffer>>()
        vertexCountMap.forEach { objectName, vertexCount ->
            vertexBuffers.put(objectName, Triple(
                memAlloc(vertexCount * vertexSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                memAlloc(vertexCount * vertexSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(),
                memAlloc(vertexCount * texcoordSize * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            ))
        }

        val tmpV = ArrayList<Float>()
        logger.info("V gets capacity of ${vertexCountMap.values.sum()*vertexSize}")
        tmpV.ensureCapacity(vertexCountMap.values.sum() * vertexSize)
        val tmpN = ArrayList<Float>()
        tmpN.ensureCapacity(vertexCountMap.values.sum() * vertexSize)
        val tmpUV = ArrayList<Float>()
        tmpUV.ensureCapacity(vertexCountMap.values.sum() * texcoordSize)

        lines = Files.lines(FileSystems.getDefault().getPath(filename))

        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().fastSplit(" ", skipEmpty = true)
            if (tokens.isNotEmpty()) {
                when (tokens[0]) {
                    "" -> {
                    }
                    "#" -> {
                    }
                    "mtllib" -> {
                        if (useMTL) {
                            materials = readFromMTL(filename.substringBeforeLast("/") + "/" + tokens[1])
                        }
                    }
                    "usemtl" -> {
                        if (targetObject is Node && useMTL) {
                            (targetObject as Node).material = materials[tokens[1]]!!
                        }
                    }
                // vertices are specified as v x y z
                    "v" -> tokens.subList(1, tokens.size).forEach { tmpV.add(it.toFloat()) }

                // normal coords are specified as vn x y z
                    "vn" -> tokens.subList(1, tokens.size).forEach { tmpN.add(it.toFloat()) }

                // UV coords maybe vt t1 t2 0.0 or vt t1 t2
                    "vt" -> {
                        if (tokens.drop(1).size == 3) {
//                            tokens.drop(1).dropLast(1)
                            tokens.subList(1, tokens.size-1)
                        } else {
//                            tokens.drop(1)
                            tokens.subList(1, tokens.size)
                        }.forEach { tmpUV.add(it.toFloat()) }
                    }

                // faces can reference to three or more vertices in these notations:
                // f v1 v2 ... vn
                // f v1//vn1 v2//vn2 ... vn//vnn
                // f v1/vt1/vn1 v2/vt2/vn2 ... vn/vtn/vnn
                    "f" -> {
                        count++
                        val elements = tokens.drop(1).map { it: String -> it.fastSplit("/") }

                        val vertices = elements.map { it[0].toInt() }
                        val uvs = elements.filter { it.size > 1 && it.getOrElse(1, { "" }).isNotEmpty() }.map { it[1].toInt() }
                        val normals = elements.filter { it.size > 2 && it.getOrElse(2, { "" }).isNotEmpty() }.map { it[2].toInt() }

                        val range = if (vertices.size == 3) {
                            triangleIndices
                        } else if (vertices.size == 4) {
                            quadIndices
                        } else {
                            quintIndices
                        }

                        val coords = (0..vertices.size-1).map { i ->
                            val base = toBufferIndex(tmpV, vertices[i], 3, 0)
                            val v = tmpV.subList(base, base + 3).toFloatArray()

                            if (boundingBox == null) {
                                boundingBox = floatArrayOf(v[0], v[0], v[1], v[1], v[2], v[2])
                            }

                            boundingBox?.let {
                                it[0] = minOf(it[0], v[0])
                                it[2] = minOf(it[2], v[1])
                                it[4] = minOf(it[4], v[2])

                                it[1] = maxOf(it[1], v[0])
                                it[3] = maxOf(it[1], v[1])
                                it[5] = maxOf(it[1], v[2])
                            }

                            val n = if (normals.size == vertices.size) {
                                val baseN = toBufferIndex(tmpN, normals[i], 3, 0)
                                tmpN.subList(baseN, baseN + 3).toFloatArray()
                            } else {
                                floatArrayOf()
                            }

                            val uv = if (uvs.size == vertices.size) {
                                val baseUV = toBufferIndex(tmpUV, uvs[i], 2, 0)
                                tmpUV.subList(baseUV, baseUV + 2).toFloatArray()
                            } else {
                                floatArrayOf()
                            }

                            Triple(v, n, uv)
                        }

                        range.map {
                            vertexBuffers[name]!!.first.put(coords[it].first)
                            vertexBuffers[name]!!.second.put(coords[it].second)
                            vertexBuffers[name]!!.third.put(coords[it].third)
                        }
                    }
                    "s" -> {
                        // TODO: Implement smooth shading across faces
                    }
                    "g", "o" -> @Suppress("UNCHECKED_CAST") {
                        if (vertexBuffers[name]!!.second.position() == 0) {
                            calculateNormals(vertexBuffers[name]!!.first, vertexBuffers[name]!!.second)
                        }

                        targetObject.vertices = vertexBuffers[name]!!.first
                        targetObject.normals = vertexBuffers[name]!!.second
                        targetObject.texcoords = vertexBuffers[name]!!.third
                        targetObject.geometryType = GeometryType.TRIANGLES

                        targetObject.vertices.flip()
                        targetObject.normals.flip()
                        targetObject.texcoords.flip()

                        vertexCount += targetObject.vertices.limit()
                        normalCount += targetObject.normals.limit()
                        uvCount += targetObject.texcoords.limit()

                        if (this is Node) {

                        }

                        // add new child mesh
                        if (this is Mesh) {
                            val child = Mesh()
                            child.name = tokens[1]
                            name = tokens[1]
                            if (!useMTL) {
                                child.material = Material()
                            }

                            boundingBox?.let {
                                (targetObject as Mesh).boundingBoxCoords = it.clone()
                            }
                            boundingBox = null

                            this.addChild(child)
                            targetObject = child
                        }

                    }
                    else -> {
                        if (!tokens[0].startsWith("#")) {
                            logger.warn("Unknown element: ${tokens.joinToString(" ")}")
                        }
                    }
                }
            }
        }

        val end = System.nanoTime()

        // recalculate normals if model did not supply them
        if (vertexBuffers[name]!!.second.position() == 0) {
            logger.warn("Model does not provide surface normals. Recalculating...")
            calculateNormals(vertexBuffers[name]!!.first, vertexBuffers[name]!!.second)
        }

        targetObject.vertices = vertexBuffers[name]!!.first
        targetObject.normals = vertexBuffers[name]!!.second
        targetObject.texcoords = vertexBuffers[name]!!.third

        targetObject.vertices.flip()
        targetObject.normals.flip()
        targetObject.texcoords.flip()

        vertexCount += targetObject.vertices.limit()
        normalCount += targetObject.normals.limit()
        uvCount += targetObject.texcoords.limit()

        if (targetObject is Mesh) {
            if (boundingBox != null) {
                (targetObject as Mesh).boundingBoxCoords = boundingBox!!.clone()
            } else {
                (targetObject as Mesh).boundingBoxCoords = null
            }
        }

        logger.info("Read ${vertexCount / vertexSize}/${normalCount / vertexSize}/${uvCount / texcoordSize} v/n/uv of model $name in ${(end - start) / 1e6} ms")
    }

    private inline fun toBufferIndex(obj: List<Number>, num: Int, vectorSize: Int, offset: Int): Int {
        val index: Int
        if (num >= 0) {
            index = (num - 1) * vectorSize + offset
        } else {
            index = (obj.size / vectorSize + num) * vectorSize + offset
        }

        return index
    }

    private fun String.fastSplit(delim: String, limit: Int = 0, skipEmpty: Boolean = false): List<String> {
        val ch: Char = delim.get(0)
        var off = 0
        val limited = limit > 0
        val list = ArrayList<String>()

        var next = indexOf(ch, off)
        while (next != -1) {
            if (!limited || list.size < limit - 1) {
                if(!(skipEmpty && next - off < 1)) {
                    list.add(substring(off, next))
                }
                off = next + 1
            } else {    // last one
                //assert (list.size() == limit - 1);
                if(!(skipEmpty && this.length - off < 1)) {
                    list.add(substring(off, this.length))
                }
                off = this.length
                break
            }

            next = indexOf(ch, off)
        }
        // If no match was found, return this
        if (off == 0)
            return ArrayList<String>(0)

        // Add remaining segment
        if (!limited || list.size < limit) {
            if (!(skipEmpty && this.length - off < 1)) {
                list.add(substring(off, this.length))
            }
        }

        // Construct result
        var resultSize = list.size
        if (limit == 0) {
            while (resultSize > 0 && list.get(resultSize - 1).length == 0) {
                resultSize--;
            }
        }

        return list.subList(0, resultSize)
    }

    /**
     * Read the [Node]'s geometry from an STL file
     *
     * @param[filename] The filename to read from.
     */
    fun readFromSTL(filename: String) {
        val logger = LoggerFactory.getLogger("STLImporter")

        var name: String = ""
        val vbuffer = ArrayList<Float>()
        val nbuffer = ArrayList<Float>()

        var boundingBox: FloatArray? = null

        val f = File(filename)
        if (!f.exists()) {
            logger.error("Could not read from $filename, file does not exist.")

            return
        }

        val start = System.nanoTime()
        val lines = Files.lines(FileSystems.getDefault().getPath(filename))

        // This lambda is used in case the STL file is stored in ASCII format
        val readFromAscii = {
            logger.info("Importing geometry from ASCII STL file $filename")
            lines.forEach {
                line ->
                val tokens = line.trim().trimEnd().split(" ")
                when (tokens[0]) {
                    "solid" -> name = tokens.drop(1).joinToString(" ")
                    "vertex" -> with(tokens.drop(1)) {
                        val x = get(0).toFloat()
                        val y = get(1).toFloat()
                        val z = get(2).toFloat()

                        if (vbuffer.size == 0 || boundingBox == null) {
                            boundingBox = floatArrayOf(x, x, y, y, z, z)
                        }

                        if (x < boundingBox!![0]) boundingBox!![0] = x
                        if (y < boundingBox!![2]) boundingBox!![2] = y
                        if (z < boundingBox!![4]) boundingBox!![4] = z

                        if (x > boundingBox!![1]) boundingBox!![1] = x
                        if (y > boundingBox!![3]) boundingBox!![3] = y
                        if (z > boundingBox!![5]) boundingBox!![5] = z

                        vbuffer.add(x)
                        vbuffer.add(y)
                        vbuffer.add(z)
                    }
                    "facet" -> tokens.drop(2).forEach { nbuffer.add(it.toFloat()) }
                    "outer" -> {
                        (1..2).forEach { ((nbuffer.size - 3)..(nbuffer.size - 1)).forEach { nbuffer.add(nbuffer[it]) } }
                    }
                    "end" -> {
                    }
                    "endloop" -> {
                    }
                    "endfacet" -> {
                    }
                    "endsolid" -> {
                    }
                    else -> logger.warn("Unknown element: ${tokens.joinToString(" ")}")
                }
            }
        }

        // This lambda is used in case the STL file is stored binary
        val readFromBinary = {
            logger.info("Importing geometry from binary STL file $filename")

            val fis = FileInputStream(filename)
            val bis = BufferedInputStream(fis)
            val headerB: ByteArray = ByteArray(80)
            val sizeB: ByteArray = ByteArray(4)
            val buffer: ByteArray = ByteArray(12)
            val size: Int

            bis.read(headerB, 0, 80)
            bis.read(sizeB, 0, 4)

            size = ((sizeB[0].toInt() and 0xFF)
                or ((sizeB[1].toInt() and 0xFF) shl 8)
                or ((sizeB[2].toInt() and 0xFF) shl 16)
                or ((sizeB[3].toInt() and 0xFF) shl 24))

            fun readFloatFromInputStream(fis: BufferedInputStream): Float {
                val floatBuf = ByteArray(4)
                val bBuf: ByteBuffer

                fis.read(floatBuf, 0, 4)
                bBuf = ByteBuffer.wrap(floatBuf)
                bBuf.order(ByteOrder.LITTLE_ENDIAN)

                return bBuf.float
            }

            for (i in 1..size) {
                // surface normal
                val n1 = readFloatFromInputStream(bis)
                val n2 = readFloatFromInputStream(bis)
                val n3 = readFloatFromInputStream(bis)

                // vertices
                for (vertex in 1..3) {
                    val x = readFloatFromInputStream(bis)
                    val y = readFloatFromInputStream(bis)
                    val z = readFloatFromInputStream(bis)

                    if (vbuffer.size == 0 || boundingBox == null) {
                        boundingBox = floatArrayOf(x, x, y, y, z, z)
                    }

                    if (x < boundingBox!![0]) boundingBox!![0] = x
                    if (y < boundingBox!![2]) boundingBox!![2] = y
                    if (z < boundingBox!![4]) boundingBox!![4] = z

                    if (x > boundingBox!![1]) boundingBox!![1] = x
                    if (y > boundingBox!![3]) boundingBox!![3] = y
                    if (z > boundingBox!![5]) boundingBox!![5] = z

                    vbuffer.add(x)
                    vbuffer.add(y)
                    vbuffer.add(z)

                    nbuffer.add(n1)
                    nbuffer.add(n2)
                    nbuffer.add(n3)
                }

                bis.read(buffer, 0, 2)
            }

            fis.close()
        }

        val arr: CharArray = CharArray(6)
        f.reader().read(arr, 0, 6)

        // If the STL file starts with the string "solid", is must be a ASCII STL file,
        // otherwise it's assumed to be binary.
        if (arr.joinToString("").startsWith("solid ")) {
            readFromAscii
        } else {
            readFromBinary
        }.invoke()

        // normals are incomplete or missing, recalculate
        if (nbuffer.size != vbuffer.size) {
            logger.warn("Model does not supply surface normals, recalculating.")
            nbuffer.clear()

            var i = 0
            while (i < vbuffer.size) {
                val v1 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val v2 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val v3 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
                i += 3

                val a = v2 - v1
                val b = v3 - v1

                val n = a.cross(b).normalized

                (1..3).forEach {
                    nbuffer.add(n.x())
                    nbuffer.add(n.y())
                    nbuffer.add(n.z())
                }
            }
        }


        val end = System.nanoTime()
        logger.info("Read ${vbuffer.size} vertices/${nbuffer.size} normals of model $name in ${(end - start) / 1e6} ms")

        vertices = ByteBuffer.allocateDirect(vbuffer.size * 4).asFloatBuffer()
        normals = ByteBuffer.allocateDirect(nbuffer.size * 4).asFloatBuffer()
        texcoords = ByteBuffer.allocateDirect(0).asFloatBuffer()
        indices = ByteBuffer.allocateDirect(0).asIntBuffer()

        vertices.put(vbuffer.toFloatArray())
        normals.put(nbuffer.toFloatArray())

        vertices.flip()
        normals.flip()

        if (this is Mesh && boundingBox != null) {
            logger.info("Bounding box of $name is ${boundingBox?.joinToString(",")}")
            this.boundingBoxCoords = boundingBox
        }
    }


    /**
     * Recalculates normals, assuming CCW winding order and taking
     * STL's facet storage format into account.
     */
    fun recalculateNormals() {
        var i = 0
        val normals = ArrayList<Float>()

        while (i < vertices.limit()) {
            val v1 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val v2 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val v3 = GLVector(vertices[i], vertices[i + 1], vertices[i + 2])
            i += 3

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalized

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())

            normals.add(n.x())
            normals.add(n.y())
            normals.add(n.z())
        }

        this.normals = BufferUtils.allocateFloatAndPut(normals.toFloatArray())
    }
}
