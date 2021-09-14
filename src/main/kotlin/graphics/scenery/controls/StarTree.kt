package graphics.scenery.controls

import graphics.scenery.Mesh
import graphics.scenery.Sphere
import graphics.scenery.primitives.TextBoard
import org.joml.Vector3f
import org.joml.Vector4f

class StarTree(textBoardText: String = "", val action: () -> Unit = {},): Mesh("StarTree") {
    var root = false
    var childrenDisplayed = false
    val starTreeChildren = this.children.filter { it.name == "StarTree" }
    init {
        if(this.parent?.name != "StarTree") {root = true; childrenDisplayed = true}
        if(!root) {
            starTreeChildren.forEach { it.visible = false }
            val sphere = Sphere(0.025f, 10)
            sphere.name = "StarTreeSphere"
            this.addChild(sphere)
            val board = TextBoard()
            board.text = textBoardText
            board.name = "ToolSelectTextBoard"
            board.transparent = 0
            board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
            board.backgroundColor = Vector4f(100f, 100f, 100f, 1.0f)
            board.spatial {
                position = Vector3f(0f, 0.05f, 0f)
                scale = Vector3f(0.05f, 0.05f, 0.05f)
            }
            this.addChild(board)
        }
        // child positions
        starTreeChildren.forEachIndexed { index, it ->
            val pos = Vector3f(0f, .15f, 0f)
            if(root) {
                pos.rotateZ((2f * Math.PI.toFloat() / starTreeChildren.size) * index)
            }
            else {
                pos.rotateZ((Math.PI.toFloat() / starTreeChildren.size) * index)
            }
            this.addChild(it)
            it.ifSpatial { position = pos }
        }
    }

    fun showChildren() {
        starTreeChildren.forEach { visible = true }
        var childrenDisplayed = true
    }

    fun hideChildren() {
        starTreeChildren.forEach { visible = false }
        var childrenDisplayed = false
    }
}
