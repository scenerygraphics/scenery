package scenery.controls.behaviours

import cleargl.util.arcball.ArcBall
import com.jogamp.opengl.math.Quaternion
import org.scijava.ui.behaviour.DragBehaviour
import scenery.Node

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ArcBallCameraControl(private val name: String, private val node: Node, private val w: Int, private val h: Int) : DragBehaviour {
    private val arcball = ArcBall()
    private var last = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)

    init {
        arcball.setBounds(w.toFloat(), h.toFloat())
    }

    override fun init(x: Int, y: Int) {
        arcball.setBounds(w.toFloat(), h.toFloat())
        arcball.setCurrent(node.rotation)
        arcball.click(w.toFloat(), h.toFloat())
    }

    override fun drag(x: Int, y: Int) {
        arcball.setBounds(w.toFloat(), h.toFloat())
        node.rotation = arcball.drag(x.toFloat(), y.toFloat())
    }

    override fun end(x: Int, y: Int) = Unit
}