package vkk.`object`

import glm_.i
import glm_.set
import kool.Ptr
import kool.adr
import kool.rem
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.system.Pointer
import uno.buffer.longBufferOf
import uno.kotlin.buffers.isEmpty
import uno.kotlin.buffers.isNotEmpty
import vkk.VkDynamicState
import vkk.adr
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

interface VkObject : Comparable<Long> {
    val L: Long

//    fun isInvalid(): Boolean = L == NULL
//    fun isValid(): Boolean = L != NULL TODO BUG

    infix operator fun plus(other: Long) = L + other
    infix operator fun minus(other: Long) = L - other
    infix operator fun times(other: Long) = L * other
    infix operator fun div(other: Long) = L / other

    override fun compareTo(other: Long): Int = L.compareTo(other)
}

infix operator fun Long.plus(other: VkObject) = plus(other.L)
infix operator fun Long.minus(other: VkObject) = minus(other.L)
infix operator fun Long.times(other: VkObject) = times(other.L)
infix operator fun Long.div(other: VkObject) = div(other.L)

fun bufferBig(size: VkDeviceSize): ByteBuffer = kool.bufferBig(size.L.i)

fun memCopy(src: Ptr, dst: Ptr, bytes: VkDeviceSize) = memCopy(src, dst, bytes.L)

fun Pointer.isInvalid() = adr == NULL
fun Pointer.isValid() = adr != NULL

inline class VkBuffer(override val L: Long) : VkObject
inline class VkBufferView(override val L: Long) : VkObject
inline class VkCommandPool(override val L: Long) : VkObject {
    val isValid get() = L != NULL
    val isInvalid get() = L == NULL
}

inline class VkDebugReportCallback(override val L: Long) : VkObject
inline class VkDescriptorPool(override val L: Long) : VkObject
inline class VkDescriptorSet(override val L: Long) : VkObject
inline class VkDescriptorSetLayout(override val L: Long) : VkObject
inline class VkDeviceMemory(override val L: Long) : VkObject
inline class VkDeviceSize(override val L: Long) : VkObject {

    operator fun plus(size: VkDeviceSize) = VkDeviceSize(L + size.L)
    operator fun plus(size: Int) = VkDeviceSize(L + size)

    val isEmpty get() = L == 0L
    val isNotEmpty get() = L != 0L
}

inline class VkEvent(override val L: Long) : VkObject
inline class VkFence(override val L: Long) : VkObject
inline class VkFramebuffer(override val L: Long) : VkObject
inline class VkImage(override val L: Long) : VkObject
inline class VkImageView(override val L: Long) : VkObject
inline class VkPipeline(override val L: Long) : VkObject
inline class VkPipelineCache(override val L: Long) : VkObject
inline class VkPipelineLayout(override val L: Long) : VkObject
inline class VkQueryPool(override val L: Long) : VkObject
inline class VkRenderPass(override val L: Long) : VkObject
inline class VkSampler(override val L: Long) : VkObject
inline class VkSemaphore(override val L: Long) : VkObject {
    val isValid get() = L != NULL
    val isInvalid get() = L == NULL
}
inline class VkShaderModule(override val L: Long) : VkObject
inline class VkSurface(override val L: Long) : VkObject // TODO -> KHR
inline class VkSwapchainKHR(override val L: Long) : VkObject

typealias VkBufferBuffer = LongBuffer
typealias VkDescriptorSetBuffer = LongBuffer
typealias VkDeviceMemoryBuffer = LongBuffer
typealias VkDeviceSizeBuffer = LongBuffer

inline class VkSemaphoreBuffer(val buffer: LongBuffer) {
    operator fun get(index: Int) = VkSemaphore(buffer[index])

    fun isNotEmpty() = buffer.isNotEmpty()
    fun isEmpty() = buffer.isEmpty()

    val rem get() = buffer.rem
    val adr get() = buffer.adr
}

fun vkSemaphoreBufferOf(semaphore: VkSemaphore) = VkSemaphoreBuffer(longBufferOf(semaphore.L))
fun MemoryStack.vkSemaphoreBufferOf(semaphore: VkSemaphore) = VkSemaphoreBuffer(longs(semaphore.L))

typealias VkSwapchainKhrBuffer = LongBuffer
//typealias VkResultBuffer = IntBuffer
typealias VkSamplerBuffer = LongBuffer

typealias VkCommandPoolArray = LongArray

inline class VkFenceArray(val array: LongArray) : Iterable<VkFence> {

    operator fun get(index: Int) = VkFence(array[index])
    operator fun set(index: Int, fence: VkFence) = array.set(index, fence.L)

    val size get() = array.size
    val indices get() = array.indices

    override operator fun iterator() = VkFenceArrayIterator()

    inner class VkFenceArrayIterator : Iterator<VkFence> {
        private var index = 0
        override fun hasNext() = index < array.size
        override fun next() =
            try {
                VkFence(array[index++])
            } catch (e: ArrayIndexOutOfBoundsException) {
                index -= 1
                throw NoSuchElementException(e.message)
            }
    }
}

fun initVkFenceArray(size: Int, block: (Int) -> VkFence) = VkFenceArray(LongArray(size) { block(it).L })
fun vkFenceArrayOf() = VkFenceArray(LongArray(0))
fun vkFenceArrayBig(size: Int) = VkFenceArray(LongArray(size))

