package graphics.scenery

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

class Cross(val orientPoint1: Vector3f = Vector3f(-0.3f, 0f, 0f), val orientPoint2: Vector3f = Vector3f(0f, -0.3f, 0f)): Mesh("Cross") {
    val sphere = Icosphere(0.3f, 4)
    val cylinder1 = Cylinder(0.02f, 2f, 4)
    val cylinder2 = Cylinder(0.02f, 2f, 4)

    init {
        this.addChild(sphere)
        sphere.material.diffuse = Vector3f(1f, 0f, 0f)
        cylinder1.orientBetweenPoints(orientPoint1, Vector3f(0f, 0f, 0f))
        cylinder1.position = Vector3f(-1f, 0f, 0f)
        this.addChild(cylinder1)


        cylinder2.orientBetweenPoints(orientPoint2, Vector3f(0f, 0f, 0f))
        cylinder2.position = Vector3f(0f, -1f, 0f)
        this.addChild(cylinder2)
    }

    fun updateCoordinateSystem(xAxis: Vector3f = Vector3f(1f, 0f, 0f), yAxis: Vector3f = Vector3f(0f, 1f, 0f),
                               zAxis: Vector3f = Vector3f(0f, 0f, 1f), translation: Vector3f = Vector3f()
    ) {
        val inverseMatrix = Matrix3f(xAxis, yAxis, zAxis).invert()
        val nb = Vector3f()
        inverseMatrix.getColumn(0, nb).normalize()
        val nn = Vector3f()
        inverseMatrix.getColumn(1, nn).normalize()
        val nt = Vector3f()
        val tr = translation
        inverseMatrix.getColumn(2, nt).normalize()
        Matrix4f(
            nb.x(), nn.x(), nt.x(), 0f,
            nb.y(), nn.y(), nt.y(), 0f,
            nb.z(), nn.z(), nt.z(), 0f,
            tr.x(), tr.y(), tr.z(), 1f)
        cylinder1.orientBetweenPoints(inverseMatrix.transform(orientPoint1), Vector3f())
        cylinder2.orientBetweenPoints(inverseMatrix.transform(orientPoint2), Vector3f())
    }
}
