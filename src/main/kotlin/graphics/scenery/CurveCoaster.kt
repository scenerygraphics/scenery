package graphics.scenery

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.scijava.ui.behaviour.ClickBehaviour

class CurveCoaster(curve: Curve, val camera: () -> Camera?): ClickBehaviour {
    private val listOfCameraPoints = curve.frenetFrames
    val bases = curve.calcBases()
    var i = 0


    override fun click(x: Int, y: Int) {
        val cam = camera.invoke()
        if(i <= listOfCameraPoints.lastIndex ) {
            val frame = listOfCameraPoints[i]
            val newViewMatrix = Matrix4f(frame.normal.x(), frame.normal.y(), frame.normal.z(), 0f,
                                            frame.binormal.x(), frame.binormal.y(), frame.binormal.z(), 0f,
                                            frame.tangent.x(), frame.tangent.y(), frame.tangent.z(),  0f,
                                            0f, 0f, 0f, 1f)
            if (cam != null) {
                cam.view = bases[i]
                //cam.position = frame.translation
            }
            i++
        }
        else { return }
        //using 25 fps or, the reverse, 40 ms per frame
    }
}
