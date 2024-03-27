package graphics.scenery.utils.extensions

import org.joml.Matrix4f

private val vulkanProjectionFix =
    Matrix4f(
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, -1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.5f, 0.0f,
        0.0f, 0.0f, 0.5f, 1.0f)

/**
 * Converts a camera projection matrix from OpenGL to Vulkan coordinate system conventions.
 */
fun Matrix4f.applyVulkanCoordinateSystem(): Matrix4f {
    val m = Matrix4f(vulkanProjectionFix)
    m.mul(this)

    return m
}
