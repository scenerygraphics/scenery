package graphics.scenery.utils.extensions

import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.textures.Texture
import graphics.scenery.utils.lazyLogger
import org.jetbrains.annotations.ApiStatus.Experimental


/**
 * Fetches this [Texture] from the GPU. This replaced the texture's
 * contents with whatever is fetched from the GPU and returns true.
 * If the texture is not known to the renderer, or no storage buffer
 * is available, it'll return false.
 */
@Experimental
fun Texture.fetchFromGPU(): Boolean {
    val logger by lazyLogger()

    val buffer = this.contents
    if(buffer == null) {
        logger.error("Texture copy from GPU requested, but no storage available.")
        return false
    }

    val ref = VulkanTexture.getReference(this)

    if(ref != null) {
        val start = System.nanoTime()
        this.contents = ref.copyTo(buffer, true)
        val end = System.nanoTime()
        logger.info("The request textures of size ${this.contents?.remaining()?.toFloat()?.div((1024f*1024f))} took: ${(end.toDouble()-start.toDouble())/1000000.0}")
    } else {
        logger.error("In fetchFromGPU: Texture not accessible")
        return false
    }

    return true
}
