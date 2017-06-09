package graphics.scenery.backends.vulkan

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
import graphics.scenery.Node

open class UBO(val device: VkDevice, var backingBuffer: VulkanBuffer? = null): AutoCloseable {
    var name = ""
    var members = LinkedHashMap<String, () -> Any>()
    var descriptor: UBODescriptor? = null
    var offsets: IntBuffer = memAllocInt(3)
    var logger = LoggerFactory.getLogger("VulkanRenderer")
    var requiredOffsetCount = 0

    private var sizeCached = 0

    companion object alignmentsCache {
        var alignments = HashMap<Class<*>, Pair<Int, Int>>()
    }

    class UBODescriptor {
        internal var memory: Long = 0
        internal var allocationSize: Long = 0
        internal var buffer: Long = 0
        internal var offset: Long = 0
        internal var range: Long = 0
    }

    fun getSizeAndAlignment(element: Any): Pair<Int, Int> {
        if(alignments.containsKey(element.javaClass)) {
            return alignments.get(element.javaClass)!!
        } else {
            val sa = when (element.javaClass) {
                GLMatrix::class.java -> {
                    Pair((element as GLMatrix).floatArray.size * 4, 4 * 4)
                }

                GLVector::class.java -> {
                    val v = element as GLVector
                    val size = v.toFloatArray().size
                    val alignment = when (size) {
                        2 -> 2
                        3 -> 4
                        4 -> 4
                        else -> 4
                    }

                    Pair(size * 4, alignment * 4)
                }

                java.lang.Float::class.java -> Pair(4, 4)
                Float::class.java -> Pair(4, 4)

                java.lang.Double::class.java -> Pair(8, 8)
                Double::class.java -> Pair(8, 8)

                Integer::class.java -> Pair(4, 4)
                Int::class.java -> Pair(4, 4)

                Short::class.java -> Pair(2, 2)

                java.lang.Boolean::class.java -> Pair(4, 4)
                Boolean::class.java -> Pair(4, 4)

                else -> {
                    logger.error("Unknown UBO member type: ${element.javaClass.simpleName}")
                    Pair(0, 0)
                }
            }

            alignments.put(element.javaClass, sa)

            return sa
        }
    }

    fun getSize(): Int {
        var totalSize = if(sizeCached == 0) {
            var size = members.map {
                getSizeAndAlignment(it.value.invoke())
            }.fold(0) { current_position, element ->
                // next element should start at the position
                // required by it's alignment
                val remainder = current_position.rem(element.second)

                val new_position = if (remainder != 0) {
                    current_position + element.second - remainder + element.first
                } else {
                    current_position + element.first
                }

                new_position
            }

            sizeCached = size
            size
        } else {
            sizeCached
        }

        return totalSize
    }

    fun copy(data: ByteBuffer, offset: Long = 0) {
        val dest = memAllocPointer(1)
        vkMapMemory(device, descriptor!!.memory, offset, descriptor!!.allocationSize* 1L, 0, dest)
        memCopy(memAddress(data), dest.get(0), data.remaining())
        vkUnmapMemory(device, descriptor!!.memory)
        memFree(dest)
    }

    fun populate(offset: Long = 0) {
        val data = if(backingBuffer == null) {
            memAlloc(getSize())
        } else {
            backingBuffer!!.stagingBuffer
        }

        members.forEach {
            var pos = data.position()
            val value = it.value.invoke()
            val (size, alignment) = getSizeAndAlignment(value)

            if(pos.rem(alignment) != 0) {
                pos = pos + alignment - (pos.rem(alignment))
                data.position(pos)
            }

            when(value.javaClass) {

                GLMatrix::class.java -> {
                    (value as GLMatrix).push(data)
                }
                GLVector::class.java -> {
                    (value as GLVector).push(data)
                }

                java.lang.Float::class.java -> {
                    data.asFloatBuffer().put(0, value as Float)
                }
                Float::class.java -> {
                    data.asFloatBuffer().put(0, value as Float)
                }

                java.lang.Double::class.java -> {
                    data.asDoubleBuffer().put(0, value as Double)
                }
                Double::class.java -> {
                    data.asDoubleBuffer().put(0, value as Double)
                }

                Integer::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Integer::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Int::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }

                java.lang.Short::class.java -> {
                    data.asShortBuffer().put(0, value as Short)
                }
                Short::class.java -> {
                    data.asShortBuffer().put(0, value as Short)
                }

                java.lang.Boolean::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
                Boolean::class.java -> {
                    data.asIntBuffer().put(0, value as Int)
                }
            }

            data.position(pos + size)
        }

        if(backingBuffer == null) {
            data.flip()
            copy(data, offset = offset)
            memFree(data)
        }
    }

    fun fromInstance(node: Node) {
        members.putAll(node.instancedProperties)
    }

    fun createUniformBuffer(deviceMemoryProperties: VkPhysicalDeviceMemoryProperties): UBODescriptor {
        if(backingBuffer == null) {
            val buffer = VU.createBuffer(device,
                deviceMemoryProperties,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true,
                allocationSize = this.getSize() * 1L)

            this.descriptor = UBODescriptor()
            this.descriptor!!.memory = buffer.memory
            this.descriptor!!.allocationSize = buffer.size
            this.descriptor!!.buffer = buffer.buffer
            this.descriptor!!.offset = 0L
            this.descriptor!!.range = this.getSize() * 1L
        } else {
            this.descriptor = UBODescriptor()
            this.descriptor!!.memory = backingBuffer!!.memory
            this.descriptor!!.allocationSize = backingBuffer!!.size
            this.descriptor!!.buffer = backingBuffer!!.buffer
            this.descriptor!!.offset = 0L
            this.descriptor!!.range = this.getSize() * 1L
        }

        return this.descriptor!!
    }

    fun updateBackingBuffer(newBackingBuffer: VulkanBuffer) {
        backingBuffer = newBackingBuffer
        this.descriptor = UBODescriptor()
        this.descriptor!!.memory = backingBuffer!!.memory
        this.descriptor!!.allocationSize = backingBuffer!!.size
        this.descriptor!!.buffer = backingBuffer!!.buffer
        this.descriptor!!.offset = 0L
        this.descriptor!!.range = this.getSize() * 1L
    }

    fun copyFromStagingBuffer() {
        backingBuffer?.copyFromStagingBuffer()
    }

    override fun close() {
        if(backingBuffer == null) {
            descriptor?.let {
                vkDestroyBuffer(device, it.buffer, null)
                vkFreeMemory(device, it.memory, null)
            }
        }
    }
}
