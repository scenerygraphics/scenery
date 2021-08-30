package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.geometry.Curve
import graphics.scenery.primitives.Arrow
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
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
    val spaceBetweenFrames = ArrayList<Float>(frames.size-1)

    var i = 0
    init {
        frames.windowed(2, 1) {
            val frameToFrame = Vector3f()
            it[0].translation.sub(it[1].translation, frameToFrame)
            spaceBetweenFrames.add(frameToFrame.length())
        }
    }
    
    override fun click(x: Int, y: Int) {
        val forward = if(camera != null) { Vector3f(camera.forward) } else { logger.warn("Cam is Null!"); Vector3f() }
        if (i <= frames.lastIndex) {
            //rotation
            val tangent = frames[i].tangent
            // euler angles
            val angleX = calcAngle(Vector2f(forward.y, forward.z), Vector2f(tangent.y, tangent.z))
            val angleY = calcAngle(Vector2f(forward.x, forward.z), Vector2f(tangent.x, tangent.z))
            val angleZ = calcAngle(Vector2f(forward.x, forward.y), Vector2f(tangent.x, tangent.y))
            val curveRotation = Quaternionf().rotateXYZ(angleX, angleY, angleZ).conjugate().normalize()
            scene.children.filter{it.name == name}[0].ifSpatial {
                rotation = curveRotation
            }

            //position
            scene.children.filter{it.name == name}[0].ifSpatial {
                if(i == 0) {
                    //intial position right before the camera
                    val beforeCam = Vector3f(forward).mul(0.5f)
                    position = (Vector3f(camera?.spatial()?.position!!)).add(beforeCam)
                }
                else {
                    val index = i
                    val frame = frames[index-1]
                    val nextFrame = frames[index]
                    val translation = Vector3f(frame.tangent).mul(Vector3f(Vector3f(nextFrame.translation).sub(Vector3f(frame.translation))).length())
                    position = position.add(translation)
                }
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
            /*
            // negative angle?
            vec1.x = -vec1.x
            if(vec2.dot(vec1) > 0) { angle *= -1.0}

             */
            angle.toFloat()
        } else  {
            0f
        }
    }
}
