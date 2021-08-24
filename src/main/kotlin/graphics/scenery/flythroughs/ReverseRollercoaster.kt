package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.geometry.Curve
import graphics.scenery.utils.LazyLogger
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.math.acos
import kotlin.math.tan

/**
 * class that lets a curve or spline object fly in front of the camera from beginning to end
 */
class ReverseRollercoaster(val scene: Scene, val cam: ()->Camera?, val name: String): ClickBehaviour {
    val logger by LazyLogger()
    val curve = scene.children.filter{it.name == name}[0]
    val camera = cam.invoke()
    var i = 0
    override fun click(x: Int, y: Int) {
        if (curve is Curve) {
            val frames = curve.frenetFrames
            if (i <= frames.lastIndex) {
                val forward = if(camera != null) { camera.forward } else { logger.warn("Cam is Null!"); Vector3f() }
                //position
                val newPosition = Vector3f()
                //right before the camera
                camera?.spatial()?.position!!.add(forward.mul(0.5f, newPosition), newPosition)
                //vector from frame location to position
                newPosition.sub(frames[i].translation)

                //rotation
                val tangent = frames[i].tangent
                // euler angles
                val angleX = calcAngle(Vector2f(forward.y, forward.z), Vector2f(tangent.y, tangent.z))
                val angleY = calcAngle(Vector2f(forward.x, forward.z), Vector2f(tangent.x, tangent.z))
                val angleZ = calcAngle(Vector2f(forward.x, forward.y), Vector2f(tangent.x, tangent.y))
                val curveRotation = Quaternionf().rotateXYZ(angleX, angleY, angleZ).normalize()

                scene.children.filter{it.name == name}[0].ifSpatial {
                    position.add(newPosition)
                    rotation = curveRotation
                }
                i += 1
            }
        }
    }

    private fun calcAngle(vec1: Vector2f, vec2: Vector2f): Float {
        vec1.normalize()
        vec2.normalize()
        val cosAngle = vec1.dot(vec2).toDouble()
        var angle = if(cosAngle > 1) { 0.0 } else { acos(cosAngle) }
        // negative angle?
        vec1[vec1.y] = -vec1.x
        if(vec1.dot(vec2) < 0) { angle *= -1.0}
        return angle.toFloat()
    }
}
