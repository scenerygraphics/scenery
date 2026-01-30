package graphics.scenery.utils.extensions

import org.joml.Vector4f

/** Takes an integer-encoded RGB value and returns it as [Vector4f] where alpha is 1.0f. */
fun Int.unpackRGB(): Vector4f {
    val r = (this shr 16 and 0x000000FF) / 255f
    val g = (this shr 8 and 0x000000FF) / 255f
    val b = (this and 0x000000FF) / 255f
    return Vector4f(r, g, b, 1.0f)
}
