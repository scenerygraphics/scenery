/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.demo.openxr

import com.jogamp.opengl.egl.EGL
import org.joml.Math
import org.joml.Matrix4f

import org.lwjgl.glfw.*
import org.lwjgl.opengl.GLX
import org.lwjgl.opengl.GLX13
import org.lwjgl.openxr.*
import org.lwjgl.system.*
import org.lwjgl.system.windows.User32

/**
 * A helper class with some static methods to help applications with OpenXR related tasks that are cumbersome in
 * some way.
 */
object XRHelper {
    fun <S : Struct?, T : StructBuffer<S, T>?> fill(buffer: T, offset: Int, value: Int): T {
        val ptr = buffer!!.address() + offset
        val stride = buffer.sizeof()
        for (i in 0 until buffer.limit()) {
            MemoryUtil.memPutInt(ptr + i * stride, value)
        }
        return buffer
    }

    /**
     * Allocates an [XrApiLayerProperties.Buffer] onto the given stack with the given number of layers and
     * sets the type of each element in the buffer to [XR_TYPE_API_LAYER_PROPERTIES][XR10.XR_TYPE_API_LAYER_PROPERTIES].
     *
     *
     * Note: you can't use the buffer after the stack is gone!
     *
     * @param stack     the stack to allocate the buffer on
     * @param numLayers the number of elements the buffer should get
     *
     * @return the created buffer
     */
    fun prepareApiLayerProperties(stack: MemoryStack?, numLayers: Int): XrApiLayerProperties.Buffer {
        return fill(
            XrApiLayerProperties.calloc(numLayers, stack!!),
            XrApiLayerProperties.TYPE,
            XR10.XR_TYPE_API_LAYER_PROPERTIES
        )
    }

    /**
     * Allocates an [XrExtensionProperties.Buffer] onto the given stack with the given number of extensions
     * and sets the type of each element in the buffer to [XR_TYPE_EXTENSION_PROPERTIES][XR10.XR_TYPE_EXTENSION_PROPERTIES].
     *
     *
     * Note: you can't use the buffer after the stack is gone!
     *
     * @param stack         the stack onto which to allocate the buffer
     * @param numExtensions the number of elements the buffer should get
     *
     * @return the created buffer
     */
    fun prepareExtensionProperties(stack: MemoryStack?, numExtensions: Int): XrExtensionProperties.Buffer {
        return fill(
            XrExtensionProperties.calloc(numExtensions, stack!!),
            XrExtensionProperties.TYPE,
            XR10.XR_TYPE_EXTENSION_PROPERTIES
        )
    }

    /**
     * Applies an off-center asymmetric perspective projection transformation to the given [Matrix4f],
     * such that it represents a projection matrix with the given **fov**, **nearZ** (a.k.a. near plane),
     * **farZ** (a.k.a. far plane).
     *
     * @param m          The matrix to apply the perspective projection transformation to
     * @param fov        The desired Field of View for the projection matrix. You should normally use the value of
     * [XrCompositionLayerProjectionView.fov].
     * @param nearZ      The nearest Z value that the user should see (also known as the near plane)
     * @param farZ       The furthest Z value that the user should see (also known as far plane)
     * @param zZeroToOne True if the z-axis of the coordinate system goes from 0 to 1 (Vulkan).
     * False if the z-axis of the coordinate system goes from -1 to 1 (OpenGL).
     *
     * @return the provided matrix
     */
    fun applyProjectionToMatrix(m: Matrix4f, fov: XrFovf, nearZ: Float, farZ: Float, zZeroToOne: Boolean): Matrix4f {
        val distToLeftPlane = Math.tan(fov.angleLeft())
        val distToRightPlane = Math.tan(fov.angleRight())
        val distToBottomPlane = Math.tan(fov.angleDown())
        val distToTopPlane = Math.tan(fov.angleUp())
        return m.frustum(distToLeftPlane * nearZ, distToRightPlane * nearZ, distToBottomPlane * nearZ, distToTopPlane * nearZ, nearZ, farZ, zZeroToOne)
    }

