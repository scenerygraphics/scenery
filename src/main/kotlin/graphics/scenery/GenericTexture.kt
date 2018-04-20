package graphics.scenery

import cleargl.GLTypeEnum
import cleargl.GLVector
import java.io.Serializable
import java.nio.ByteBuffer

/**
 * Data class for storing renderer-agnostic textures
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
data class GenericTexture @JvmOverloads constructor(
    /** Name of the texture, might e.g. be "diffuse" */
    var name: String,
    /** Dimensions of the texture in pixels */
    var dimensions: GLVector,
    /** The texture's number of channels */
    var channels: Int = 4,
    /** [NativeTypeEnum] declaring the data type stored in [contents] */
    var type: GLTypeEnum = GLTypeEnum.UnsignedByte,
    /** Byte contents of the texture */
    @Transient var contents: ByteBuffer,
    /** Shall the texture be repeated on the U/S coordinate? */
    var repeatS: Boolean = true,
    /** Shall the texture be repeated on the V/T coordinate? */
    var repeatT: Boolean = true,
    /** Should the texture data be interpreted as normalized? Default is true, non-normalisation is better for volume data, though */
    var normalized: Boolean = true,
    /** Should mipmaps be generated? */
    var mipmap: Boolean = true
) : Serializable
