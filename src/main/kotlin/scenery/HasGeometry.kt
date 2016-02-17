package scenery

import cleargl.GLVector
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

interface HasGeometry {
    val vertexSize: Int
    val texcoordSize: Int
    val geometryType: GeometryType

    var vertices: FloatArray
    var normals: FloatArray
    var texcoords: FloatArray
    var indices: IntArray

    fun readFromOBJ(filename: String) {
        var name: String = ""
        var vbuffer = ArrayList<Float>()
        var nbuffer = ArrayList<Float>()
        var tbuffer = ArrayList<Float>()

        var tmpV = ArrayList<Float>()
        var tmpN = ArrayList<Float>()
        var tmpUV = ArrayList<Float>()

        var f = File(filename)
        if(!f.exists()) {
            System.out.println("Could not read from ${filename}, file does not exist.")

            vertices = FloatArray(0)
            normals = FloatArray(0)
            texcoords = FloatArray(0)
            indices = IntArray(0)
            return
        }

        val start = System.nanoTime()
        val inputStream = FileInputStream(filename)
        val lines = BufferedReader(InputStreamReader(inputStream)).readLines()

        inputStream.close()
        var count = 0

        System.out.println("Reading from OBJ file")
        lines.forEach {
            line ->
            val tokens = line.trim().trimEnd().split(" ").filter { it.length > 0 }
            if(tokens.size > 0) {
                when (tokens[0]) {
                    "" -> {
                    }
                    "#" -> {
                    }
                    "mtllib" -> {
                    }
                    "v" -> tokens.drop(1).forEach { tmpV.add(it.toFloat()) }
                    "vn" -> tokens.drop(1).forEach { tmpN.add(it.toFloat()) }
                    "vt" -> tokens.drop(1).dropLast(1).forEach { tmpUV.add(it.toFloat()) }
                    "f" -> {
                        count++
                        val elements = tokens.drop(1).map { it.split("/") }

                        val vertices = elements.map { it[0].toInt() }
                        val uvs = elements.filter { it.size > 1 }. map { it[1].toInt() }
                        val normals = elements.filter { it.size > 2 }.map { it[2].toInt() }

                        var range = ArrayList<Int>()
                        if(vertices.size == 3) {
                            range.addAll((0..vertices.size-1).toList())
                        } else if(vertices.size == 4) {
                            range.add(0)
                            range.add(1)
                            range.add(2)

                            range.add(0)
                            range.add(2)
                            range.add(3)
                        }
                        else {
                            System.err.println("Polygonal triangulation is not yet supported")
                            range.addAll((0..vertices.size-1).toList())
                            // TODO: Implement polygons!
                        }

                        fun toBufferIndex(num: Int, vectorSize: Int, offset: Int): Int {
                            return (num-1)*vectorSize+offset
                        }

                        for(i in range) {
                            vbuffer.add(tmpV.get(toBufferIndex(vertices[i], 3, 0)))
                            vbuffer.add(tmpV.get(toBufferIndex(vertices[i], 3, 1)))
                            vbuffer.add(tmpV.get(toBufferIndex(vertices[i], 3, 2)))

                            if(normals.size == vertices.size) {
                                nbuffer.add(tmpN.get(toBufferIndex(normals[i], 3, 0)))
                                nbuffer.add(tmpN.get(toBufferIndex(normals[i], 3, 1)))
                                nbuffer.add(tmpN.get(toBufferIndex(normals[i], 3, 2)))
                            }

                            if(uvs.size == vertices.size) {
                                tbuffer.add(tmpUV.get(toBufferIndex(uvs[i], 2, 0)))
                                tbuffer.add(tmpUV.get(toBufferIndex(uvs[i], 2, 1)))
                            }
                        }
                    }
                    "s" -> {
                    } // TODO: Implement smooth shading across faces
                    "g" -> {
                    } // TODO: Implement groups
                    "usemtl" -> {
                        // TODO: Implement materials
                    }
                    else -> System.err.println("Unknown element: ${tokens.joinToString(" ")}")
                }
            }
        }

        val end = System.nanoTime()
        System.out.println("Read ${vbuffer.size}/${nbuffer.size}/${tbuffer.size} v/t/uv of model ${name} in ${(end-start)/1e6} ms")

        vertices = vbuffer.toFloatArray()
        normals = nbuffer.toFloatArray()
        texcoords = tbuffer.toFloatArray()
    }

