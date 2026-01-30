package graphics.scenery.utils.extensions

import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Takes an integer-encoded RGB value and returns it as [Vector4f] where alpha is 1.0f.
 */
fun Int.unpackRGB(): Vector4f {
    val r = (this shr 16 and 0x000000FF) / 255f
    val g = (this shr 8 and 0x000000FF) / 255f
    val b = (this and 0x000000FF) / 255f
    return Vector4f(r, g, b, 1.0f)
}

/**
 * Transforms an 8-bit ARGB integer to 8bit RGBA by extracting each 8-bit channel
 * and repositioning them in the output integer.
 * @receiver An integer in ARGB format (0xAARRGGBB)
 * @return An integer in RGBA format (0xRRGGBBAA)
 */
fun Int.toRGBA() : Int {
    val a = this shr 24 and 0xff
    val r = this shr 16 and 0xff
    val g = this shr 8 and 0xff
    val b = this and 0xff
    return a shl 24 or (b shl 16) or (g shl 8) or r
}

/**
 * Stretches/Compresses an RGB vector so that the brightest color equals 1,
 * scaling the other two channels proportionally.
 * */
fun Vector3f.stretchColor(): Vector3f {
    return this.div(this.max())
}
