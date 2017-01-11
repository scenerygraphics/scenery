package graphics.scenery.controls.behaviours

import cleargl.GLVector
import org.scijava.ui.behaviour.ClickBehaviour
import graphics.scenery.Camera
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.Sphere
import graphics.scenery.backends.Renderer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class SelectCommand(private val name: String, private val renderer: Renderer, private val scene: Scene, private val cam: Camera) : ClickBehaviour {
    override fun click(x: Int, y: Int) {
        val view = (cam.target - cam.position).normalize()
//        val view = cam.forward.normalized
        var h = view.cross(cam.up).normalize()
        var v = h.cross(view)

        val width = renderer.window.width.toFloat()
        val height = renderer.window.height.toFloat()

        val fov = cam.fov * Math.PI/180.0f//fov in rad
        val lengthV = Math.tan(fov/2.0).toFloat()*cam.nearPlaneDistance
        val lengthH = lengthV * (width/height)

        v = v*lengthV
        h = h*lengthH

        val posX = (x - width/2.0f)/(width/2.0f)
        val posY = -1.0f*(y - height/2.0f)/(height/2.0f)

        val worldPos = cam.position + view*cam.nearPlaneDistance + h*posX + v*posY
        val worldDir = (worldPos - cam.position).normalized

        System.err.println("Select click pos: $view -> ${cam.position} -> $worldPos x=$posX/y=$posY")

        (5..50).forEach {
            val s = Sphere(0.2f, 20)
            s.position = worldPos + worldDir* it.toFloat()*5.0f
            scene.addChild(s)
        }
    }

    fun AABBintersect(node: Node, origin: GLVector, dir: GLVector): Float {
        if(node.boundingBoxCoords == null) {
            return Float.MAX_VALUE
        } else {
            val min = GLVector(node.boundingBoxCoords!![0], node.boundingBoxCoords!![2], node.boundingBoxCoords!![4], 1.0f)
            val max = GLVector(node.boundingBoxCoords!![1], node.boundingBoxCoords!![3], node.boundingBoxCoords!![5], 1.0f)

            val min_world = node.world.mult(min)
            val max_world = node.world.mult(max)

            return 0.0f
        }
    }
}
