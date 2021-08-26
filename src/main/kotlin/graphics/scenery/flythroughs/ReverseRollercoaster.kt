package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.geometry.Curve
import graphics.scenery.utils.LazyLogger
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.ojalgo.array.QuaternionArray
import org.scijava.ui.behaviour.ClickBehaviour
import kotlin.math.acos

/**
 * class that lets a curve or spline object fly in front of the camera from beginning to end
 */
class ReverseRollercoaster(val scene: Scene, val cam: ()->Camera?, val name: String): ClickBehaviour {
    val logger by LazyLogger()
    val curve: Node = scene.children.filter{it.name == name}[0]
    val camera = cam.invoke()
    val frames = if(curve is Curve) { curve.frenetFrames } else { ArrayList() }
    var i = 0
    init {
        val matBright = DefaultMaterial()
        matBright.diffuse  = Vector3f(0.0f, 1.0f, 0.0f)
        matBright.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matBright.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matBright.cullingMode = Material.CullingMode.None

        val matFaint = DefaultMaterial()
        matFaint.diffuse  = Vector3f(0.0f, 0.6f, 0.6f)
        matFaint.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.cullingMode = Material.CullingMode.None

    }
    
    override fun click(x: Int, y: Int) {
        if (i <= frames.lastIndex) {
            val forward = if(camera != null) { Vector3f(camera.forward) } else { logger.warn("Cam is Null!"); Vector3f() }
            val up = if(camera != null) { Vector3f(camera.up) } else { logger.warn("Cam is null"); Vector3f() }
            //position
            val newPosition = Vector3f(camera?.spatial()?.position!!)
            //right before the camera
            val beforeCam = Vector3f(forward).mul(0.2f)
            //vector from frame location to position
            val frame = frames[i]
            newPosition.add(beforeCam).sub(frame.translation)
            //transfer all frenet frame positions
            frames.forEach { 
                it.translation.add(newPosition)
            }
            //rotation
            val tangent = frames[i].tangent
            /*
            // euler angles
            val angleX = calcAngle(Vector2f(forward.y, forward.z), Vector2f(tangent.y, tangent.z))
            val angleY = calcAngle(Vector2f(forward.x, forward.z), Vector2f(tangent.x, tangent.z))
            val angleZ = calcAngle(Vector2f(forward.x, forward.y), Vector2f(tangent.x, tangent.y))
            val curveRotation = Quaternionf().rotateXYZ(angleX, angleY, angleZ).normalize()

             */

            val curveRotation = Quaternionf().lookAlong(forward, up).normalize()

            scene.children.filter{it.name == name}[0].ifSpatial {
                position = position.add(newPosition)
                rotation = curveRotation
            }
            i += 1
        }
    }

    private fun calcAngle(vec1: Vector2f, vec2: Vector2f): Float {
        vec1.normalize()
        vec2.normalize()
        // normalize will return NaN if one vector is the null vector
        return if(!vec1.x.isNaN() && !vec1.y.isNaN() && !vec2.x.isNaN() && !vec2.y.isNaN()) {
            val cosAngle = vec1.dot(vec2).toDouble()
            var angle = if(cosAngle > 1) { 0.0 } else { acos(cosAngle) }
            // negative angle?
            vec1[vec1.y] = -vec1.x
            if(vec2.dot(vec1) > 0) { angle *= -1.0}
            angle.toFloat()
        } else  {
            0f
        }
    }
}
