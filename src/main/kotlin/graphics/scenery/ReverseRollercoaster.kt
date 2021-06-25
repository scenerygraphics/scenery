package graphics.scenery

import org.scijava.ui.behaviour.ClickBehaviour

/**
 * class that lets a curve or spline object fly in front of the camera from beginning to end
 */
class ReverseRollercoaster(val cam: ()-> Camera?, val curve: Curve): ClickBehaviour {
    val frenetFrames = curve.frenetFrames

    /*
    Top level idea: create a box, corresponding to the boundingBox of the curve. Then set rotation of the curve equal to the rotation of the box
    (need to find out whether the box is even necessary) take the box and rotate it along the curve with help of the FrenetFrames.
    As the position choose the cam position plus forward vec of the cam times the length of the box.
     */
    init {
        val boundingBox = curve.getMaximumBoundingBox()
        val box = Box()


        }
    override fun click(x: Int, y: Int) {
        TODO("Not yet implemented")
    }
}