    /**
     * Appends the right *XrGraphicsBinding*** struct to the next chain of *sessionCreateInfo*. OpenGL
     * session creation is poorly standardized in OpenXR, so the right graphics binding struct depends on the OS and
     * the windowing system, among others. There are basically 4 graphics binding structs for this:
     *
     *  * *XrGraphicsBindingOpenGLWin32KHR*, which can only be used on Windows computers.
     *  * *XrGraphicsBindingOpenGLXlibKHR*, which can only be used on Linux computers with the X11 windowing system.
     *  *
     * *XrGraphicsBindingOpenGLWaylandKHR*, which can only be used on Linux computers with the
     * Wayland windowing system. But, no OpenXR runtime has implemented the specification for this struct and
     * the Wayland developers have claimed that the specification doesn't make sense and can't be implemented
     * as such. For this reason, this method won't use this struct.
     *
     *  *
     * *XrGraphicsBindingEGLMNDX*, which is cross-platform, but can only be used if the experimental
     * OpenXR extension *XR_MNDX_egl_enable* is enabled. But, since the extension is experimental, it
     * is not widely supported (at the time of writing this only by the Monado OpenXR runtime). Nevertheless,
     * this is the only way to create an OpenXR session on the Wayland windowing system (or on systems
     * without well-known windowing system).
     *
     *
     *
     * The parameter **useEGL** determines which graphics binding struct this method will choose:
     *
     *  *
     * If **useEGL** is true, this method will use *XrGraphicsBindingEGLMNDX*.
     * The caller must ensure that the extension *XR_MNDX_egl_enable* has been enabled.
     *
     *  *
     * If **useEGL** is false, this method will try to use a platform-specific struct.
     * If no such struct exists, it will throw an *IllegalStateException*.
     *
     *
     *
     * @param sessionCreateInfo The *XrSessionCreateInfo* whose next chain should be populated
     * @param stack The *MemoryStack* onto which this method should allocate the graphics binding struct
     * @param window The GLFW window
     * @param useEGL Whether this method should use *XrGraphicsBindingEGLMNDX*
     * @return sessionCreateInfo (after appending a graphics binding to it)
     * @throws IllegalStateException If the current OS and/or windowing system needs EGL, but **useEGL** is false
     */
   /* @Throws(IllegalStateException::class)
    fun createGraphicsBindingOpenGL(
        sessionCreateInfo: XrSessionCreateInfo, stack: MemoryStack?, window: Long, useEGL: Boolean
    ): XrSessionCreateInfo {
        if (useEGL) {
            println("Using XrGraphicsBindingEGLMNDX to create the session...")
            return sessionCreateInfo.next(
                XrGraphicsBindingEGLMNDX.malloc(stack!!)
                    .`type$Default`()
                    .next(MemoryUtil.NULL)
                    .getProcAddress(EGL.getCapabilities().eglGetProcAddress)
                    .display(GLFWNativeEGL.glfwGetEGLDisplay())
                    .config(GLFWNativeEGL.glfwGetEGLConfig(window))
                    .context(GLFWNativeEGL.glfwGetEGLContext(window))
            )
        }
        return when (Platform.get()) {
            Platform.LINUX -> {
                val platform = GLFW.glfwGetPlatform()
                if (platform == GLFW.GLFW_PLATFORM_X11) {
                    val display = GLFWNativeX11.glfwGetX11Display()
                    val glxConfig = GLFWNativeGLX.glfwGetGLXFBConfig(window)
                    val visualInfo = GLX13.glXGetVisualFromFBConfig(display, glxConfig)
                        ?: throw IllegalStateException("Failed to get visual info")
                    val visualid = visualInfo.visualid()
                    println("Using XrGraphicsBindingOpenGLXlibKHR to create the session")
                    sessionCreateInfo.next(
                        XrGraphicsBindingOpenGLXlibKHR.malloc(stack!!)
                            .`type$Default`()
                            .xDisplay(display)
                            .visualid(visualid.toInt())
                            .glxFBConfig(glxConfig)
                            .glxDrawable(GLX.glXGetCurrentDrawable())
                            .glxContext(GLFWNativeGLX.glfwGetGLXContext(window))
                    )
                } else {
                    throw IllegalStateException(
                        "X11 is the only Linux windowing system with explicit OpenXR support. All other Linux systems must use EGL."
                    )
                }
            }
            Platform.WINDOWS -> {
                println("Using XrGraphicsBindingOpenGLWin32KHR to create the session")
                sessionCreateInfo.next(
                    XrGraphicsBindingOpenGLWin32KHR.malloc(stack!!)
                        .`type$Default`()
                        .hDC(User32.GetDC(GLFWNativeWin32.glfwGetWin32Window(window)))
                        .hGLRC(GLFWNativeWGL.glfwGetWGLContext(window))
                )
            }
            else -> throw IllegalStateException(
                "Windows and Linux are the only platforms with explicit OpenXR support. All other platforms must use EGL."
            )
        }
    }*/
}
