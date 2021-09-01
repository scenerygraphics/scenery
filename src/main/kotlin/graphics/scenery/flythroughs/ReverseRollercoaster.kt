package graphics.scenery.flythroughs

import graphics.scenery.*
import graphics.scenery.geometry.Curve
import graphics.scenery.utils.LazyLogger
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

    init {
        val frameNodesParent = Mesh("FrameNodeParents")
        frames.forEachIndexed { index, frame ->
            val frameNode = Mesh("FrameNode $index")
            frameNode.spatial().position = frame.translation
            frameNodesParent.addChild(frameNode)
        }
        scene.children.filter{it.name == name}[0].addChild(frameNodesParent)
    }
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
                val currentFrame = scene.children.filter{it.name == name}[0].
                    children.filter{it.name == "FrameNodeParents"}[0].children[i]
                val frameToBeforeCam = Vector3f(beforeCam).sub(currentFrame.ifSpatial { rotation }?.rotation?.transform(Vector3f(currentFrame.ifSpatial { position }?.position)))
                val nextPosition = Vector3f(position).add(frameToBeforeCam)
                position = nextPosition
            }
            i += 1
        }
    }
}
