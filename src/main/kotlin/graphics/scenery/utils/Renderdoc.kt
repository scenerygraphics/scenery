package graphics.scenery.utils

import com.sun.jna.Native
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * Minimal Renderdoc integration. At the moment, only loads the Renderdoc library into
 * the processes' address space. Has to be run before any renderers are initialiased.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */

interface RenderdocLibrary : StdCallLibrary {
    fun RENDERDOC_GetAPI(version: Int, out: PointerByReference): Int

    companion object {
        val instance = Native.loadLibrary("renderdoc", RenderdocLibrary::class.java) as RenderdocLibrary
    }
}

enum class RenderdocVersion(val versionNumber: Int) {
    eRENDERDOC_API_Version_1_0_0(10000),    // RENDERDOC_API_1_0_0 = 1 00 00
    eRENDERDOC_API_Version_1_0_1(10001),    // RENDERDOC_API_1_0_1 = 1 00 01
    eRENDERDOC_API_Version_1_0_2(10002),    // RENDERDOC_API_1_0_2 = 1 00 02
    eRENDERDOC_API_Version_1_1_0( 10100),    // RENDERDOC_API_1_1_0 = 1 01 00
    eRENDERDOC_API_Version_1_1_1(10101),    // RENDERDOC_API_1_1_1 = 1 01 01
}

class Renderdoc: AutoCloseable {
    private val logger by LazyLogger()

    init {
        logger.info("Initialising Renderdoc")

        val p = PointerByReference()
        val ret = RenderdocLibrary.instance.RENDERDOC_GetAPI(RenderdocVersion.eRENDERDOC_API_Version_1_1_1.versionNumber, p)

        if(ret != 1) {
            logger.error("Renderdoc attachment failed, return code=$ret")
        } else {
            renderdocAttached = true
        }

    }

    override fun close() {
        logger.debug("Closing Renderdoc")
    }

    companion object {
        var renderdocAttached: Boolean = false
    }
}
