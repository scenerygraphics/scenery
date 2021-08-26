package graphics.scenery

import org.joml.Vector3f

/**
 * Data class to store Frenet frames (wandering coordinate systems), consisting of [tangent], [normal], [binormal]
 */
data class FrenetFrame(val tangent: Vector3f, var normal: Vector3f, var binormal: Vector3f, val translation: Vector3f)
