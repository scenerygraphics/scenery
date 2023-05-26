package graphics.scenery.utils

import kool.Ptr
import kool.unsafe
import org.joml.Vector2f
import org.joml.Vector3f

operator fun Ptr<Vector3f>.get(index: Int): Vector3f {
    val ofs = adr.toLong() + (index shl 2)
    return Vector3f(unsafe.getFloat(ofs), unsafe.getFloat(ofs + 4), unsafe.getFloat(ofs + 8))
}
operator fun Ptr<Vector3f>.set(index: Int, v: Vector3f) {
    val ofs = adr.toLong() + (index shl 2)
    unsafe.putFloat(ofs, v.x)
    unsafe.putFloat(ofs + 4, v.y)
    unsafe.putFloat(ofs + 8, v.z)
}
operator fun Ptr<Vector2f>.set(index: Int, v: Vector2f) {
    val ofs = adr.toLong() + (index shl 2)
    unsafe.putFloat(ofs, v.x)
    unsafe.putFloat(ofs + 4, v.y)
}

operator fun Ptr<Vector3f>.inc() = Ptr<Vector3f>(adr.toLong() + 12)
operator fun Ptr<Vector2f>.inc() = Ptr<Vector2f>(adr.toLong() + 8)
