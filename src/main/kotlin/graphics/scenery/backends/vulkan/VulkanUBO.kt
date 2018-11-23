package graphics.scenery.backends.vulkan

import glm_.L
import graphics.scenery.Node
import graphics.scenery.backends.UBO
import kool.free
import kool.rem
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import vkk.VkBufferUsage
import vkk.VkMemoryProperty
import vkk.`object`.VkBuffer
import vkk.`object`.VkDeviceMemory
import vkk.`object`.VkDeviceSize
import vkk.mappedMemory
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * UBO class for Vulkan, providing specific functionality, such as buffer making and UBO buffer creation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class VulkanUBO(val device: VulkanDevice, var backingBuffer: VulkanBuffer? = null) : AutoCloseable, UBO() {
    /** [UBODescriptor] for this UBO, containing size, memory pointer, etc. */
    var descriptor = UBODescriptor()
        private set
    /** Offsets for this UBO, with respect to the backing buffer. */
    var offsets: IntBuffer = memAllocInt(1).put(0, 0)
    private var closed = false
    private var ownedBackingBuffer: VulkanBuffer? = null
    private var stagingMemory: ByteBuffer? = null

    val vkDev get() = device.vulkanDevice

    /**
     * UBO descriptor class, wrapping memory pointers,
     * allocation sizes, offsets and ranges for the backing buffer.
     */
    class UBODescriptor {
        internal var memory = VkDeviceMemory(NULL)
        internal var allocationSize = VkDeviceSize(0)
        internal var buffer = VkBuffer(0)
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    protected fun copy(data: ByteBuffer, offset: VkDeviceSize = VkDeviceSize(0)) {
        vkDev.mappedMemory(descriptor.memory, offset, descriptor.allocationSize) { dest ->
            data copyTo dest
        }
    }

    /**
     * Populates the [backingBuffer] with the members of this UBO, subject to the determined
     * sizes and alignments. A buffer [offset] can be given. This routine checks via it's super
     * if an actual buffer update is required, and if not, will just set the buffer to the
     * cached position. Otherwise it will serialise all the members into [backingBuffer].
     *
     * Returns true if [backingBuffer] has been updated, and false if not.
     */
    fun populate(offset: VkDeviceSize = VkDeviceSize(0)): Boolean {
        val updated: Boolean
        val buffer = backingBuffer

        if (buffer == null) {
            val data = stagingMemory ?: memAlloc(getSize())
            stagingMemory = data

            updated = super.populate(data, offset.L, elements = null)

            data.flip()
            copy(data, offset)
        } else {
            val sizeRequired = if (sizeCached <= 0) {
                buffer.alignment
            } else {
                sizeCached + buffer.alignment
            }

            if (buffer.stagingBuffer.rem < sizeRequired.i) {
                logger.debug("Resizing $buffer from ${buffer.size} to ${buffer.size.i * 1.5}")
                buffer.resize()
            }

            updated = super.populate(buffer.stagingBuffer, offset.L, elements = null)
        }

        return updated
    }

    /**
     * Populates the [bufferView] with the members of this UBO, subject to the determined
     * sizes and alignments in a parallelized manner. A buffer [offset] can be given, as well as
     * a list of [elements], overriding the UBO's members. This routine checks via it's super
     * if an actual buffer update is required, and if not, will just set the buffer to the
     * cached position. Otherwise it will serialise all the members into [bufferView].
     *
     * Returns true if [bufferView] has been updated, and false if not.
     */
    fun populateParallel(bufferView: ByteBuffer, offset: Long, elements: LinkedHashMap<String, () -> Any>): Boolean {
        bufferView.position(0)
        bufferView.limit(bufferView.capacity())
        return super.populate(bufferView, offset, elements)
    }

    /**
     * Creates this UBO's members from the instancedProperties of [node].
     */
    fun fromInstance(node: Node) {
        node.instancedProperties.forEach { members.putIfAbsent(it.key, it.value) }
    }

    /**
     * Creates a [UBODescriptor] for this UBO and returns it.
     */
    fun createUniformBuffer(): UBODescriptor {
        backingBuffer?.let { buffer ->
            descriptor.memory = buffer.memory
            descriptor.allocationSize = buffer.size
            descriptor.buffer = buffer.vulkanBuffer
            descriptor.offset = 0L
            descriptor.range = this.getSize() * 1L

            return descriptor
        }

        ownedBackingBuffer = VulkanBuffer(device,
            VkDeviceSize(this.getSize().L),
            VkBufferUsage.UNIFORM_BUFFER_BIT.i,
            VkMemoryProperty.HOST_VISIBLE_BIT.i,
            wantAligned = true)

        ownedBackingBuffer?.let { buffer ->
            descriptor = UBODescriptor()
            descriptor.memory = buffer.memory
            descriptor.allocationSize = buffer.size
            descriptor.buffer = buffer.vulkanBuffer
            descriptor.offset = 0L
            descriptor.range = this.getSize() * 1L
        }

        return descriptor
    }

    /**
     * Updates this buffer to use a new [backingBuffer] buffer,
     * given as [newBackingBuffer].
     */
    @Suppress("unused")
    fun updateBackingBuffer(newBackingBuffer: VulkanBuffer) {
        descriptor.memory = newBackingBuffer.memory
        descriptor.allocationSize = newBackingBuffer.size
        descriptor.buffer = newBackingBuffer.vulkanBuffer
        descriptor.offset = 0L
        descriptor.range = this.getSize() * 1L

        backingBuffer = newBackingBuffer
    }

    /**
     * Copies the backing buffer's staging buffer content to the actual backing buffer,
     * used for RAM -> GPU copies.
     */
    @Suppress("unused")
    fun copyFromStagingBuffer() {
        backingBuffer?.copyFromStagingBuffer()
    }

    /**
     * Closes this UBO, freeing staging memory and closing any self-owned backing buffers.
     */
    override fun close() {
        if (closed) {
            return
        }

        logger.trace("Closing UBO $this ...")
        if (backingBuffer == null) {
            ownedBackingBuffer?.let {
                logger.trace("Destroying self-owned buffer of $this/$it  ${it.memory.asHexString})...")
                it.close()
            }
        }

        stagingMemory?.free()

        offsets.free()
        closed = true
    }
}
