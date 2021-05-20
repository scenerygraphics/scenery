package graphics.scenery

import kotlinx.coroutines.delay
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

class Rollercoaster(ribbonDiagram: RibbonDiagram, val camera: () -> Camera?): ClickBehaviour {
    val subproteins = ribbonDiagram.children
    val listOfCameraPoints = ArrayList<Curve.FrenetFrame>(subproteins.size*100)
    var i = 0

    init {
        ribbonDiagram.children.forEach { subprotein ->
            subprotein.children.forEach{ residue ->
                if(residue is Curve) {
                    residue.frenetFrames.forEach { frame ->
                        listOfCameraPoints.add(frame)
                    }
                }

            }
        }
    }

    override fun click(x: Int, y: Int) {
        if(i <= listOfCameraPoints.lastIndex) {
            val it = listOfCameraPoints[i]
            val cam = camera.invoke()
            if (cam != null) {
                cam.rotation.lookAlong(it.tangent, it.normal)
                cam.position = (it.translation)
                cam.updateWorld(true)
            }
            i++
        }
        else {return }
        //using 25 fps or, the reverse, 40 ms per frame
    }
}
