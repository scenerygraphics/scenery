package graphics.scenery.geometry.curve

import org.joml.Vector3f

/**
 * Data class to store Frenet frames (wandering coordinate systems), consisting of [tangent], [normal], [binormal]
 */
data class FrenetFrame(
    val tangent: Vector3f,
    val normal: Vector3f,
    val binormal: Vector3f,
    val translation: Vector3f
)
