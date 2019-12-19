package graphics.scenery.volumes

import graphics.scenery.utils.Image
import graphics.scenery.utils.LazyLogger
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Class for holding RGBA colormaps for volumes
 */
class Colormap(val buffer: ByteBuffer, val width: Int, val height: Int) {

    companion object {
        val logger by LazyLogger()

        /**
         * Creates a new color map from a [ByteBuffer], with dimensions given as [width] and [height].
         */
        fun fromBuffer(buffer: ByteBuffer, width: Int, height: Int): Colormap {
            return Colormap(buffer.duplicate(), width, height)
        }

        /**
         * Creates a new colormap from an [array], with dimensions given as [width] and [height].
         */
        fun fromArray(array: ByteArray, width: Int, height: Int): Colormap {
            return Colormap(ByteBuffer.wrap(array), width, height)
        }

        /**
         * Creates a new colormap from an [stream], with the file type/extension given in [extension].
         */
        fun fromStream(stream: InputStream, extension: String): Colormap {
            val image = Image.readFromStream(stream, extension)
            return Colormap(image.contents, image.width, image.height)
        }

        /**
         * Tries to load a colormap from a file. Available colormaps can be queried with [list].
         */
        fun get(name: String): Colormap {
            val resource = this::class.java.getResourceAsStream("colormap-$name.png")
                    ?: throw FileNotFoundException("Could not find color map for name $name (colormap-$name.png)")

            return fromStream(resource, "png")
        }

        /**
         * Returns a list of strings containing the names of the available color maps for use with [get].
         */
        fun list(): List<String> {
            // FIXME: Hardcoded for the moment, not nice.
            return listOf("grays", "hot", "jet", "plasma", "viridis")
        }
    }
}
