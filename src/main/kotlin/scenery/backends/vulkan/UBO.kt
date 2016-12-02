package scenery.backends.vulkan

import cleargl.GLMatrix
import cleargl.GLVector
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class UBO(val device: VkDevice) {
    var name = ""
    var members = LinkedHashMap<String, Any>()
    var descriptor: UBODescriptor? = null
    var offsets: IntBuffer? = null
    var logger = LoggerFactory.getLogger("VulkanRenderer")

    private var currentPointer: PointerBuffer? = null
    private var currentPosition = 0L

    class UBODescriptor {
        internal var memory: Long = 0
        internal var allocationSize: Long = 0
        internal var buffer: Long = 0
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    fun getSize(): Int {
        val sizes = members.map {
            when (it.value.javaClass) {
                GLMatrix::class.java -> (it.value as GLMatrix).floatArray.size * 4
                GLVector::class.java -> (it.value as GLVector).toFloatArray().size * 4
                java.lang.Float::class.java -> 4
                Float::class.java -> 4
                Double::class.java -> 8
                java.lang.Double::class.java -> 8
                Int::class.java -> 4
                Integer::class.java -> 4
                java.lang.Integer::class -> 4
                Short::class.java -> 2
                Boolean::class.java -> 4
                java.lang.Boolean::class.java -> 4
                else -> 0
            }
        }

        return sizes.sum()
    }

    fun getPointerBuffer(size: Int): ByteBuffer {
        if (currentPointer == null) {
            this.map()
        }

        val buffer = memByteBuffer(currentPointer!!.get(0) + currentPosition, size)
        currentPosition += size * 1L

        return buffer
    }

    fun copy(data: ByteBuffer) {
        val dest = memAllocPointer(1)
        vkMapMemory(device, descriptor!!.memory, 0, descriptor!!.allocationSize* 1L, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining())
        vkUnmapMemory(device, descriptor!!.memory)
        memFree(dest)
    }

    fun populate() {
        val data = memAlloc(getSize())

        members.forEach {
            when(it.value.javaClass) {
                GLMatrix::class.java -> (it.value as GLMatrix).put(data)
                GLVector::class.java -> (it.value as GLVector).put(data)
                Float::class.java -> {
                    data.asFloatBuffer().put(it.value as Float)
                    data.position(data.position() + 4)
                }
                Double::class.java -> {
                    data.asDoubleBuffer().put(it.value as Double)
                    data.position(data.position() + 8)
                }
                Integer::class.java -> {
                    data.asIntBuffer().put(it.value as Int)
                    data.position(data.position() + 4)
                }
                Int::class.java -> {
                    data.asIntBuffer().put(it.value as Int)
                    data.position(data.position() + 4)
                }
                Short::class.java -> {
                    data.asShortBuffer().put(it.value as Short)
                    data.position(data.position() + 2)
                }
                Boolean::class.java -> {
                    data.asIntBuffer().put(it.value as Int)
                    data.position(data.position() + 4)
                }
            }
        }

        val dest = memAllocPointer(1)
        vkMapMemory(device, descriptor!!.memory, 0, descriptor!!.allocationSize* 1L, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining())
        vkUnmapMemory(device, descriptor!!.memory)

        memFree(dest)
        memFree(data)
    }

    fun map(): PointerBuffer {
        val dest = memAllocPointer(1)
        vkMapMemory(device, descriptor!!.memory, 0, descriptor!!.allocationSize* 1L, 0, dest)

        currentPointer = dest
        return dest
    }

    fun createUniformBuffer(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties): UBO.UBODescriptor {
        var err: Int
        // Create a new buffer
        val bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(this.getSize() * 1L)
            .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)

        val uniformDataVSBuffer = scenery.backends.vulkan.VU.run(MemoryUtil.memAllocLong(1), "Create UBO Buffer") {
            vkCreateBuffer(device, bufferInfo, null, this)
        }

        bufferInfo.free()

        // Get memory requirements including size, alignment and memory type
        val memReqs = VkMemoryRequirements.calloc()
        vkGetBufferMemoryRequirements(device, uniformDataVSBuffer, memReqs)
        val memSize = memReqs.size()
        val memoryTypeBits = memReqs.memoryTypeBits()
        memReqs.free()
        // Gets the appropriate memory type for this type of buffer allocation
        // Only memory types that are visible to the host
        val pMemoryTypeIndex = MemoryUtil.memAllocInt(1)
        VU.getMemoryType(deviceMemoryProperties, memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, pMemoryTypeIndex)
        val memoryTypeIndex = pMemoryTypeIndex.get(0)
        MemoryUtil.memFree(pMemoryTypeIndex)
        // Allocate memory for the uniform buffer
        val pUniformDataVSMemory = MemoryUtil.memAllocLong(1)
        val allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .pNext(NULL)
            .allocationSize(memSize)
            .memoryTypeIndex(memoryTypeIndex)

        err = vkAllocateMemory(device, allocInfo, null, pUniformDataVSMemory)
        val uniformDataVSMemory = pUniformDataVSMemory.get(0)
        MemoryUtil.memFree(pUniformDataVSMemory)
        allocInfo.free()
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to allocate UBO memory: " + scenery.backends.vulkan.VU.translate(err))
        }
        // Bind memory to buffer
        err = vkBindBufferMemory(device, uniformDataVSBuffer, uniformDataVSMemory, 0)
        if (err != VK_SUCCESS) {
            throw AssertionError("Failed to bind UBO memory: " + scenery.backends.vulkan.VU.translate(err))
        }

        this.descriptor = UBO.UBODescriptor()
        this.descriptor!!.memory = uniformDataVSMemory
        this.descriptor!!.allocationSize = memSize
        this.descriptor!!.buffer = uniformDataVSBuffer
        this.descriptor!!.offset = 0L
        this.descriptor!!.range = this.getSize() * 1L

        return this.descriptor!!
    }
}
