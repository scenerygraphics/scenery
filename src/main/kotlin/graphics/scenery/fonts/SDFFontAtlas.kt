package graphics.scenery.fonts

import cleargl.GLVector
import org.jocl.cl_mem
import graphics.scenery.GeometryType
import graphics.scenery.Hub
import graphics.scenery.Mesh
import graphics.scenery.compute.OpenCLContext
import graphics.scenery.BufferUtils
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Creates renderer-agnostic signed-distance fields (SDF) of fonts
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[hub] The [Hub] to use
 * @property[fontName] The font name to create a SDF for
 * @property[distanceFieldSize] The size of the SDF in pixels
 * @constructor Generates a SDF of the given font
 */
open class SDFFontAtlas(var hub: Hub, val fontName: String, val distanceFieldSize: Int = 64, val maxDistance: Int = 10) {
    /** default charset for the SDF font atlas, default is ASCII charset */
    var charset = (32..127)
    /** Hash map of the char linked to it's width and a byte buffer with the SDF of the char */
    var fontMap = LinkedHashMap<Char, Pair<Float, ByteBuffer>>()
    /** Font size for the SDF */
    var fontSize: Float = 0f
    /** Texcoord storage for each glyph */
    var glyphTexcoords = HashMap<Char, GLVector>()

    /** Font atlas width */
    var atlasWidth = 0
    /** Font atlas height */
    var atlasHeight = 0

    /** Backing store for the font atlas, will finally have a size of 4*atlasWidth*atlasHeight. */
    var fontAtlasBacking: ByteBuffer = ByteBuffer.allocate(1)

    init {
        val ocl = OpenCLContext(hub)
        var input: cl_mem
        var output: cl_mem

        fontSize = distanceFieldSize*0.85f

        val font = Font(fontName, Font.PLAIN, fontSize.toInt())

        charset.map {
            val character =  genCharImage(it.toChar(), font, distanceFieldSize)

            input = ocl.wrapInput(character.second)
            val outputBuffer = ByteBuffer.allocate(4*distanceFieldSize*distanceFieldSize)
            output = ocl.wrapOutput(outputBuffer)

            ocl.loadKernel(OpenCLContext::class.java.getResource("DistanceTransform.cl"), "SignedDistanceTransformByte")
                    .runKernel("SignedDistanceTransformByte",
                            distanceFieldSize*distanceFieldSize,
                            input,
                            output,
                            distanceFieldSize,
                            distanceFieldSize,
                            maxDistance)

            ocl.readBuffer(output, outputBuffer)
            val buf = outputBuffer.duplicate()
            buf.rewind()
            fontMap.put(it.toChar(), Pair(character.first, buf))
        }

        fontAtlasBacking = toFontAtlas(fontMap, distanceFieldSize)
    }

    /**
     * Dumps a given byte buffer to a file. Useful for debugging the SDF
     *
     * @param[buf] The ByteBuffer to dump.
     */
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

    /**
     * Generates an image of a given char with the Java font engine
     *
     * @param[c] The char to generate the image for
     * @param[font] The Java Font object of the font to use
     * @param[size] Size of the image
     * @returns A pair of char width and the image byte buffer
     */
    protected fun genCharImage(c: Char, font: Font, size: Int): Pair<Float, ByteBuffer> {
        /* Creating temporary image to extract character size */
        var image = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)
        var g: Graphics2D = image.createGraphics()
        g.font = font
        val metrics = g.fontMetrics
        g.dispose()

        /* Get char charWidth and charHeight */
        val charWidth = metrics.charWidth(c)

        /* Create image for holding the char */
        image = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        g = image.createGraphics()
        g.font = font
        g.paint = Color.WHITE
        g.drawString(c.toString(), 10, size/2 + metrics.maxAscent/2 - 10)
        g.dispose()

        val data = (image.raster.dataBuffer as DataBufferByte).data

        val imageBuffer: ByteBuffer = ByteBuffer.allocateDirect(data.size)
        imageBuffer.order(ByteOrder.nativeOrder())
        imageBuffer.put(data, 0, data.size)
        imageBuffer.rewind()

