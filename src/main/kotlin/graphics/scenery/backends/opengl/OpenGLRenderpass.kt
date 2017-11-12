package graphics.scenery.backends.opengl

import cleargl.GLFramebuffer
import cleargl.GLVector
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.utils.LazyLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 7/14/2017.
 */


class OpenGLRenderpass(var passName: String = "", var passConfig: RenderConfigReader.RenderpassConfig) {
    private val logger by LazyLogger()

    var openglMetadata: OpenGLMetadata = OpenGLMetadata()
    var output = ConcurrentHashMap<String, GLFramebuffer>()
    var inputs = ConcurrentHashMap<String, GLFramebuffer>()
    var defaultShader: OpenGLShaderProgram? = null
    var UBOs = ConcurrentHashMap<String, OpenGLUBO>()

    data class Rect2D(var width: Int = 0, var height: Int = 0, var offsetX: Int = 0, var offsetY: Int = 0)
    data class Viewport(var area: Rect2D = Rect2D(), var minDepth: Float = 0.0f, var maxDepth: Float = 1.0f)
    data class ClearValue(var clearColor: GLVector = GLVector(0.0f, 0.0f, 0.0f, 1.0f), var clearDepth: Float = 0.0f)

    data class OpenGLMetadata(
        var scissor: Rect2D = Rect2D(),
        var renderArea: Rect2D = Rect2D(),
        var clearValues: ClearValue = ClearValue(),
        var colorWriteMask: Array<Boolean> = arrayOf(true, true, true, true),
        var viewport: Viewport = Viewport(),
        var eye: Int = 0
    )

    fun initializeShaderParameters(settings: Settings, backingBuffer: OpenGLRenderer.OpenGLBuffer){
        passConfig.parameters?.let { params ->
            val ubo = OpenGLUBO(backingBuffer)

            ubo.name = "ShaderParameters-$passName"
            params.forEach { entry ->
                // Entry could be created in Java, so we check for both Java and Kotlin strings
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                val value = if (entry.value is String || entry.value is java.lang.String) {
                    val s = entry.value as String
                    GLVector(*(s.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()))
                } else if (entry.value is Double) {
                    (entry.value as Double).toFloat()
                } else {
                    entry.value
                }

                val settingsKey = if (entry.key.startsWith("Global")) {
                    "Renderer.${entry.key.substringAfter("Global.")}"
                } else {
                    "Renderer.$passName.${entry.key}"
                }

                if (!entry.key.startsWith("Global")) {
                    settings.set(settingsKey, value)
                }

                ubo.add(entry.key, { settings.get(settingsKey) })
            }

            ubo.populate()

            UBOs.put(ubo.name, ubo)
        }
    }

    fun updateShaderParameters() {
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderParameters-")) {
                ubo.offset = ubo.backingBuffer!!.advance()
                ubo.populate()
            }
        }
    }
}
