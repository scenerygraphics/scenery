package scenery

import cleargl.GLVector
import coremem.types.NativeTypeEnum
import java.nio.ByteBuffer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
data class GenericTexture(
        var name: String,
        var dimensions: GLVector,
        var channels: Int = 4,
        var type: NativeTypeEnum = NativeTypeEnum.Byte,
        var contents: ByteBuffer,
        var repeatS: Boolean = true,
        var repeatT: Boolean = true
        )
