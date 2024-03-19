@file:JvmName("VectorMath")
package graphics.scenery.utils.extensions

import org.joml.*
import java.lang.Math
import java.nio.FloatBuffer

/* Vector2f */

/**
 * Adds [other] to a copy of this vector and returns the result.
 */
operator fun Vector2f.plus(other: Vector2fc): Vector2f {
    return Vector2f(this).add(other)
}

/**
 * Multiplies a copy of this vector with [other] by-component and returns the result.
 */
operator fun Vector2f.times(other: Vector2fc): Vector2f {
    return Vector2f(this).mul(other)
}

/**
 * Multiplies a copy of this vector with [other] and returns the result.
 */
operator fun Vector2f.times(other: Float): Vector2f {
    return Vector2f(this).mul(other)
}

/**
 * Subtracts [other] from a copy of this vector and returns the result.
 */
operator fun Vector2f.minus(other: Vector2fc): Vector2f {
    return Vector2f(this).minus(other)
}

/**
 * Adds [other] to this vector in-place and returns the result.
 */
operator fun Vector2f.plusAssign(other: Vector2fc): Unit {
    this.add(other)
}

/**
 * Subtracts [other] from this vector in-place and returns the result.
 */
operator fun Vector2f.minusAssign(other: Vector2fc): Unit {
    this.sub(other)
}

/**
 * Multiplies this vector with [other] in-place.
 */
operator fun Vector2f.timesAssign(other: Float): Unit {
    this.mul(other)
}

/**
 * Divides this vector by [other] in-place.
 */
operator fun Vector2f.divAssign(other: Float): Unit {
    this.mul(1.0f / other)
}

/**
 * Multiplies this float value with the [other] vector and returns the result.
 */
operator fun Float.times(other: Vector2fc): Vector2f {
    return Vector2f(other).times(this)
}

/**
 * Converts this 2D vector to 3D, setting the z component to 0.0f.
 */
fun Vector2fc.xyz(): Vector3f {
    return Vector3f().set(x(), y(), 0.0f)
}


/**
 * Converts this 2D vector to 4D, setting the z component to 0.0f, and the w component to 1.0f.
 */
fun Vector2fc.xyzw(): Vector4f {
    return Vector4f().set(x(), y(), 0.0f, 1.0f)
}

/**
 * Returns the value of the biggest component of this vector.
 */
fun Vector2fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

/**
 * Converts this vector to a [FloatArray].
 */
fun Vector2f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y)
}


/* Vector3f */

/**
 * Adds [other] to a copy of this vector and returns the result.
 */
operator fun Vector3f.plus(other: Vector3fc): Vector3f {
    return Vector3f(this).add(other)
}

/**
 * Multiplies a copy of this vector with [other] by-component and returns the result.
 */
operator fun Vector3f.times(other: Vector3fc): Vector3f {
    return Vector3f(this).mul(other)
}

/**
 * Multiplies a copy of this vector with [other] and returns the result.
 */
operator fun Vector3f.times(other: Float): Vector3f {
    return Vector3f(this).mul(other)
}

/**
 * Subtracts [other] from a copy of this vector and returns the result.
 */
operator fun Vector3f.minus(other: Vector3fc): Vector3f {
    return Vector3f(this).sub(other)
}

/**
 * Adds [other] to this vector in-place and returns the result.
 */
operator fun Vector3f.plusAssign(other: Vector3fc): Unit {
    this.add(other)
}

/**
 * Subtracts [other] from this vector in-place and returns the result.
 */
operator fun Vector3f.minusAssign(other: Vector3fc): Unit {
    this.sub(other)
}

/**
 * Multiplies this vector with [other] in-place.
 */
operator fun Vector3f.timesAssign(other: Float): Unit {
    this.mul(other)
}

/**
 * Divides this vector by [other] in-place.
 */
operator fun Vector3f.divAssign(other: Float): Unit {
    this.set(this.mul(1.0f / other))
}

/**
 * Multiplies this float value with the [other] vector and returns the result.
 */
operator fun Float.times(other: Vector3fc): Vector3f {
    return Vector3f(other).times(this)
}

/**
 * Extracts the x and y components of this 3D vector and returns them as 2D vector.
 */
fun Vector3fc.xy(): Vector2f {
    return Vector2f().set(this.x(), this.y())
}

