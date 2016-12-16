package graphics.scenery.scenery

import cleargl.GLTypeEnum
import cleargl.GLVector
import java.nio.ByteBuffer

/**
 * Data class for storing renderer-agnostic textures
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
data class GenericTexture(
    /** Name of the texture, might e.g. be "diffuse" */
    var name: String,
    /** Dimensions of the texture in pixels */
    var dimensions: GLVector,
    /** The texture's number of channels */
    var channels: Int = 4,
    /** [NativeTypeEnum] declaring the data type stored in [contents] */
    var type: GLTypeEnum = GLTypeEnum.Byte,
    /** Byte contents of the texture */
    var contents: ByteBuffer,
    /** Shall the texture be repeated on the U/S coordinate? */
    var repeatS: Boolean = true,
    /** Shall the texture be repeated on the V/T coordinate? */
    var repeatT: Boolean = true
)
