package graphics.scenery.backends.vulkan

import graphics.scenery.Node
import graphics.scenery.backends.UBO
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * UBO class for Vulkan, providing specific functionality, such as buffer making and UBO buffer creation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanUBO(val device: VulkanDevice, var backingBuffer: VulkanBuffer? = null): AutoCloseable, UBO() {
    var descriptor: UBODescriptor? = null
    var offsets: IntBuffer = memAllocInt(1).put(0)
    var requiredOffsetCount = 0
    private var ownedBackingBuffer: VulkanBuffer? = null

    class UBODescriptor {
        internal var memory: Long = 0
        internal var allocationSize: Long = 0
        internal var buffer: Long = 0
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    fun copy(data: ByteBuffer, offset: Long = 0) {
        val dest = memAllocPointer(1)

        VU.run("Mapping buffer memory/vkMapMemory", { vkMapMemory(device.vulkanDevice, descriptor!!.memory, offset, descriptor!!.allocationSize* 1L, 0, dest) })
        memCopy(memAddress(data), dest.get(0), data.remaining().toLong())

        vkUnmapMemory(device.vulkanDevice, descriptor!!.memory)
        memFree(dest)
    }

    fun populate(offset: Long = 0) {
        val data = if(backingBuffer == null) {
            memAlloc(getSize())
        } else {
            backingBuffer!!.stagingBuffer
        }

        super.populate(data, offset)

        if(backingBuffer == null) {
            data.flip()
            copy(data, offset = offset)
            memFree(data)
        }
    }

    fun fromInstance(node: Node) {
        members.putAll(node.instancedProperties)
    }

    fun createUniformBuffer(): UBODescriptor {
        if(backingBuffer == null) {
            ownedBackingBuffer = VulkanBuffer(device,
                this.getSize() * 1L,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true)

            ownedBackingBuffer?.let { buffer ->
                this.descriptor = UBODescriptor()
                this.descriptor!!.memory = buffer.memory
                this.descriptor!!.allocationSize = buffer.size
                this.descriptor!!.buffer = buffer.vulkanBuffer
                this.descriptor!!.offset = 0L
                this.descriptor!!.range = this.getSize() * 1L
            }
        } else {
            this.descriptor = UBODescriptor()
            this.descriptor!!.memory = backingBuffer!!.memory
            this.descriptor!!.allocationSize = backingBuffer!!.size
            this.descriptor!!.buffer = backingBuffer!!.vulkanBuffer
            this.descriptor!!.offset = 0L
            this.descriptor!!.range = this.getSize() * 1L
        }

        return this.descriptor!!
    }

    @Suppress("unused")
    fun updateBackingBuffer(newBackingBuffer: VulkanBuffer) {
        backingBuffer = newBackingBuffer
        this.descriptor = UBODescriptor()
        this.descriptor!!.memory = backingBuffer!!.memory
        this.descriptor!!.allocationSize = backingBuffer!!.size
        this.descriptor!!.buffer = backingBuffer!!.vulkanBuffer
        this.descriptor!!.offset = 0L
        this.descriptor!!.range = this.getSize() * 1L
    }

    @Suppress("unused")
    fun copyFromStagingBuffer() {
        backingBuffer?.copyFromStagingBuffer()
    }

    override fun close() {
        logger.debug("Closing UBO $this ...")
        if(backingBuffer == null) {
            ownedBackingBuffer?.let {
                logger.debug("Destroying self-owned buffer of $this/$it  ${it.memory.toHexString()})...")
                it.close()
            }
        }

        memFree(offsets)
    }
}
