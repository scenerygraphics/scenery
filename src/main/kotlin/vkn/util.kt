package vkn

import glfw_.advance
import glfw_.appBuffer
import glfw_.appBuffer.ptr
import gli_.extension
import glm_.BYTES
import glm_.i
import glm_.set
import graphics.scenery.spirvcrossj.*
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.system.Struct
import org.lwjgl.system.StructBuffer
import org.lwjgl.vulkan.*
import uno.kotlin.buffers.indices
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.file.Files
import java.nio.file.Path


//fun pointerBufferOf(vararg strings: String): PointerBuffer {
//    val buf = pointerBufferBig(strings.size)
//    for (i in strings.indices)
//        buf[i] = strings[i]
//    return buf
//}
//
//operator fun PointerBuffer.set(index: Int, string: String) {
//    put(index, string.memUTF16)
//}
inline operator fun PointerBuffer.set(index: Int, long: Long) {
    put(index, long)
}

inline operator fun PointerBuffer.set(index: Int, pointer: Pointer) {
    put(index, pointer)
}

//operator fun PointerBuffer.plusAssign(string: String) {
//    put(string.stackUTF16)
//}

//operator fun <T> PointerBuffer.plusAssign(elements: Iterable<T>) {
//    for (item in elements)
//        if (item is String)
//            put(item.memUTF16)
//        else
//            throw Error()
//}
//
//fun PointerBuffer.isNotEmpty() = position() > 0

const val VK_WHOLE_SIZE = 0L.inv()

typealias VkBuffer = Long
typealias VkCommandPool = Long
typealias VkDebugReportCallback = Long
typealias VkDescriptorPool = Long
typealias VkDescriptorSet = Long
typealias VkDescriptorSetLayout = Long
typealias VkDeviceMemory = Long
typealias VkDeviceSize = Long
typealias VkFence = Long
typealias VkFramebuffer = Long
typealias VkImage = Long
typealias VkImageView = Long
typealias VkPipeline = Long
typealias VkPipelineCache = Long
typealias VkPipelineLayout = Long
typealias VkRenderPass = Long
typealias VkSampler = Long
typealias VkSemaphore = Long
typealias VkShaderModule = Long
typealias VkSurfaceKHR = Long
typealias VkSwapchainKHR = Long

typealias VkDescriptorSetBuffer = LongBuffer
typealias VkSemaphoreBuffer = LongBuffer
typealias VkSwapchainKhrBuffer = LongBuffer
typealias VkResultBuffer = IntBuffer
typealias VkSamplerBuffer = LongBuffer
typealias VkImageViewBuffer = LongBuffer

typealias VkImageArray = LongArray
typealias VkImageViewArray = LongArray


object LongArrayList {
    operator fun ArrayList<Long>.set(index: Int, long: LongBuffer) {
        set(index, long[0])
    }

    infix fun ArrayList<Long>.resize(newSize: Int) {
        if (size < newSize)
            for (i in size until newSize)
                add(NULL)
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}

object VkPhysicalDeviceArrayList {
//    operator fun ArrayList<VkPhysicalDevice>.set(index: Int, long: LongBuffer) {
//        set(index, long[0])
//    }

    infix fun ArrayList<VkPhysicalDevice>.resize(newSize: Int) {
        if (size < newSize) TODO()
//            for (i in size until newSize)
//                add(VkPhysicalDevice())
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}


inline fun vkDestroySemaphores(device: VkDevice, semaphores: VkSemaphoreBuffer) {
    for (i in 0 until semaphores.remaining())
        VK10.nvkDestroySemaphore(device, semaphores[i], NULL)
}


inline fun vkDestroyBuffer(device: VkDevice, buffer: VkBuffer) = VK10.nvkDestroyBuffer(device, buffer, NULL)


val FloatBuffer.adr get() = MemoryUtil.memAddress(this)
val IntBuffer.adr get() = MemoryUtil.memAddress(this)

inline val Pointer.adr get() = address()


fun PointerBuffer?.toArrayList(): ArrayList<String> {
    val count = this?.remaining() ?: 0
    if (this == null || count == 0) return arrayListOf()
    val res = ArrayList<String>(count)
    for (i in 0 until count)
        res += get(i).utf8
    return res
}

fun Collection<String>.toPointerBuffer(): PointerBuffer {
    val pointers = PointerBuffer.create(ptr.advance(Pointer.POINTER_SIZE * size), size)
    for (i in indices)
        pointers.put(i, elementAt(i).utf8)
    return pointers
}


fun glslToSpirv(path: Path): ByteBuffer {

    var compileFail = false
    var linkFail = false
    val program = TProgram()

    val code = Files.readAllLines(path).joinToString("\n")

    val extension = path.extension
    val shaderType = when (extension) {
        "vert" -> EShLanguage.EShLangVertex
        "frag" -> EShLanguage.EShLangFragment
        "geom" -> EShLanguage.EShLangGeometry
        "tesc" -> EShLanguage.EShLangTessControl
        "tese" -> EShLanguage.EShLangTessEvaluation
        "comp" -> EShLanguage.EShLangCompute
        else -> throw RuntimeException("Unknown shader extension .$extension")
    }

    println("${path.fileName}: Compiling shader code  (${code.length} bytes)... ")

    val shader = TShader(shaderType).apply {
        setStrings(arrayOf(code), 1)
        setAutoMapBindings(true)
    }

    val messages = EShMessages.EShMsgDefault or EShMessages.EShMsgVulkanRules or EShMessages.EShMsgSpvRules

    val resources = libspirvcrossj.getDefaultTBuiltInResource()
    if (!shader.parse(resources, 450, false, messages))
        compileFail = true

    if (compileFail) {
        println("Info log: " + shader.infoLog)
        println("Debug log: " + shader.infoDebugLog)
        throw RuntimeException("Compilation of ${path.fileName} failed")
    }

    program.addShader(shader)

    if (!program.link(EShMessages.EShMsgDefault) || !program.mapIO())
        linkFail = true

    if (linkFail) {
        System.err.println(program.infoLog)
        System.err.println(program.infoDebugLog)

        throw RuntimeException("Linking of program ${path.fileName} failed!")
    }


    val spirv = IntVec()
    libspirvcrossj.glslangToSpv(program.getIntermediate(shaderType), spirv)

    println("Generated " + spirv.capacity() + " bytes of SPIRV bytecode.")

    //System.out.println(shader);
    //System.out.println(program);

    return spirv.toByteBuffer()
}

private fun IntVec.toByteBuffer(): ByteBuffer {
    val bytes = appBuffer.buffer(size().i * Int.BYTES)
    val ints = bytes.asIntBuffer()
    for (i in ints.indices)
        ints[i] = get(i).i
    return bytes
}


inline fun VkDescriptorPoolSize.Buffer.appyAt(index: Int, block: VkDescriptorPoolSize.() -> Unit): VkDescriptorPoolSize.Buffer {
    get(index).block()
    return this
}

inline fun VkSubpassDependency.Buffer.appyAt(index: Int, block: VkSubpassDependency.() -> Unit): VkSubpassDependency.Buffer {
    get(index).block()
    return this
}

inline fun VkVertexInputAttributeDescription.Buffer.appyAt(index: Int, block: VkVertexInputAttributeDescription.() -> Unit): VkVertexInputAttributeDescription.Buffer {
    get(index).block()
    return this
}

operator fun <T : Struct, SELF : StructBuffer<T, SELF>> StructBuffer<T, SELF>.set(index: Int, value: T) {
    put(index, value)
}

inline fun <T, C : Iterable<T>> C.applyOnEach(action: T.() -> Unit): C = onEach(action)

