@file:JvmName("VectorMath")
package graphics.scenery.utils.extensions

import org.joml.*
import java.lang.Math
import java.nio.FloatBuffer

/* Vector2f */

operator fun Vector2f.plus(other: Vector2fc): Vector2f {
    return Vector2f(this).add(other)
}

operator fun Vector2f.times(other: Vector2fc): Vector2f {
    return Vector2f(this).mul(other)
}

operator fun Vector2f.times(other: Float): Vector2f {
    return Vector2f(this).mul(other)
}

operator fun Vector2f.minus(other: Vector2fc): Vector2f {
    return Vector2f(this).minus(other)
}

operator fun Vector2f.plusAssign(other: Vector2fc): Unit {
    this.add(other)
}

operator fun Vector2f.minusAssign(other: Vector2fc): Unit {
    this.sub(other)
}

operator fun Vector2f.timesAssign(other: Float): Unit {
    this.mul(other)
}

operator fun Vector2f.divAssign(other: Float): Unit {
    this.mul(1.0f / other)
}

operator fun Float.times(other: Vector2fc): Vector2f {
    return Vector2f(other).times(this)
}

fun Vector2fc.xyz(): Vector3f {
    return Vector3f().set(x(), y(), 0.0f)
}


fun Vector2fc.xyzw(): Vector4f {
    return Vector4f().set(x(), y(), 0.0f, 1.0f)
}

fun Vector2fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

fun Vector2f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y)
}


/* Vector3f */

operator fun Vector3f.plus(other: Vector3fc): Vector3f {
    return Vector3f(this).add(other)
}

operator fun Vector3f.times(other: Vector3fc): Vector3f {
    return Vector3f(this).mul(other)
}

operator fun Vector3f.times(other: Float): Vector3f {
    return Vector3f(this).mul(other)
}

operator fun Vector3f.minus(other: Vector3fc): Vector3f {
    return Vector3f(this).sub(other)
}

operator fun Vector3f.plusAssign(other: Vector3fc): Unit {
    this.add(other)
}

operator fun Vector3f.minusAssign(other: Vector3fc): Unit {
    this.sub(other)
}

operator fun Vector3f.timesAssign(other: Float): Unit {
    this.mul(other)
}

operator fun Vector3f.divAssign(other: Float): Unit {
    this.set(this.mul(1.0f / other))
}

operator fun Float.times(other: Vector3fc): Vector3f {
    return Vector3f(other).times(this)
}

fun Vector3fc.xy(): Vector2f {
    return Vector2f().set(this.x(), this.y())
}

fun Vector3fc.xyzw(): Vector4f {
    return Vector4f().set(this, 1.0f)
}

fun Vector3fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

fun Vector3f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y, this.z)
}

/* Vector4f */

operator fun Vector4f.plus(other: Vector4fc): Vector4f {
    return Vector4f(this).add(other)
}

operator fun Vector4f.times(other: Vector4fc): Vector4f {
    return Vector4f(this).mul(other)
}

operator fun Vector4f.times(other: Float): Vector4f {
    return Vector4f(this).mul(other)
}

operator fun Vector4f.minus(other: Vector4fc): Vector4f {
    return Vector4f(this).sub(other)
}

operator fun Vector4f.plusAssign(other: Vector4fc): Unit {
    this.add(other)
}

operator fun Vector4f.minusAssign(other: Vector4fc): Unit {
    this.sub(other)
}

operator fun Vector4f.timesAssign(other: Float): Unit {
    this.mul(other)
}

operator fun Vector4f.divAssign(other: Float): Unit {
    this.mul(1.0f / other)
}

operator fun Float.times(other: Vector4fc): Vector4f {
    return Vector4f(other).times(this)
}

fun Vector4fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

fun Vector4fc.xyz(): Vector3f {
    return Vector3f().set(x(), y(), z())
}

fun Vector4f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y, this.z, this.w)
}

operator fun Quaternionf.times(other: Quaternionf): Quaternionf {
    return this.mul(other)
}

infix operator fun Matrix4f.times(rhs: Matrix4f): Matrix4f {
    val m = Matrix4f(this)
    m.mul(rhs)

    return m
}

fun Matrix4f.compare(right: Matrix4fc, explainDiff: Boolean): Boolean {
    val EPSILON = 0.00001f
    val left = this

    for (r in 0 .. 3) {
        for (c in 0..3) {
            val delta = Math.abs(left.get(c, r) - right.get(c, r))

            if (delta > EPSILON) {
                if (explainDiff) {
                    System.err.println(
                        "Matrices differ at least in position row=$r, col=$c, |delta|=$delta")
                    System.err.println("LHS: $left")
                    System.err.println("RHS: $right")
                }
                return false
            }
        }
    }
    return true
}

operator fun FloatBuffer.plusAssign(v: Vector2f) {
    v.get(this)
    this.position(this.position() + 2)
}

operator fun FloatBuffer.plusAssign(v: Vector3f) {
    v.get(this)
    this.position(this.position() + 3)
}

operator fun FloatBuffer.plusAssign(v: Vector4f) {
    v.get(this)
    this.position(this.position() + 4)
}