inline class VkFramebufferArray(val array: LongArray) : Iterable<VkFramebuffer> {

    operator fun get(index: Int) = VkFramebuffer(array[index])
    operator fun set(index: Int, framebuffer: VkFramebuffer) = array.set(index, framebuffer.L)

    val size get() = array.size
    val indices get() = array.indices

    override operator fun iterator() = VkFramebufferArrayIterator()

    inner class VkFramebufferArrayIterator : Iterator<VkFramebuffer> {
        private var index = 0
        override fun hasNext() = index < array.size
        override fun next() =
            try {
                VkFramebuffer(array[index++])
            } catch (e: ArrayIndexOutOfBoundsException) {
                index -= 1
                throw NoSuchElementException(e.message)
            }
    }
}

fun initVkFramebufferArray(size: Int, block: (Int) -> VkFramebuffer) = VkFramebufferArray(LongArray(size) { block(it).L })
fun vkFramebufferArrayOf() = VkFramebufferArray(LongArray(0))

inline class VkImageArray(val array: LongArray) : Iterable<VkImage> {

    operator fun get(index: Int) = VkImage(array[index])
    operator fun set(index: Int, image: VkImage) = array.set(index, image.L)

    val size get() = array.size
    val indices get() = array.indices

    override operator fun iterator() = VkImageArrayIterator()

    inner class VkImageArrayIterator : Iterator<VkImage> {
        private var index = 0
        override fun hasNext() = index < array.size
        override fun next() =
            try {
                VkImage(array[index++])
            } catch (e: ArrayIndexOutOfBoundsException) {
                index -= 1
                throw NoSuchElementException(e.message)
            }
    }
}

fun VkImageArray(size: Int, block: (Int) -> VkImage) = VkImageArray(LongArray(size) { block(it).L })
fun VkImageArray(size: Int) = VkImageArray(LongArray(size))
fun VkImageArray(images: Collection<VkImage>) = VkImageArray(LongArray(images.size) { images.elementAt(it).L })
fun VkImageArray() = VkImageArray(LongArray(0))

inline class VkImageViewArray(val array: LongArray) : Iterable<VkImageView> {

    operator fun get(index: Int) = VkImageView(array[index])
    operator fun set(index: Int, imageView: VkImageView) = array.set(index, imageView.L)

    val size get() = array.size
    val indices get() = array.indices

    override operator fun iterator() = VkImageViewArrayIterator()

    inner class VkImageViewArrayIterator : Iterator<VkImageView> {
        private var index = 0
        override fun hasNext() = index < array.size
        override fun next() =
            try {
                VkImageView(array[index++])
            } catch (e: ArrayIndexOutOfBoundsException) {
                index -= 1
                throw NoSuchElementException(e.message)
            }
    }
}

fun VkImageViewArray(size: Int, block: (Int) -> VkImageView) = VkImageViewArray(LongArray(size) { block(it).L })
fun VkImageViewArray(size: Int) = VkImageViewArray(LongArray(size))
fun VkImageViewArray() = VkImageViewArray(LongArray(0))

inline class VkSemaphoreArray(val array: LongArray) {
    operator fun get(index: Int) = VkSemaphore(array[index])
}

fun VkSemaphoreArray(size: Int, init: (Int) -> VkSemaphore) = VkSemaphoreArray(LongArray(size) { init(it).L })
fun VkSemaphoreArray(semaphores: Collection<VkSemaphore>) = VkSemaphoreArray(semaphores.size) { semaphores.elementAt(it) }


inline class VkImageViewBuffer(val buffer: LongBuffer) {

    operator fun set(index: Int, imageView: VkImageView) {
        buffer[index] = imageView.L
    }
}

fun MemoryStack.vkImageViewBufferBig(size: Int) = VkImageViewBuffer(callocLong(size))
fun MemoryStack.vkImageViewBufferOf(imageView: VkImageView): VkImageViewBuffer =
    VkImageViewBuffer(longs(imageView.L))

fun MemoryStack.vkImageViewBufferOf(imageView0: VkImageView,
                                    imageView1: VkImageView): VkImageViewBuffer =
    VkImageViewBuffer(longs(imageView0.L, imageView1.L))

fun MemoryStack.vkImageViewBufferOf(imageView0: VkImageView,
                                    imageView1: VkImageView,
                                    imageView2: VkImageView): VkImageViewBuffer =
    VkImageViewBuffer(longs(imageView0.L, imageView1.L, imageView2.L))

inline class VkDynamicStateBuffer(val buffer: IntBuffer) {

    operator fun set(index: Int, imageView: VkDynamicState) {
        buffer[index] = imageView.i
    }
}

fun MemoryStack.vkDynamicStateBufferBig(size: Int) = VkDynamicStateBuffer(callocInt(size))
fun MemoryStack.vkDynamicStateBufferOf(dynamicStates: Collection<VkDynamicState>): VkDynamicStateBuffer {
    val buffer = vkDynamicStateBufferBig(dynamicStates.size)
    for (i in dynamicStates.indices)
        buffer[i] = dynamicStates.elementAt(i)
    return buffer
}

fun MemoryStack.vkDynamicStateBufferOf(dynamicState: VkDynamicState): VkDynamicStateBuffer = VkDynamicStateBuffer(ints(dynamicState.i))

fun MemoryStack.vkDynamicStateBufferOf(dynamicState0: VkDynamicState,
                                       dynamicState1: VkDynamicState): VkDynamicStateBuffer =
    VkDynamicStateBuffer(ints(dynamicState0.i, dynamicState1.i))

fun MemoryStack.vkDynamicStateBufferOf(dynamicState0: VkDynamicState,
                                       dynamicState1: VkDynamicState,
                                       dynamicState2: VkDynamicState): VkDynamicStateBuffer =
    VkDynamicStateBuffer(ints(dynamicState0.i, dynamicState1.i, dynamicState2.i))



typealias VkResultBuffer = IntBuffer