/**
 * Converts this 3D vector to 4D, while setting the w component of the new vector to 1.0f.
 */
fun Vector3fc.xyzw(): Vector4f {
    return Vector4f().set(this, 1.0f)
}

/**
 * Returns the value of the biggest component of this vector.
 */
fun Vector3fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

/**
 * Converts this vector to a [FloatArray].
 */
fun Vector3f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y, this.z)
}

/**
 * Infix shortcut for calculating the cross product of this vector
 * with [rhs]. The calculation happens on a copy of this vector, which is then returned.
 */
infix fun Vector3f.X(rhs: Vector3f): Vector3f {
    return Vector3f(this).cross(rhs)
}

/* Vector4f */

/**
 * Adds [other] to a copy of this vector and returns the result.
 */
operator fun Vector4f.plus(other: Vector4fc): Vector4f {
    return Vector4f(this).add(other)
}

/**
 * Multiplies a copy of this vector with [other] by-component and returns the result.
 */
operator fun Vector4f.times(other: Vector4fc): Vector4f {
    return Vector4f(this).mul(other)
}

/**
 * Multiplies a copy of this vector with [other] and returns the result.
 */
operator fun Vector4f.times(other: Float): Vector4f {
    return Vector4f(this).mul(other)
}

/**
 * Subtracts [other] from a copy of this vector and returns the result.
 */
operator fun Vector4f.minus(other: Vector4fc): Vector4f {
    return Vector4f(this).sub(other)
}

/**
 * Adds [other] to this vector in-place and returns the result.
 */
operator fun Vector4f.plusAssign(other: Vector4fc): Unit {
    this.add(other)
}

/**
 * Subtracts [other] from this vector in-place and returns the result.
 */
operator fun Vector4f.minusAssign(other: Vector4fc): Unit {
    this.sub(other)
}

/**
 * Multiplies this vector with [other] in-place.
 */
operator fun Vector4f.timesAssign(other: Float): Unit {
    this.mul(other)
}

/**
 * Divides this vector by [other] in-place.
 */
operator fun Vector4f.divAssign(other: Float): Unit {
    this.mul(1.0f / other)
}

/**
 * Multiplies this float value with the [other] vector and returns the result.
 */
operator fun Float.times(other: Vector4fc): Vector4f {
    return Vector4f(other).times(this)
}

/**
 * Returns the value of the biggest component of this vector.
 */
fun Vector4fc.max(): Float {
    val component = this.maxComponent()
    return this[component]
}

/**
 * Returns the x, y, z components of this 4D vector.
 */
fun Vector4fc.xyz(): Vector3f {
    return Vector3f().set(x(), y(), z())
}

/**
 * Converts this vector to a [FloatArray].
 */
fun Vector4f.toFloatArray(): FloatArray {
    return floatArrayOf(this.x, this.y, this.z, this.w)
}

/**
 * Multiples this quaternion with [other] and returns the result.
 */
operator fun Quaternionf.times(other: Quaternionf): Quaternionf {
    return this.mul(other)
}

/**
 * Multiplies a copy of this matrix with [rhs] and returns the result.
 */
infix operator fun Matrix4f.times(rhs: Matrix4f): Matrix4f {
    val m = Matrix4f(this)
    m.mul(rhs)

    return m
}

/**
 * Comapres this matrix and [right] with each other. If the absolute difference exceeds
 * [epsilon], the function will return false, otherwise true.
 */
fun Matrix4f.compare(right: Matrix4fc, explainDiff: Boolean, epsilon: Float = 0.00001f): Boolean {
    val left = this

    for (r in 0 .. 3) {
        for (c in 0..3) {
            val delta = Math.abs(left.get(c, r) - right.get(c, r))

            if (delta > epsilon) {
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

/**
 * Adds the vector [v] to the FloatBuffer, while advancing its position.
 */
operator fun FloatBuffer.plusAssign(v: Vector2f) {
    v.get(this)
    this.position(this.position() + 2)
}

/**
 * Adds the vector [v] to the FloatBuffer, while advancing its position.
 */
operator fun FloatBuffer.plusAssign(v: Vector3f) {
    v.get(this)
    this.position(this.position() + 3)
}

/**
 * Adds the vector [v] to the FloatBuffer, while advancing its position.
 */
operator fun FloatBuffer.plusAssign(v: Vector4f) {
    v.get(this)
    this.position(this.position() + 4)
}



