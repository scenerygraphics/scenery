package glfw_

import glm_.vec2.Vec2i
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVidMode
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memGetLong
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.VkInstance
import vkn.VK_CHECK_RESULT
import vkn.VkSurfaceKHR
import vkn.adr

/**
 * Created by elect on 22/04/17.
 */

object glfw {

    @Throws(RuntimeException::class)
    fun init() {

        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit())
            throw RuntimeException("Unable to initialize GLFW")

        /* This window hint is required to use OpenGL 3.1+ on macOS */
        if (Platform.get() == Platform.MACOSX)
            windowHint.forwardComp = true
    }

    val vulkanSupported get() = GLFWVulkan.glfwVulkanSupported()

    fun <R> windowHint(block: windowHint.() -> R) = windowHint.block()

    val primaryMonitor get() = glfwGetPrimaryMonitor()

    val videoMode get() = glfwGetVideoMode(primaryMonitor)!!

    var start = System.nanoTime()
    val time get() = (System.nanoTime() - start) / 1e9f

    fun videoMode(monitor: Long) = glfwGetVideoMode(monitor)

    val resolution
        get() = videoMode.size

    var swapInterval = 0
        set(value) = glfwSwapInterval(value)

    fun terminate() {
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }

    fun pollEvents() = glfwPollEvents()

    val requiredInstanceExtensions: ArrayList<String>
        get() {
            val pCount = appBuffer.intBuffer
            val ppNames = GLFWVulkan.nglfwGetRequiredInstanceExtensions(pCount.adr)
            val a = GLFWVulkan.glfwGetRequiredInstanceExtensions()
            val count = pCount[0]
            val pNames = MemoryUtil.memPointerBufferSafe(ppNames, count) ?: return arrayListOf()
            val res = ArrayList<String>(count)
            for (i in 0 until count)
                res += MemoryUtil.memASCII(pNames[i])
            return res
        }

    fun createWindowSurface(windowHandle: Long, instance: VkInstance): VkSurfaceKHR {
        val pSurface = appBuffer.long
        VK_CHECK_RESULT(GLFWVulkan.nglfwCreateWindowSurface(instance.adr, windowHandle, NULL, pSurface))
        return memGetLong(pSurface)
    }
}

inline val GLFWVidMode.width: Int
    get() = GLFWVidMode.nwidth(adr)
inline val GLFWVidMode.height: Int
    get() = GLFWVidMode.nheight(adr)
inline val GLFWVidMode.size: Vec2i
    get() = Vec2i(width, height)
inline val GLFWVidMode.redBits: Int
    get() = GLFWVidMode.nredBits(adr)
inline val GLFWVidMode.greenBits: Int
    get() = GLFWVidMode.ngreenBits(adr)
inline val GLFWVidMode.blueBits: Int
    get() = GLFWVidMode.nblueBits(adr)
inline val GLFWVidMode.refreshRate: Int
    get() = GLFWVidMode.nrefreshRate(adr)
