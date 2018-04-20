package graphics.scenery.backends.vulkan

import graphics.scenery.Node
import graphics.scenery.backends.UBO
import org.lwjgl.system.MemoryUtil.*
import vkn.*
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * UBO class for Vulkan, providing specific functionality, such as buffer making and UBO buffer creation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanUBO(val device: VulkanDevice, var backingBuffer: VulkanBuffer? = null) : AutoCloseable, UBO() {
    var descriptor: UBODescriptor? = null
    var offsets: IntBuffer = memAllocInt(1).put(0, 0)
    var requiredOffsetCount = 0

    private var ownedBackingBuffer: VulkanBuffer? = null

    class UBODescriptor {
        internal var memory: VkDeviceMemory = NULL
        internal var allocationSize: VkDeviceSize = 0L
        internal var buffer: VkBuffer = NULL
        internal var offset: VkDeviceSize = 0L
        internal var range: VkDeviceSize = 0L
    }

    fun copy(data: ByteBuffer, offset: Long = 0) {

        device.vulkanDevice.mappingMemory(descriptor!!.memory, offset, descriptor!!.allocationSize * 1L, 0) { dest ->
            memCopy(memAddress(data), dest, data.remaining().toLong())
        }
    }

    fun populate(offset: Long = 0L) {
        val data = if (backingBuffer == null) {
            memAlloc(getSize())
        } else {
            backingBuffer!!.stagingBuffer
        }

        super.populate(data, offset, elements = null)

        if (backingBuffer == null) {
            data.flip()
            copy(data, offset = offset)
            memFree(data)
        }
    }

    fun populateParallel(bufferView: ByteBuffer, offset: Long, elements: LinkedHashMap<String, () -> Any>) {
        bufferView.position(0)
        bufferView.limit(bufferView.capacity())
        super.populate(bufferView, offset, elements)
    }

    fun fromInstance(node: Node) {
        node.instancedProperties.forEach { members.putIfAbsent(it.key, it.value) }
    }

    fun createUniformBuffer(): UBODescriptor {
        if (backingBuffer == null) {
            ownedBackingBuffer = VulkanBuffer(device,
                getSize() * 1L,
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT.i,
                wantAligned = true)

            ownedBackingBuffer?.let { buffer ->
                descriptor = UBODescriptor().apply {
                    memory = buffer.memory
                    allocationSize = buffer.size
                    this.buffer = buffer.vulkanBuffer
                    offset = 0L
                    range = getSize() * 1L
                }
            }
        } else {
            if (descriptor == null)
                descriptor = UBODescriptor()
            descriptor!!.apply {
                memory = backingBuffer!!.memory
                allocationSize = backingBuffer!!.size
                buffer = backingBuffer!!.vulkanBuffer
                offset = 0L
                range = getSize() * 1L
            }
        }
        return descriptor!!
    }

    @Suppress("unused")
    fun updateBackingBuffer(newBackingBuffer: VulkanBuffer) {
        backingBuffer = newBackingBuffer

        if (descriptor == null)
            descriptor = UBODescriptor()

        descriptor!!.apply {
            memory = backingBuffer!!.memory
            allocationSize = backingBuffer!!.size
            buffer = backingBuffer!!.vulkanBuffer
            offset = 0L
            range = getSize() * 1L
        }
    }

    @Suppress("unused")
    fun copyFromStagingBuffer() {
        backingBuffer?.copyFromStagingBuffer()
    }

    override fun close() {
        logger.trace("Closing UBO $this ...")
        if (backingBuffer == null) {
            ownedBackingBuffer?.let {
                logger.trace("Destroying self-owned buffer of $this/$it  ${it.memory.toHexString()})...")
                it.close()
            }
        }

        memFree(offsets)
    }
}
