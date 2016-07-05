package scenery.fonts

import cleargl.GLVector
import org.jocl.cl_mem
import scenery.GeometryType
import scenery.Hub
import scenery.Mesh
import scenery.compute.OpenCLContext
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SDFFontAtlas(var hub: Hub, val fontName: String, val distanceFieldSize: Int = 64) {
    var charset = (32..127)
    var fontMap = LinkedHashMap<Char, Pair<Float, ByteBuffer>>()
    var fontSize: Float = 0f
    var glyphTexcoords = HashMap<Char, GLVector>()

    var atlasWidth = 0
    var atlasHeight = 0

    var fontAtlasBacking: ByteBuffer = ByteBuffer.allocate(1)

    init {
        val ocl = OpenCLContext(hub, devicePreference = "0,0")
        var input: cl_mem
        var output: cl_mem

        fontSize = distanceFieldSize*0.85f

        charset.map {
            var start = System.nanoTime()
            val character =  genCharImage(it.toChar(), Font(fontName, 0, fontSize.toInt()), distanceFieldSize)

            input = ocl.wrapInput(character.second)
            val outputBuffer = ByteBuffer.allocate(4*distanceFieldSize*distanceFieldSize)
            output = ocl.wrapOutput(outputBuffer)

            ocl.loadKernel(OpenCLContext::class.java.getResource("DistanceTransform.cl"), "SignedDistanceTransformByte")
                    .runKernel("SignedDistanceTransformByte",
                            distanceFieldSize*distanceFieldSize,
                            false,
                            input,
                            output,
                            distanceFieldSize,
                            distanceFieldSize)

            ocl.readBuffer(output, outputBuffer)
            val buf = outputBuffer.duplicate()
            buf.rewind()
            fontMap.put(it.toChar(), Pair(character.first, buf))
        }

        fontAtlasBacking = toFontAtlas(fontMap, distanceFieldSize)
//        dumpToFile(fontAtlasBacking)
    }

    fun dumpToFile(buf: ByteBuffer) {
        try {
            val file = File(System.getProperty("user.home") + "/SDFFontAtlas-${System.currentTimeMillis()}-$fontName.raw")
            val channel = FileOutputStream(file, false).channel
            buf.rewind()
            channel.write(buf)
            channel.close()
        } catch (e: Exception) {
            System.err.println("Unable to dump " + this.fontName)
            e.printStackTrace()
        }

    }

    protected fun genCharImage(c: Char, font: Font, size: Int): Pair<Float, ByteBuffer> {
        /* Creating temporary image to extract character size */
        var image = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)
        var g: Graphics2D = image.createGraphics();
        g.setFont(font);
        val metrics = g.getFontMetrics();
        g.dispose();

        /* Get char charWidth and charHeight */
        val charWidth = metrics.charWidth(c);
        val charHeight = metrics.getHeight();

        /* Create image for holding the char */
        image = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        g = image.createGraphics()
        g.setFont(font);
        g.setPaint(java.awt.Color.WHITE);
        g.drawString(c.toString(), 10, size/2 + metrics.maxAscent/2 - 10)
        g.dispose();

        val data = (image.getRaster().getDataBuffer() as DataBufferByte).data

        var imageBuffer: ByteBuffer = ByteBuffer.allocateDirect(data.size)
        imageBuffer.order(ByteOrder.nativeOrder())
        imageBuffer.put(data, 0, data.size)
        imageBuffer.rewind()

        return Pair(charWidth.toFloat()/size.toFloat(), imageBuffer)
    }

    protected fun toFontAtlas(map: AbstractMap<Char, Pair<Float, ByteBuffer>>, charSize: Int): ByteBuffer {
        var texWidth = 1
        val mapSize = map.size

        // find power-of-two texture size that fits
        while (texWidth < charSize*Math.sqrt(mapSize.toDouble())) {
            texWidth *= 2
        }
        var texHeight = texWidth

        val buffer = ByteBuffer.allocate(4*texWidth*texWidth)
        val fb = buffer.asFloatBuffer()
        val glyphsPerLine: Int = texWidth/charSize
        val lines: Int = mapSize/glyphsPerLine

        System.err.println("Figured texture size for $mapSize should be $texWidth^2, $glyphsPerLine/line, $lines lines")

        (0..lines).forEach { line ->
            val minGlyphIndex = 0 + glyphsPerLine*line
            val maxGlyphIndex = if(glyphsPerLine*(line+1) - 1 >= mapSize) mapSize - 1 else glyphsPerLine*(line+1) - 1

            System.err.println("$line: $minGlyphIndex -> $maxGlyphIndex")

            (0..charSize-1).forEach {
                (minGlyphIndex..maxGlyphIndex).forEach { glyph ->
                    val char = charset.toList().get(glyph).toChar()
                    val charBuffer = map.get(charset.toList().get(glyph).toChar())!!

                    val glyphIndexOnLine = glyph-minGlyphIndex
                    glyphTexcoords.putIfAbsent(char, GLVector(
                            (glyphIndexOnLine*charSize*1.0f)/texWidth,
                            (line*charSize*1.0f)/texHeight,
                            (glyphIndexOnLine*charSize*1.0f)/texWidth+(charSize*1.0f)/(1.0f*texWidth),
                            (line*charSize*1.0f+charSize*1.0f)/texHeight))
                    fb.put(readLineFromBuffer(charSize, it, charBuffer.second))
                }
            }
        }

        buffer.rewind()

        atlasWidth = texWidth
        atlasHeight = texHeight

        return buffer
    }

    protected fun readLineFromBuffer(lineSize: Int, line: Int, buf: ByteBuffer): FloatArray {
        val array = FloatArray(lineSize)
        val fb = buf.asFloatBuffer()

        fb.position(lineSize*line)
        fb.get(array, 0, lineSize)

        return array
    }

    fun getAtlas(): ByteBuffer {
        return fontAtlasBacking
    }

    fun getTexcoordsForGlyph(glyph: Char): GLVector {
        return glyphTexcoords.getOrDefault(glyph, GLVector(0.0f, 0.0f, 1.0f, 1.0f))
    }

    fun createMeshForString(text: String): Mesh {
        val m = Mesh()
        m.geometryType = GeometryType.TRIANGLES

        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val texcoords = ArrayList<Float>()
        val indices = ArrayList<Int>()

        var basex = 0.0f
        var basei = 0

        text.toCharArray().forEachIndexed { index, char ->
            vertices.addAll(listOf(
                    basex + 0.0f, 0.0f, 0.0f,
                    basex + 1.0f, 0.0f, 0.0f,
                    basex + 1.0f, 1.0f, 0.0f,
                    basex + 0.0f, 1.0f, 0.0f
            ))

            normals.addAll(listOf(
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f
            ))

            indices.addAll(listOf(
                    basei + 0, basei + 1, basei + 2,
                    basei + 0, basei + 2, basei + 3
            ))

            val glyphTexCoords = getTexcoordsForGlyph(char)

            texcoords.addAll(listOf(
                    glyphTexCoords.x(), glyphTexCoords.w(),
                    glyphTexCoords.z(), glyphTexCoords.w(),
                    glyphTexCoords.z(), glyphTexCoords.y(),
                    glyphTexCoords.x(), glyphTexCoords.y()
            ))

            // add font width as new base size
            basex += fontMap.get(char)!!.first
            basei += 4
        }

        m.vertices = (vertices.clone() as ArrayList<Float>).toFloatArray()
        m.normals = (normals.clone() as ArrayList<Float>).toFloatArray()
        m.texcoords = (texcoords.clone() as ArrayList<Float>).toFloatArray()
        m.indices = (indices.clone() as ArrayList<Int>).toIntArray()

        return m
    }
}