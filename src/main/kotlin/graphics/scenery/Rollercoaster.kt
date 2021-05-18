package graphics.scenery

import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

class Rollercoaster(ribbonDiagram: RibbonDiagram, val camera: () -> Camera?): ClickBehaviour {
    data class CameraPoint(val point: Vector3f, val tangent: Vector3f)
    val subproteins = ribbonDiagram.children
    val listOfCameraPoints = ArrayList<CameraPoint>(subproteins.size*100)

    init {
        ribbonDiagram.children.forEach { subprotein ->
            subprotein.children.forEach{ residue ->
                if(residue is Curve) {
                    residue.frenetFrames.forEach { frame ->
                        listOfCameraPoints.add(CameraPoint(frame.translation, frame.tangent))
                    }
                }

            }
        }
    }

    override fun click(x: Int, y: Int) {
        print("herewego")
        listOfCameraPoints.forEach {
            val cam = camera.invoke()
            cam!!.position = it.point
            cam!!.forward = it.tangent
            //using 25 fps or, the reverse, 40 ms per frame
            Thread.sleep(40)
        }
    }
}