    fun readFromSTL(filename: String) {
        var name: String = ""
        var vbuffer = ArrayList<Float>()
        var nbuffer = ArrayList<Float>()

        var f = File(filename)
        if(!f.exists()) {
            System.out.println("Could not read from ${filename}, file does not exist.")

            vertices = FloatArray(0)
            normals = FloatArray(0)
            texcoords = FloatArray(0)
            indices = IntArray(0)
            return
        }

        val start = System.nanoTime()
        val inputStream = FileInputStream(filename)
        val lines = BufferedReader(InputStreamReader(inputStream)).readLines()

        inputStream.close()

        val readFromAscii = {
            System.out.println("Reading from ASCII STL file")
            lines.forEach {
                line ->
                val tokens = line.trim().trimEnd().split(" ")
                when(tokens[0]) {
                    "solid" -> name = tokens.drop(1).joinToString(" ")
                    "vertex" -> tokens.drop(1).forEach { vbuffer.add(it.toFloat()) }
                    "facet" -> tokens.drop(2).forEach { nbuffer.add(it.toFloat()) }
                    "outer" -> {}
                    "end" -> {}
                    "endloop" -> {}
                    "endfacet" -> {}
                    "endsolid" -> {}
                    else -> System.err.println("Unknown element: ${tokens.joinToString(" ")}")
                }
            }}

        val readFromBinary = {
            System.out.println("Reading from binary STL file")
            val f = FileInputStream(filename)
            val b = BufferedInputStream(f)
            var headerB: ByteArray = ByteArray(80)
            var sizeB: ByteArray = ByteArray(4)
            var buffer: ByteArray = ByteArray(12)
            var size: Int

            b.read(headerB, 0, 80)
            b.read(sizeB, 0, 4)

            size = ((sizeB[0].toInt() and 0xFF)
                    or ((sizeB[1].toInt() and 0xFF) shl 8)
                    or ((sizeB[2].toInt() and 0xFF) shl 16)
                    or ((sizeB[3].toInt() and 0xFF) shl 24))

            fun readFloatFromInputStream(fis: BufferedInputStream): Float {
                var floatBuf = ByteArray(4)
                var bBuf: ByteBuffer

                fis.read(floatBuf, 0, 4)
                bBuf = ByteBuffer.wrap(floatBuf)
                bBuf.order(ByteOrder.LITTLE_ENDIAN)

                return bBuf.float
            }

            val name = String(headerB.copyOfRange(0, headerB.indexOfFirst { it == 0.toByte()  }))

            for (i in 1..size) {
                // surface normal
//                                nbuffer.add(readFloatFromInputStream(b))
//                                nbuffer.add(readFloatFromInputStream(b))
//                                nbuffer.add(readFloatFromInputStream(b))
                readFloatFromInputStream(b)
                readFloatFromInputStream(b)
                readFloatFromInputStream(b)

                // vertices
                for (vertex in 1..3) {
                    vbuffer.add(readFloatFromInputStream(b))
                    vbuffer.add(readFloatFromInputStream(b))
                    vbuffer.add(readFloatFromInputStream(b))
                }

                b.read(buffer, 0, 2)
            }

            f.close()
        }

        if(lines[0].startsWith("solid ")) {
            readFromAscii
        } else {
            readFromBinary
        }.invoke()

        var i = 0
        while(i < vbuffer.size) {
            val v1 = GLVector(vbuffer[i], vbuffer[i + 1], vbuffer[i + 2])
            i += 3

            val v2 = GLVector(vbuffer[i], vbuffer[i+1], vbuffer[i+2])
            i += 3

            val v3 = GLVector(vbuffer[i], vbuffer[i+1], vbuffer[i+2])
            i += 3

            val a = v2 - v1
            val b = v3 - v1

            val n = a.cross(b).normalized

            nbuffer.add(n.x())
            nbuffer.add(n.y())
            nbuffer.add(n.z())
        }


        val end = System.nanoTime()
        System.out.println("Read ${vbuffer.size} vertices/${nbuffer.size} normals of model ${name} in ${(end-start)/1e6} ms")

        vertices = vbuffer.toFloatArray()
        normals = nbuffer.toFloatArray()
    }
}
