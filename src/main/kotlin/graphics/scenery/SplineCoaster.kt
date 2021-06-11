package graphics.scenery

import graphics.scenery.utils.LazyLogger
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.Logger
import kotlin.math.acos

class SplineCoaster(spline: Spline, val camera: () -> Camera?): ClickBehaviour {
    private val curve = Curve
    private val listOfCameraPoints = FrenetFramesCalc(spline).computeFrenetFrames()
    val bases = FrenetFramesCalc(spline).calcBases(listOfCameraPoints)
    var j = 0
    private val logger: Logger by LazyLogger()

    override fun click(x: Int, y: Int) {
        val camera = camera.invoke()
        if(j <= listOfCameraPoints.lastIndex && camera != null) {
            //change cam position
            val frame = listOfCameraPoints[j]
            camera.position = frame.translation

            //change cam orientation
            //desired view direction in world coords
            val worldDirVec = frame.tangent
            if (worldDirVec.lengthSquared() < 0.01) {
                //ill defined task, happens typically when cam is inside the node which we want center on
                logger.info("Camera is on the spot you want to look at. Please, move the camera away first.")
                return
            }
            val camForwardXZ = Vector2f(camera.forward.x, camera.forward.z)
            val wantLookAtXZ = Vector2f(worldDirVec.x, worldDirVec.z)
            var totalYawAng = camForwardXZ.normalize().dot(wantLookAtXZ.normalize()).toDouble()
            //while mathematically impossible, cumulated numerical inaccuracies have different opinion
            totalYawAng = if (totalYawAng > 1) {
                0.0
            } else {
                acos(totalYawAng)
            }

            //switch direction?
            camForwardXZ[camForwardXZ.y] = -camForwardXZ.x
            if (wantLookAtXZ.dot(camForwardXZ) > 0) totalYawAng *= -1.0
            val camForwardYed = Vector3f(camera.forward)
            Quaternionf().rotateXYZ(0f, (-totalYawAng).toFloat(), 0f).normalize().transform(camForwardYed)
            var totalPitchAng = camForwardYed.normalize().dot(worldDirVec.normalize()).toDouble()
            totalPitchAng = if (totalPitchAng > 1) {
                0.0
            } else {
                acos(totalPitchAng)
            }

            //switch direction?
            if (camera.forward.y > worldDirVec.y) totalPitchAng *= -1.0
            if (camera.up.y < 0) totalPitchAng *= -1.0

            //animation options: control delay between animation frames -- fluency
            val rotPausePerStep: Long = 5 //miliseconds

            //animation options: control max number of steps -- upper limit on total time for animation
            val rotMaxSteps = 999999 //effectively disabled....

            //animation options: the hardcoded 5 deg (0.087 rad) -- smoothness
            //how many steps when max update/move is 5 deg
            val totalDeltaAng = Math.max(Math.abs(totalPitchAng), Math.abs(totalYawAng)).toFloat()
            var rotSteps = Math.ceil(totalDeltaAng / 0.087).toInt()
            if (rotSteps > rotMaxSteps) rotSteps = rotMaxSteps

            /*
            logger.info("centering over "+rotSteps+" steps the pitch " + 180*totalPitchAng/Math.PI
                    + " and the yaw " + 180*totalYawAng/Math.PI);
            */

            //angular progress aux variables
            var donePitchAng = 0.0
            var doneYawAng = 0.0
            var deltaAng: Float
            camera.targeted = false
            var i = 1
            while (i <= rotSteps) {

                //this emulates ease-in ease-out animation, both vars are in [0:1]
                var timeProgress = i.toFloat() / rotSteps
                val angProgress = (if (2.let { timeProgress *= it; timeProgress } <= 1) //two cubics connected smoothly into S-shape curve from [0,0] to [1,1]
                    timeProgress * timeProgress * timeProgress else 2.let { timeProgress -= it; timeProgress } * timeProgress * timeProgress + 2) / 2

                //rotate now by this ang: "where should I be by now" minus "where I got last time"
                deltaAng = (angProgress * totalPitchAng - donePitchAng).toFloat()
                val pitchQ = Quaternionf().rotateXYZ(-deltaAng, 0f, 0f).normalize()
                deltaAng = (angProgress * totalYawAng - doneYawAng).toFloat()
                val yawQ = Quaternionf().rotateXYZ(0f, deltaAng, 0f).normalize()
                camera.rotation = pitchQ.mul(camera.rotation).mul(yawQ).normalize()
                donePitchAng = angProgress * totalPitchAng
                doneYawAng = angProgress * totalYawAng
                /*
                try {
                    Thread.sleep(rotPausePerStep)
                } catch (e: InterruptedException) {
                    i = rotSteps
                }
                 */
                ++i
            }
            j++
        }
        else { return }
        //using 25 fps or, the reverse, 40 ms per frame
    }
}
