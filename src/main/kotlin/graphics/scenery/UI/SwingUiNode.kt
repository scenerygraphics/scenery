package graphics.scenery.UI

import graphics.scenery.attribute.material.Material
import graphics.scenery.primitives.Plane
import graphics.scenery.textures.Texture
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

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

    /**
     * Sets up the UINode with no CUllingMode.None for the Plane and adds the retexturing routing using the Snapshot of the attached SwingFrame (SwingBridgeFrame)
     */
    init {
        //to also hit the backside
        this.material().cullingMode = Material.CullingMode.None

        this.update += {
            this.spatial().scale = Vector3f(swingBridgeFrame.width.toFloat()/swingBridgeFrame.height.toFloat(), 1.0f, 1.0f)
            spatial().needsUpdate = true
        }
    }

    /**
     * Function that takes the last taken screenshot from the SwingUI-window coming from the attached swingBridgeFrame and updates the SwingUINodes-Plane texture with it
     */
    fun updateUITexture() {
        swingBridgeFrame.finalImage?.let {
            this.material {
                textures["diffuse"] = Texture.fromImage((it), Texture.RepeatMode.Repeat.all(), Texture.BorderColor.OpaqueBlack, true, true,
                    Texture.FilteringMode.NearestNeighbour, Texture.FilteringMode.NearestNeighbour, hashSetOf(Texture.UsageType.Texture))
            }
        }
    }

    /**
     * Converts a 3D world space position to a 2D coordinate relative to the plane
     */
    private fun worldSpaceToUISpace(wPos: Vector3f) : Vector2f {
        val modelMat = Matrix4f(this.spatial().model).invert()
        val hitPosModel = Vector4f(wPos, 1f).mul(modelMat)

        return  Vector2f((hitPosModel.x + 0.5f) * swingUiDimension.first, swingUiDimension.second - (hitPosModel.y + 0.5f) * swingUiDimension.second)
    }

    /**
     * Calls the ctrlClick method of the attached swingBridgeFrame to simulate a Ctrl+MouseButton1 on the SwingFrame
     */
    fun ctrlClick(wPos: Vector3f) {

        val swingPos = worldSpaceToUISpace(wPos)
        swingBridgeFrame.ctrlClick(swingPos.x.toInt(), swingPos.y.toInt())
    }

    /**
     * Calls the pressed method of the attached swingBridgeFrame to simulate a MouseButton1_pressed on the SwingFrame
     */
    fun pressed(wPos: Vector3f) {

        val swingPos = worldSpaceToUISpace(wPos)
        swingBridgeFrame.pressed(swingPos.x.toInt(), swingPos.y.toInt())
    }

    /**
     * Calls the drag method of the attached swingBridgeFrame to simulate a MouseButton1 drag on the SwingFrame
     */
    fun drag(wPos: Vector3f) {

        val swingPos = worldSpaceToUISpace(wPos)
        swingBridgeFrame.drag(swingPos.x.toInt(), swingPos.y.toInt())
    }

    /**
     * Calls the release method of the attached swingBridgeFrame to simulate a MouseButton1_released on the SwingFrame
     */
    fun released(wPos: Vector3f) {

        val swingPos = worldSpaceToUISpace(wPos)
        swingBridgeFrame.released(swingPos.x.toInt(), swingPos.y.toInt())
    }

}
