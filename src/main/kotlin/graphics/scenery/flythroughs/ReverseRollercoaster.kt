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
    init {
        val arrows = Mesh("arrows")
        //debug arrows
        val matFaint = DefaultMaterial()
        matFaint.diffuse  = Vector3f(0.0f, 0.6f, 0.6f)
        matFaint.ambient  = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.specular = Vector3f(1.0f, 1.0f, 1.0f)
        matFaint.cullingMode = Material.CullingMode.None
        frames.forEachIndexed { index, it ->
            if(index%20 == 0) {
                val arrowX = Arrow(it.binormal - Vector3f())
                arrowX.edgeWidth = 0.5f
                arrowX.addAttribute(Material::class.java, matFaint)
                arrowX.spatial().position = it.translation
                arrows.addChild(arrowX)
                val arrowY = Arrow(it.normal - Vector3f())
                arrowY.edgeWidth = 0.5f
                arrowY.addAttribute(Material::class.java, matFaint)
                arrowY.spatial().position = it.translation
                arrows.addChild(arrowY)
                val arrowZ = Arrow(it.tangent - Vector3f())
                arrowZ.edgeWidth = 0.5f
                arrowZ.addAttribute(Material::class.java, matFaint)
                arrowZ.spatial().position = it.translation
                arrows.addChild(arrowZ)
            }
        }
        //scene.children.filter{it.name == name}[0].addChild(arrows)
    }
    
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

            frames.forEach {
                println("before: ${it.translation}")
                curveRotation.transform(it.translation)
                println("after: ${it.translation}")
            }
            //position
            scene.children.filter{it.name == name}[0].ifSpatial {
                val rotatedFramePosition = frames[i].translation
                val frameToBeforeCam = Vector3f(beforeCam).sub(rotatedFramePosition)
                //println("This is frame befor Camera: $frameToBeforeCam $i")
                frames.forEach { it.translation.add(frameToBeforeCam) }
                val nextPosition = Vector3f(position).add(frameToBeforeCam)
                //println("This is frame nextposition: $nextPosition $i")
                position = nextPosition
            }
            i += 1
        }
    }
}
