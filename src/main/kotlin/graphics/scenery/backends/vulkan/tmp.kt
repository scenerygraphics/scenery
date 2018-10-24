package graphics.scenery.backends.vulkan

import glm_.L
import glm_.i
import kool.Ptr
import kool.adr
import kool.remSize
import org.lwjgl.system.MemoryUtil
import vkk.VkBuffer
import vkk.VkDeviceMemory
import vkk.VkDeviceSize
import java.nio.Buffer

operator fun VkDeviceSize.rem(b: VkDeviceSize) = VkDeviceSize(L % b.L)
val VkDeviceSize.i
    get() = L.i

operator fun Int.rem(b: VkDeviceSize) = VkDeviceSize(this % b.L)
operator fun Int.plus(b: VkDeviceSize) = VkDeviceSize(this + b.L)
operator fun VkDeviceSize.minus(b: VkDeviceSize) = VkDeviceSize(L - b.L)
operator fun VkDeviceSize.times(b: Int) = VkDeviceSize(L * b)

inline var Buffer.lim: Int
    get() = limit()
    set(value) {
        limit(value)
    }

infix fun Buffer.copyTo(ptr: Ptr) = MemoryUtil.memCopy(adr, ptr, remSize.L)
infix fun Buffer.copyFrom(ptr: Ptr) = MemoryUtil.memCopy(ptr, adr, remSize.L)

val VkDeviceMemory.asHexString: String
    get() = "0x%X".format(L)
val VkBuffer.asHexString: String
    get() = "0x%X".format(L)
