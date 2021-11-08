package graphics.scenery.flythroughs

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.Scene

import graphics.scenery.geometry.Curve
import graphics.scenery.geometry.FrenetFrame
import graphics.scenery.geometry.FrenetFramesCalc
import graphics.scenery.geometry.UniformBSpline
import graphics.scenery.proteins.Axis
import graphics.scenery.proteins.Helix
import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.LazyLogger
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour
import org.slf4j.Logger
import java.text.DecimalFormat
import kotlin.math.acos

class ProteinBuilder(ribbonDiagram: RibbonDiagram, override val cam: ()-> Camera?,  val scene: Scene,
                     private val name: String): ProteinRollercoaster(ribbonDiagram, cam), ClickBehaviour {
    val img = Image.fromResource("L-Glutamic-Acid.jpg", ProteinBuilder::class.java)
    var k = 0
    val residueCount = ribbonDiagram.children.flatMap { chains -> chains.children }.flatMap { residues -> residues.children }.size
    val framesCount = listOfCameraFrames.size
    private val cameraFramesPerStepFloat = listOfCameraFrames.size.toFloat()/ribbonDiagram.children.flatMap { chains -> chains.children }.flatMap { residues -> residues.children }.size
    //toInt() cuts away all the decimal places after 0
    private val cameraFramesPerStep = cameraFramesPerStepFloat.toInt()
    //for more precision we will use the first decimal place after the comma as well
    private val remainder = ((cameraFramesPerStepFloat-cameraFramesPerStep)*10).toInt()
    override fun click(x: Int, y: Int) {
        if(k > 0) {
            val startingPoint = if(k%10 <= remainder) { 0 } else { 1 }
            for (l in  startingPoint .. cameraFramesPerStep) {
                flyToNextPoint()
            }
        }
        //remove old pic
        scene.removeChild("le box du win")
        //add the amino acid picture
        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        val height = img.height
        val width = img.width
        box.spatial().scale = Vector3f(width/height.toFloat(), 1f, 0f)
        box.spatial().rotation = Quaternionf(camera?.spatial()?.rotation).conjugate()
        box.spatial().position = Vector3f(camera?.spatial()?.position)
        val forwardTimesTwo = Vector3f()
        if(camera?.targeted == true) {
            box.spatial().position.add(camera.target.mul(2f, forwardTimesTwo))
        }
        else {
            box.spatial().position.add(camera?.forward?.mul(2f, forwardTimesTwo))
        }
        box.material {
            textures["alphamask"] = Texture.fromImage(img)
            metallic = 0.3f
            roughness = 0.9f
        }
        //scene.addChild(box)

        if(scene.children.filter { it.name == name }[0] is RibbonDiagram) {
            if (k <= scene.children.filter { it.name == name }[0].children.flatMap { subProtein -> subProtein.children }
                    .flatMap { curve -> curve.children }.lastIndex) {
                scene.children.filter { it.name == name }[0].children.flatMap { subProtein -> subProtein.children }
                    .flatMap { curve -> curve.children }[k].visible = true
            }
        }
        k += 1
    }
}
