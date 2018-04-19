package glfw_

import glm_.i
import org.lwjgl.glfw.GLFW.*

object windowHint {

    fun default() = glfwDefaultWindowHints()

    var resizable = true
        set(value) {
            glfwWindowHint(GLFW_RESIZABLE, value.i)
            field = value
        }

    var visible = true
        set(value) {
            glfwWindowHint(GLFW_VISIBLE, value.i)
            field = value
        }

    var decorated = true
        set(value) {
            glfwWindowHint(GLFW_DECORATED, value.i)
            field = value
        }

    var focused = true
        set(value) {
            glfwWindowHint(GLFW_FOCUSED, value.i)
            field = value
        }

    var autoIconify = true
        set(value) {
            glfwWindowHint(GLFW_AUTO_ICONIFY, value.i)
            field = value
        }

    var floating = false
        set(value) {
            glfwWindowHint(GLFW_FLOATING, value.i)
            field = value
        }

    var maximized = false
        set(value) {
            glfwWindowHint(GLFW_FLOATING, value.i)
            field = value
        }

    var redBits = 8
        set(value) {
            glfwWindowHint(GLFW_RED_BITS, value)
            field = value
        }

    var greenBits = 8
        set(value) {
            glfwWindowHint(GLFW_GREEN_BITS, value)
            field = value
        }

    var blueBits = 8
        set(value) {
            glfwWindowHint(GLFW_BLUE_BITS, value)
            field = value
        }

    var alphaBits = 8
        set(value) {
            glfwWindowHint(GLFW_ALPHA_BITS, value)
            field = value
        }

    var depthBits = 24
        set(value) {
            glfwWindowHint(GLFW_DEPTH_BITS, value)
            field = value
        }

    var stencilBits = 8
        set(value) {
            glfwWindowHint(GLFW_STENCIL_BITS, value)
            field = value
        }

    var accumRedBits = 0
        set(value) {
            glfwWindowHint(GLFW_ACCUM_RED_BITS, value)
            field = value
        }

    var accumGreenBits = 0
        set(value) {
            glfwWindowHint(GLFW_ACCUM_GREEN_BITS, value)
            field = value
        }

    var accumBlueBits = 0
        set(value) {
            glfwWindowHint(GLFW_ACCUM_BLUE_BITS, value)
            field = value
        }

    var accumAlphaBits = 0
        set(value) {
            glfwWindowHint(GLFW_ACCUM_ALPHA_BITS, value)
            field = value
        }

    var auxBuffers = 0
        set(value) {
            glfwWindowHint(GLFW_AUX_BUFFERS, value)
            field = value
        }

    var samples = 0
        set(value) {
            glfwWindowHint(GLFW_SAMPLES, value)
            field = value
        }

    var refreshRate = GLFW_DONT_CARE
        set(value) {
            glfwWindowHint(GLFW_REFRESH_RATE, value)
            field = value
        }

    var stereo = false
        set(value) {
            glfwWindowHint(GLFW_STEREO, value.i)
            field = value
        }

    var srgb = false
        set(value) {
            glfwWindowHint(GLFW_SRGB_CAPABLE, value.i)
            field = value
        }

    var doubleBuffer = true
        set(value) {
            glfwWindowHint(GLFW_DOUBLEBUFFER, value.i)
            field = value
        }

    var api = "gl"
        set(value) {
            glfwWindowHint(GLFW_CLIENT_API, when (value) {
                "gl" -> GLFW_OPENGL_API
                "es" -> GLFW_OPENGL_ES_API
                "none" -> GLFW_NO_API
                else -> throw Error("invalid client api, possible values [gl, es, none]")
            })
            field = value
        }

    val context = Context

    var forwardComp = true
        set(value) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, value.i)
            field = value
        }

    var debug = true
        set(value) {
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, value.i)
            field = value
        }

    var profile = "any"
        set(value) {
            glfwWindowHint(GLFW_OPENGL_PROFILE, when (value) {
                "core" -> GLFW_OPENGL_CORE_PROFILE
                "compat" -> GLFW_OPENGL_COMPAT_PROFILE
                "any" -> GLFW_OPENGL_ANY_PROFILE
                else -> throw Error("invalid profile, possible values [core, compat, any]")
            })
            field = value
        }
}

object Context {

    var creationApi = "native"
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_CREATION_API, when (value) {
                "native" -> GLFW_NATIVE_CONTEXT_API
                "egl" -> GLFW_EGL_CONTEXT_API
                else -> throw Error("invalid context creation api, possible values [native, egl]")
            })
            field = value
        }

    var version = "1.0"
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, java.lang.Integer.parseInt(value[0].toString()))
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, java.lang.Integer.parseInt(value[2].toString()))
            field = value
        }

    var major = 1
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, value)
            field = value
        }

    var minor = 0
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, value)
            field = value
        }

    var robustness = GLFW_NO_ROBUSTNESS
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_ROBUSTNESS, value)
            field = value
        }

    var releaseBehaviour = "any"
        set(value) {
            glfwWindowHint(GLFW_CONTEXT_ROBUSTNESS, when(value){
                "any" -> GLFW_ANY_RELEASE_BEHAVIOR
                "flush" -> GLFW_RELEASE_BEHAVIOR_FLUSH
                "none" -> GLFW_RELEASE_BEHAVIOR_NONE
                else -> throw Error("invalid release behaviour, possible values [any, flush, none]")
            })
            field = value
        }
}