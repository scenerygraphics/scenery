package graphics.scenery.backends.opengl

import cleargl.GLFramebuffer
import org.joml.Vector3f
import graphics.scenery.Settings
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.StickyBoolean
import org.joml.Vector2f
import org.joml.Vector4f
import java.util.concurrent.ConcurrentHashMap

/**
 * Class to contain an OpenGL render pass with name [passName] and associated configuration
 * [passConfig].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class OpenGLRenderpass(var passName: String = "", var passConfig: RenderConfigReader.RenderpassConfig) {
    private val logger by LazyLogger()

    /** OpenGL metadata */
    var openglMetadata: OpenGLMetadata = OpenGLMetadata()
    /** Output(s) of the pass */
    var output = ConcurrentHashMap<String, GLFramebuffer>()
    /** Inputs of the pass */
    var inputs = ConcurrentHashMap<String, GLFramebuffer>()
    /** The default shader the pass uses */
    var defaultShader: OpenGLShaderProgram? = null
    /** UBOs required by this pass */
    var UBOs = ConcurrentHashMap<String, OpenGLUBO>()

    /** Class to store 2D rectangles with [width], [height] and offsets [offsetX] and [offsetY] */
    data class Rect2D(var width: Int = 0, var height: Int = 0, var offsetX: Int = 0, var offsetY: Int = 0)
    /** Class to store viewport information, [area], and minimal/maximal depth coordinates ([minDepth] and [maxDepth]). */
    data class Viewport(var area: Rect2D = Rect2D(), var minDepth: Float = 0.0f, var maxDepth: Float = 1.0f)
    /** Class to store clear values for color targets ([clearColor]) and depth targets ([clearDepth]) */
    data class ClearValue(var clearColor: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 1.0f), var clearDepth: Float = 0.0f)

    /**
     * OpenGL metadata class, storing [scissor] areas, [renderArea]s, [clearValues], [viewports], and
     * for which [eye] this metadata is valid.
     */
    data class OpenGLMetadata(
        var scissor: Rect2D = Rect2D(),
        var renderArea: Rect2D = Rect2D(),
        var clearValues: ClearValue = ClearValue(),
        var viewport: Viewport = Viewport(),
        var eye: Int = 0
    )

    /**
     * Initialises shader parameters for this pass from [settings], which will be serialised
     * into [backingBuffer].
     */
    fun initializeShaderParameters(settings: Settings, backingBuffer: OpenGLRenderer.OpenGLBuffer) {
        val ubo = OpenGLUBO(backingBuffer)

        ubo.name = "ShaderParameters-$passName"
        passConfig.parameters.forEach { entry ->
            // Entry could be created in Java, so we check for both Java and Kotlin strings
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            val value = if (entry.value is String || entry.value is java.lang.String) {
                val s = entry.value as String
                val split = s.split(",").map { it.trim().trimStart().toFloat() }.toFloatArray()
                when(split.size) {
                    2 -> Vector2f(split[0], split[1])
                    3 -> Vector3f(split[0], split[1], split[2])
                    4 -> Vector4f(split[0], split[1], split[2], split[3])
                    else -> throw IllegalStateException("Dont know how to handle ${split.size} elements in Shader Parameter split")
                }
            } else if (entry.value is Double) {
                (entry.value as Double).toFloat()
            } else {
                entry.value
            }

            val settingsKey = when {
                entry.key.startsWith("System") -> "System.${entry.key.substringAfter("System.")}"
                entry.key.startsWith("Global") -> "Renderer.${entry.key.substringAfter("Global.")}"
                entry.key.startsWith("Pass") -> "Renderer.$passName.${entry.key.substringAfter("Pass.")}"
                else -> "Renderer.$passName.${entry.key}"
            }

            if (!entry.key.startsWith("Global") && !entry.key.startsWith("Pass.") && !entry.key.startsWith("System.")) {
                settings.setIfUnset(settingsKey, value)
            }

            ubo.add(entry.key, { settings.get(settingsKey) })
        }

        ubo.setOffsetFromBackingBuffer()
        ubo.populate()

        UBOs.put(ubo.name, ubo)
    }

    /**
     * Updates previously set-up shader parameters.
     *
     * Returns true if the parameters have been updated, and false if not.
     */
    fun updateShaderParameters(): Boolean {
        var updated: Boolean by StickyBoolean(false)

        logger.trace("Updating shader parameters for ${this.passName}")
        UBOs.forEach { uboName, ubo ->
            if(uboName.startsWith("ShaderParameters-")) {
                ubo.setOffsetFromBackingBuffer()
                updated = ubo.populate()
            }
        }

        return updated
    }
}
