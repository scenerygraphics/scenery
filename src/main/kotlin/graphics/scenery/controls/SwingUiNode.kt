package graphics.scenery.controls

import graphics.scenery.attribute.material.Material
import graphics.scenery.primitives.Plane
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.FileInputStream

/**
 * @author Jan Tiemann
 * @author Konrad Michel
 *
 * Scene representation of a SwingFrame on a plane in form of a texture snapshot render of the frame in combination with SwingBridgeFrame
 */
class SwingUiNode(val swingBridgeFrame : SwingBridgeFrame) : Plane(
    Vector3f(-0.5f,-0.5f,0.0f),
    Vector3f(-0.5f,0.5f,0.0f),
    Vector3f(0.5f,-0.5f,0.0f),
    Vector3f(0.5f,0.5f,0.0f)
) {

    var swingUiDimension = 0 to 0


    init {
        //to also hit the backside
        this.material().cullingMode = Material.CullingMode.None
        this.material {
            textures["diffuse"] = Texture.fromImage(Image.fromStream(FileInputStream("C:\\Users\\Kodels Bier\\Desktop\\helix.png"), ".png", false))
        }

        this.update += {
            this.spatial().scale = Vector3f(swingBridgeFrame.width.toFloat()/swingBridgeFrame.height.toFloat(), 1.0f, 1.0f)
            updateUITexture()
            spatial().needsUpdate = true
        }
    }
    fun updateUITexture() {
        swingBridgeFrame.finalImage?.let {
            this.material {
                textures["diffuse"] = Texture.fromImage((it), Texture.RepeatMode.Repeat.all(), Texture.BorderColor.OpaqueBlack, true, true,
                    Texture.FilteringMode.NearestNeighbour, Texture.FilteringMode.NearestNeighbour, hashSetOf(Texture.UsageType.Texture))
            }
        }
    }

    //from Jan
    fun Matrix4f.copy(): Matrix4f = Matrix4f(this)

    fun click(wPos: Vector3f) {

        val hitPosModel = Vector4f(wPos, 1f).mul(this.spatial().model.copy().invert())

        val swingX = (hitPosModel.x + 0.5f) * swingUiDimension.first
        val swingY = swingUiDimension.second - (hitPosModel.y + 0.5f) * swingUiDimension.second
        println("Clicky at $swingX : $swingY")
        swingBridgeFrame.click(swingX.toInt(), swingY.toInt())
    }

    fun pressed(wPos: Vector3f) {

        println("${this.spatial().model}")
        println("${wPos.x},${wPos.y},${wPos.z}")
        val hitPosModel = Vector4f(wPos, 1f).mul(this.spatial().model.copy().invert())
        println("${hitPosModel.x},${hitPosModel.y},${hitPosModel.z},${hitPosModel.w},")

        val swingX = (hitPosModel.x + 0.5f) * swingUiDimension.first
        val swingY = swingUiDimension.second - (hitPosModel.y + 0.5f) * swingUiDimension.second
        println("Press at $swingX : $swingY")
        swingBridgeFrame.pressed(swingX.toInt(), swingY.toInt())
    }

    fun drag(wPos: Vector3f) {

        println("${this.spatial().model}")
        println("${wPos.x},${wPos.y},${wPos.z}")
        val hitPosModel = Vector4f(wPos, 1f).mul(this.spatial().model.copy().invert())
        println("${hitPosModel.x},${hitPosModel.y},${hitPosModel.z},${hitPosModel.w},")

        val swingX = (hitPosModel.x + 0.5f) * swingUiDimension.first
        val swingY = swingUiDimension.second - (hitPosModel.y + 0.5f) * swingUiDimension.second
        println("Drag at $swingX : $swingY")
        swingBridgeFrame.drag(swingX.toInt(), swingY.toInt())
    }

    fun released(wPos: Vector3f) {

        println("${this.spatial().model}")
        println("${wPos.x},${wPos.y},${wPos.z}")
        val hitPosModel = Vector4f(wPos, 1f).mul(this.spatial().model.copy().invert())
        println("${hitPosModel.x},${hitPosModel.y},${hitPosModel.z},${hitPosModel.w},")

        val swingX = (hitPosModel.x + 0.5f) * swingUiDimension.first
        val swingY = swingUiDimension.second - (hitPosModel.y + 0.5f) * swingUiDimension.second
        println("Release at $swingX : $swingY")
        swingBridgeFrame.released(swingX.toInt(), swingY.toInt())
    }

}
