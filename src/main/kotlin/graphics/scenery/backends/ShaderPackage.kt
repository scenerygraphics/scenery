package graphics.scenery.backends

import graphics.scenery.utils.lazyLogger
import graphics.scenery.utils.SystemHelpers.Companion.toIntArray
import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer
import java.util.*
import kotlin.experimental.and

/**
 * Data class to contain packages of shader source code and SPIRV byte code.
 * [type] indicates the shader type. [priority] is set upon initialisation,
 * after it has been determined whether the plain source or SPIRV is newer.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
data class ShaderPackage(val baseClass: Class<*>,
                         val type: ShaderType,
                         val spirvPath: String?,
                         val codePath: String?,
                         val spirv: ByteArray?,
                         val code: String?,
                         var priority: SourceSPIRVPriority,
                         val disableCaching: Boolean = false) {
    private val logger by lazyLogger()

    init {
        val sourceNewer = if(code != null) {
            val codeDate = Date(baseClass.getResource(codePath).openConnection().lastModified)
            val spirvDate = if(spirv != null) {
                logger.debug("base class for path=$spirvPath=$baseClass")
                val res = baseClass.getResource(spirvPath)
                if(res == null) {
                    Date(0)
                } else {
                    Date(res.openConnection().lastModified + 500)
                }
            } else {
                Date(0)
            }

            codeDate.after(spirvDate)
        } else {
            false
        }

        priority = if(sourceNewer) {
            SourceSPIRVPriority.SourcePriority
        } else {
            SourceSPIRVPriority.SPIRVPriority
        }
    }

    /**
     * Returns the glslang-digestible SPIRV bytecode from this package.
     */
    fun getSPIRVOpcodes(): IntArray? {
        var i = 0
        spirv?.let { spv ->
            return spv.toIntArray()
        }

        return null
    }

    /**
     * Returns a short string representation of this package.
     */
    fun toShortString(): String {
        return "${this.codePath}/${this.spirvPath}/${this.type}"
    }
}
