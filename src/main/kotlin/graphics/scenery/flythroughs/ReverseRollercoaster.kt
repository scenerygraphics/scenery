package graphics.scenery.flythroughs

import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.geometry.Curve
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

/**
 * class that lets a curve or spline object fly in front of the camera from beginning to end
 */
class ReverseRollercoaster(val scene: Scene, val cam: ()->Camera?, val name: String): ClickBehaviour {
    val curve = scene.children.filter{it.name == name}[0]
    val camera = cam.invoke()
    var i = 0
    override fun click(x: Int, y: Int) {
        if (curve is Curve) {
            val frames = curve.frenetFrames
            if (i <= frames.lastIndex) {
                val intermediateVector = Vector3f()
                curve.spatial().position.add((frames[i].translation.sub(curve.spatial().position, intermediateVector)))
                curve.spatial().position.add(camera?.spatial()?.position?.add(camera?.forward?.mul(0.5f, intermediateVector)), intermediateVector)
                scene.children.filter{it.name == name}[0].ifSpatial { position =  curve.spatial().position}
                i += 1
            }
        }
    }
}
