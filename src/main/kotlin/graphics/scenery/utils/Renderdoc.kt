package graphics.scenery.utils

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * Interface for the Windows version of the Renderdoc library.
 */
interface RenderdocWindowsLibrary : StdCallLibrary {
    fun RENDERDOC_GetAPI(version: Int, out: PointerByReference): Int

    companion object {
        val instance = Native.loadLibrary("renderdoc", RenderdocWindowsLibrary::class.java)
            ?: throw IllegalStateException("Could not match interfaces to Renderdoc library (renderdoc.dll)")
    }
}

/**
 * Interface for the Linux version of the Renderdoc library.
 */
interface RenderdocLinuxLibrary : Library {
    fun RENDERDOC_GetAPI(version: Int, out: PointerByReference): Int

    companion object {
        val instance = Native.loadLibrary("renderdoc", RenderdocLinuxLibrary::class.java)
            ?: throw IllegalStateException("Could not match interfaces to Renderdoc library (librenderdoc.so)")
    }
}

/** These enums represent the different Renderdoc versions */
enum class RenderdocVersion(val versionNumber: Int) {
    eRENDERDOC_API_Version_1_0_0(10000),    // RENDERDOC_API_1_0_0 = 1 00 00
    eRENDERDOC_API_Version_1_0_1(10001),    // RENDERDOC_API_1_0_1 = 1 00 01
    eRENDERDOC_API_Version_1_0_2(10002),    // RENDERDOC_API_1_0_2 = 1 00 02
    eRENDERDOC_API_Version_1_1_0(10100),    // RENDERDOC_API_1_1_0 = 1 01 00
    eRENDERDOC_API_Version_1_1_1(10101),    // RENDERDOC_API_1_1_1 = 1 01 01
}

/**
 * Minimal Renderdoc integration. At the moment, only loads the Renderdoc library into
 * the processes' address space. Has to be run before any renderers are initialiased.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class Renderdoc : AutoCloseable {
    private val logger by lazyLogger()

    init {
        logger.info("Initialising Renderdoc")

        val p = PointerByReference()
        when (ExtractsNatives.getPlatform()) {
            ExtractsNatives.Platform.WINDOWS -> {
                val ret = RenderdocWindowsLibrary.instance.RENDERDOC_GetAPI(RenderdocVersion.eRENDERDOC_API_Version_1_1_1.versionNumber, p)

                if (ret != 1) {
                    logger.error("Renderdoc attachment failed, return code=$ret")
                } else {
                    renderdocAttached = true
                }
            }

            ExtractsNatives.Platform.LINUX -> {
                val ret = RenderdocLinuxLibrary.instance.RENDERDOC_GetAPI(RenderdocVersion.eRENDERDOC_API_Version_1_1_1.versionNumber, p)

                if (ret != 1) {
                    logger.error("Renderdoc attachment failed, return code=$ret")
                } else {
                    renderdocAttached = true
                }
            }

            else -> logger.warn("Renderdoc is not supported on this platform, sorry.")
        }
    }

    /** Closes this Renderdoc interface. */
    override fun close() {
        logger.debug("Closing Renderdoc")
    }

    companion object {
        /** Check whether Renderdoc is attached to this scenery instance. */
        var renderdocAttached: Boolean = false
    }
}