        return Pair(charWidth.toFloat()/size.toFloat(), imageBuffer)
    }

    /**
     * Converts a map of chars and SDFs to a square texture that can be used
     * by a renderer.
     *
     * @param[map] map of a char to a pair of glyph width and the SDF byte buffer
     * @param[charSize] Pixel size of each glyph
     * @return A byte buffer containing the full font atlas texture
     */
    protected fun toFontAtlas(map: AbstractMap<Char, Pair<Float, ByteBuffer>>, charSize: Int): ByteBuffer {
        var texWidth = 1
        val mapSize = map.size

        // find power-of-two texture size that fits
        while (texWidth < charSize*Math.sqrt(mapSize.toDouble())) {
            texWidth *= 2
        }
        val texHeight = texWidth

        val buffer = ByteBuffer.allocateDirect(4*texWidth*texWidth)
        val fb = buffer.asFloatBuffer()
        val glyphsPerLine: Int = texWidth/charSize
        val lines: Int = mapSize/glyphsPerLine

        (0..lines).forEach { line ->
            val minGlyphIndex = 0 + glyphsPerLine*line
            val maxGlyphIndex = if(glyphsPerLine*(line+1) - 1 >= mapSize) mapSize - 1 else glyphsPerLine*(line+1) - 1

            (0..charSize-1).forEach {
                (minGlyphIndex..maxGlyphIndex).forEach { glyph ->
                    val char = charset.toList()[glyph].toChar()
                    val charBuffer = map[charset.toList()[glyph].toChar()]!!
                    val glyphWidth = fontMap[char]!!.first

                    val glyphIndexOnLine = glyph-minGlyphIndex
                    glyphTexcoords.putIfAbsent(char, GLVector(
                            (glyphIndexOnLine*charSize*1.0f+12.0f)/texWidth,
                            (line*charSize*1.0f)/texHeight,
                            (glyphIndexOnLine*charSize*1.0f+12.0f)/texWidth+(glyphWidth*charSize*1.0f)/(1.0f*texWidth),
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

    /**
     * Reads a single line from a given buffer and returns the line as float array
     *
     * @param[lineSize] The pixel size of one line
     * @param[line] The number of the line to read
     * @param[buf] The ByteBuffer to read the line from
     * @return FloatArray of the line pixels
     */
    protected fun readLineFromBuffer(lineSize: Int, line: Int, buf: ByteBuffer): FloatArray {
        val array = FloatArray(lineSize)
        val fb = buf.asFloatBuffer()

        fb.position(lineSize*line)
        fb.get(array, 0, lineSize)

        return array
    }

    /**
     * Exposes the font atlas texture to the outside world
     */
    fun getAtlas(): ByteBuffer {
        return fontAtlasBacking
    }

    /**
     * Returns the texcoords for a given glyph
     *
     * @param[glyph] The char to get the texcoords for.
     * @return The char's texcoords.
     */
    fun getTexcoordsForGlyph(glyph: Char): GLVector {
        return glyphTexcoords.getOrDefault(glyph, GLVector(0.0f, 0.0f, 1.0f, 1.0f))
    }

    /**
     * Creates a mesh for a given string, correctly aligning the glyphs on the mesh.
     *
     * @param[text] The text to create a mesh for
     * @return A [Mesh] with the glyphs on it (via texcoords).
     */
    @Suppress("UNCHECKED_CAST")
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
            val glyphWidth = fontMap[char]!!.first

            vertices.addAll(listOf(
                    basex + 0.0f, 0.0f, 0.0f,
                    basex + glyphWidth, 0.0f, 0.0f,
                    basex + glyphWidth, 1.0f, 0.0f,
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
            basex += glyphWidth
            basei += 4
        }

        m.vertices = BufferUtils.allocateFloatAndPut(vertices.toFloatArray())
        m.normals = BufferUtils.allocateFloatAndPut(normals.toFloatArray())
        m.texcoords = BufferUtils.allocateFloatAndPut(texcoords.toFloatArray())
        m.indices = BufferUtils.allocateIntAndPut(indices.toIntArray())

        return m
    }
}
