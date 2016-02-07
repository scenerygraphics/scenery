package scenery

import java.io.File
import java.io.FileInputStream
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

    fun readFromSTL(filename: String) {
        var name: String = ""
        var vbuffer = ArrayList<Float>()
        var nbuffer = ArrayList<Float>()

        System.out.println("reading mesh from " + filename)

        val start = System.nanoTime()
        val lines = File(filename).readLines()

        val readFromAscii = {
            System.out.println("Reading from ASCII file")
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
            System.out.println("Reading from binary file")
            val b = FileInputStream(filename)
            var headerB: ByteArray = ByteArray(80)
            var sizeB: ByteArray = ByteArray(4)
            var buffer: ByteArray = ByteArray(12)
            var size: Int = 0

            System.out.println("reading header & size")

            b.read(headerB, 0, 80)
            System.out.println("reading size")
            b.read(sizeB, 0, 4)

            size = ((sizeB[0].toInt() and 0xFF)
                    or ((sizeB[1].toInt() and 0xFF) shl 8)
                    or ((sizeB[2].toInt() and 0xFF) shl 16)
                    or ((sizeB[3].toInt() and 0xFF) shl 24))

            fun readFloatFromInputStream(fis: FileInputStream): Float {
                var floatBuf = ByteArray(4)
                var bBuf: ByteBuffer

                fis.read(floatBuf, 0, 4)
                bBuf = ByteBuffer.wrap(floatBuf)
                bBuf.order(ByteOrder.LITTLE_ENDIAN)

                return bBuf.float
            }

            System.out.println("size is ${size}")

            val name = String(headerB.copyOfRange(0, headerB.indexOfFirst { it == 0.toByte()  }))
            System.out.println("Found ${name} with size ${size}")

            for(i in 1..size) {
                // surface normal
                nbuffer.add(readFloatFromInputStream(b))
                nbuffer.add(readFloatFromInputStream(b))
                nbuffer.add(readFloatFromInputStream(b))

                // vertices
                for(vertex in 1..3) {
                    vbuffer.add(readFloatFromInputStream(b))
                    vbuffer.add(readFloatFromInputStream(b))
                    vbuffer.add(readFloatFromInputStream(b))
                }

                b.read(buffer, 0, 2)
            }
        }

        if(lines[0].startsWith("solid ")) {
            readFromAscii
        } else {
            readFromBinary
        }.invoke()

        val end = System.nanoTime()
        System.out.println("Read ${vbuffer.size} vertices/${nbuffer.size} normals of model ${name} in ${(end-start)/1e6} ms")

        vertices = vbuffer.toFloatArray()
        normals = nbuffer.toFloatArray()
    }
}
