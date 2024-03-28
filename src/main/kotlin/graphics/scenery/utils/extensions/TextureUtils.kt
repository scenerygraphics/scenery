package graphics.scenery.utils.extensions

import graphics.scenery.backends.vulkan.VulkanTexture
import graphics.scenery.textures.Texture
import graphics.scenery.utils.lazyLogger

fun Texture.fetchTexture(texture: Texture): Int {
    val logger by lazyLogger()

    val ref = VulkanTexture.getReference(texture)
    val buffer = texture.contents ?: return -1

    if(ref != null) {
        val start = System.nanoTime()
        texture.contents = ref.copyTo(buffer, true)
        val end = System.nanoTime()
        logger.info("The request textures of size ${texture.contents?.remaining()?.toFloat()?.div((1024f*1024f))} took: ${(end.toDouble()-start.toDouble())/1000000.0}")
    } else {
        logger.error("In fetchTexture: Texture not accessible")
    }

    return 0
}
