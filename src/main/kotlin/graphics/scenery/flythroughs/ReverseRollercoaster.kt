package graphics.scenery.flythroughs

import graphics.scenery.*
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.geometry.Curve
import graphics.scenery.primitives.Arrow
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.extensions.minus
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

/**
 * class that lets a curve or spline object fly in front of the camera from beginning to end
 */
class ReverseRollercoaster(val scene: Scene, val cam: ()->Camera?, val name: String): ClickBehaviour {
    val logger by LazyLogger()
    val curve: Node = scene.children.filter{it.name == name}[0]
    val camera = cam.invoke()
    val frames = if(curve is Curve) { curve.frenetFrames } else { ArrayList() }
    val forward = if(camera != null) { Vector3f(camera.forward) } else { logger.warn("Cam is Null!"); Vector3f() }
    val up = if(camera != null) { Vector3f(camera.up) } else { logger.warn("Cam is Null!"); Vector3f() }
    var index = 0
    //initial position right before camera
    val stretchedForward = Vector3f(forward) //.mul(2f)
    private val beforeCam = (Vector3f(camera?.spatial()?.position!!)) //.add(stretchedForward)

    var i = 0
    override fun click(x: Int, y: Int) {
        if (i <= frames.lastIndex) {
            //rotation
            val tangent = frames[i].tangent
            val binormal = frames[i].binormal
            //tangent.mul(-1f)
            val curveRotation = Quaternionf().lookAlong(tangent, binormal).normalize()

            scene.children.filter{it.name == name}[0].ifSpatial {
                rotation = curveRotation
            }
            //position
            scene.children.filter{it.name == name}[0].ifSpatial {
                val rotatedFramePosition = curveRotation.transform(frames[i].translation)
                val frameToBeforeCam = Vector3f(beforeCam).sub(rotatedFramePosition)
                //println("This is frame before Camera: $frameToBeforeCam $i")
                val nextPosition = Vector3f(position).add(frameToBeforeCam)
                //println("This is frame nextposition: $nextPosition $i")
                position = nextPosition
            }
            i += 1
        }
    }
}
